package com.ex2tools.iv.voice;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class VoiceService extends Service {

    public static final String ACTION_TOGGLE = "com.ex2tools.iv.voice.TOGGLE";
    public static final String ACTION_LISTEN = "com.ex2tools.iv.voice.LISTEN";
    public static final String ACTION_SYNC_WAKE = "com.ex2tools.iv.voice.SYNC_WAKE";
    public static final String ACTION_ECARX_KEY = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT";

    private static final String EXTRA_ECARX_TYPE = "ecarx.extra.ECARX_KEY_ACTION_TYPE";
    private static final String KEYSERVER_PACKAGE = "com.flyme.auto.keyserver";
    private static final String KEYSERVER_SERVICE = "com.flyme.auto.keyserver.service.AutoKeyService";
    private static final String KEYSERVER_BOOT_ACTION = "com.geely.action.BOOT_KET_SERVICE_APP";

    private static final int SAMPLE_RATE = 16000;
    private static final int MODE_IDLE = 0;
    private static final int MODE_WAKE = 1;
    private static final int MODE_COMMAND = 2;
    private static final long LISTEN_TIMEOUT_MS = 12000L;
    private static final long WAKE_DEBOUNCE_MS = 1500L;
    private static final long TOGGLE_DEBOUNCE_MS = 450L;
    private static final float MAX_SOFT_GAIN = 14.0f;
    private static final double TARGET_RMS = 2800.0d;
    private static final double NOISE_FLOOR_RMS = 60.0d;

    private static volatile boolean running;
    private static volatile boolean ready;
    private static volatile String state = "disabled";
    private static volatile String lastText = "";
    private static volatile long lastKeyserverAt;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mode = new AtomicInteger(MODE_IDLE);
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean shouldRecord = new AtomicBoolean(false);
    private final AtomicBoolean restartRecognition = new AtomicBoolean(false);
    private final AtomicBoolean pendingCommand = new AtomicBoolean(false);
    private final StringBuilder pendingFinal = new StringBuilder();

    private volatile Model model;
    private volatile Recognizer recognizer;
    private volatile AudioRecord audioRecord;
    private volatile Thread recognitionThread;
    private volatile String activeKind;
    private volatile AudioManager audioManager;
    private volatile AudioFocusRequest focusRequest;
    private volatile NoiseSuppressor noiseSuppressor;
    private volatile AutomaticGainControl hardwareAgc;
    private volatile float softAgcGain = 6.0f;
    private volatile String lastPartial = "";
    private volatile VoiceOverlay overlay;

    private long lastKeyAt;
    private long lastToggleAtMs;
    private long lastWakeAtMs;

    private final Runnable timeoutRunnable = this::onTimeout;

    private final BroadcastReceiver keyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_ECARX_KEY.equals(intent.getAction())) return;
            if (!Prefs.voiceEnabled(VoiceService.this) || !Prefs.voiceMicKey(VoiceService.this)) return;
            int type = intent.getIntExtra(EXTRA_ECARX_TYPE, 0);
            if (type != 0 && type != 1) return;
            long now = System.currentTimeMillis();
            if (now - lastKeyAt < TOGGLE_DEBOUNCE_MS) return;
            lastKeyAt = now;
            LogStore.add(VoiceService.this, "voice key actionType=" + type);
            toggleListening();
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static boolean isReady() {
        return ready;
    }

    public static String state() {
        return state;
    }

    public static String lastText() {
        return lastText;
    }

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, VoiceService.class).setAction(ACTION_SYNC_WAKE));
        } catch (Throwable t) {
            LogStore.add(c, "voice start failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, VoiceService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void listen(Context c) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, VoiceService.class).setAction(ACTION_LISTEN));
        } catch (Throwable t) {
            LogStore.add(c, "voice listen failed: " + t);
        }
    }

    public static void sync(Context c) {
        if (!Prefs.voiceEnabled(c)) {
            stop(c);
            return;
        }
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, VoiceService.class).setAction(ACTION_SYNC_WAKE));
        } catch (Throwable t) {
            LogStore.add(c, "voice sync failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        overlay = new VoiceOverlay(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        running = true;
        startForegroundSafe();
        registerKeyReceiver();
        maybeStartKeyserver();
        loadModel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.voiceEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundSafe();
        maybeStartKeyserver();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_LISTEN.equals(action)) {
            if (ready) {
                enterCommandMode(null);
            } else {
                pendingCommand.set(true);
            }
        } else if (ACTION_TOGGLE.equals(action)) {
            toggleListening();
        } else if (ACTION_SYNC_WAKE.equals(action) || action == null) {
            syncWakeListening();
        } else {
            syncWakeListening();
        }
        return START_STICKY;
    }

    private void toggleListening() {
        long now = System.currentTimeMillis();
        if (now - lastToggleAtMs < TOGGLE_DEBOUNCE_MS) return;
        lastToggleAtMs = now;
        if (!Prefs.voiceEnabled(this)) return;
        if (mode.get() == MODE_COMMAND) {
            endCommandSession();
            return;
        }
        if (!ready) {
            pendingCommand.set(true);
            return;
        }
        enterCommandMode(null);
    }

    private void loadModel() {
        state = "unpacking";
        executor.execute(() -> {
            try {
                VoiceModel.ensure(this);
                state = "loading";
                model = new Model(VoiceModel.dir(this).getAbsolutePath());
                ready = true;
                state = "ready";
                main.post(() -> {
                    if (pendingCommand.getAndSet(false)) {
                        enterCommandMode(null);
                    } else {
                        syncWakeListening();
                    }
                });
            } catch (Throwable t) {
                ready = false;
                state = "error";
                LogStore.add(this, "voice model failed: " + t);
            }
        });
    }

    private void enterCommandMode(String initial) {
        if (!Prefs.voiceEnabled(this)) return;
        if (!ready || model == null) {
            pendingCommand.set(true);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            state = "mic_denied";
            Toast.makeText(this, R.string.voice_permission_mic, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        mode.set(MODE_COMMAND);
        pendingFinal.setLength(0);
        lastPartial = "";
        state = "command";
        if (overlay != null) {
            overlay.show();
            overlay.setTitle(getString(R.string.voice_listening));
            overlay.setText(getString(R.string.voice_command_hint));
        }
        requestAudioFocus(true);
        resetTimeout();
        requestRecognizerRestartIfNeeded("command");
        ensureRecognitionRunning();
        if (initial != null && !initial.trim().isEmpty()) {
            handleCommandFinal(initial.trim());
        }
    }

    private void enterWakeMode() {
        if (!Prefs.voiceEnabled(this) || !Prefs.voiceWake(this) || !ready || model == null) {
            stopRecognition();
            return;
        }
        mode.set(MODE_WAKE);
        state = "wake";
        if (overlay != null) overlay.hide();
        main.removeCallbacks(timeoutRunnable);
        abandonAudioFocus();
        requestRecognizerRestartIfNeeded("wake");
        ensureRecognitionRunning();
    }

    private void syncWakeListening() {
        if (!Prefs.voiceEnabled(this)) {
            stopRecognition();
            return;
        }
        if (ready && Prefs.voiceWake(this)) {
            if (mode.get() != MODE_COMMAND) enterWakeMode();
        } else if (mode.get() != MODE_COMMAND) {
            stopRecognition();
        }
    }

    private void stopRecognition() {
        main.removeCallbacks(timeoutRunnable);
        shouldRecord.set(false);
        restartRecognition.set(false);
        mode.set(MODE_IDLE);
        pendingFinal.setLength(0);
        lastPartial = "";
        if (overlay != null) overlay.hide();
        abandonAudioFocus();
        state = ready ? "ready" : "disabled";
    }

    private void endCommandSession() {
        main.removeCallbacks(timeoutRunnable);
        pendingFinal.setLength(0);
        lastPartial = "";
        if (overlay != null) overlay.hide();
        if (Prefs.voiceEnabled(this) && Prefs.voiceWake(this) && ready) {
            enterWakeMode();
        } else {
            stopRecognition();
        }
    }

    private void requestRecognizerRestartIfNeeded(String kind) {
        if (!listening.get()) return;
        if (!kind.equals(activeKind) || restartRecognition.get()) {
            LogStore.add(this, "restart recognizer " + activeKind + " -> " + kind);
            restartRecognition.set(true);
            shouldRecord.set(false);
        }
    }

    private void ensureRecognitionRunning() {
        if (listening.get() || model == null || mode.get() == MODE_IDLE) return;
        listening.set(true);
        shouldRecord.set(true);
        restartRecognition.set(false);
        String kind = mode.get() == MODE_WAKE ? "wake" : "command";
        activeKind = kind;
        Model localModel = model;
        Thread thread = new Thread(() -> recognitionLoop(localModel, kind), "voice-asr");
        recognitionThread = thread;
        thread.start();
    }

    private void afterRecognitionStopped() {
        if (!running || mode.get() == MODE_IDLE || !ready || model == null) return;
        shouldRecord.set(true);
        ensureRecognitionRunning();
    }

    private void recognitionLoop(Model localModel, String kind) {
        Recognizer rec = null;
        AudioRecord record = null;
        boolean failed = false;
        try {
            rec = createRecognizer(localModel, kind);
            record = createAudioRecord();
            if (record == null) {
                main.post(() -> {
                    Toast.makeText(this, R.string.voice_permission_mic, Toast.LENGTH_LONG).show();
                    stopRecognition();
                });
                return;
            }
            audioRecord = record;
            recognizer = rec;
            record.startRecording();
            attachAudioEffects(record);
            softAgcGain = "wake".equals(kind) ? 4.0f : 6.0f;

            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int frame = min > 0 ? (min * 2) / 2 : 2048;
            if (frame < 2048) frame = 2048;
            short[] pcm = new short[frame];
            int skip = "command".equals(kind) ? 4000 : 0;

            while (shouldRecord.get() && !restartRecognition.get() && mode.get() != MODE_IDLE) {
                int read;
                try {
                    read = record.read(pcm, 0, frame);
                } catch (Throwable t) {
                    break;
                }
                if (read < 0) {
                    if (shouldRecord.get()) failed = true;
                    break;
                }
                if (read == 0) {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                if (skip > 0) {
                    skip -= read;
                    continue;
                }
                applySoftAgc(pcm, read);
                boolean finalResult;
                try {
                    finalResult = rec.acceptWaveForm(pcm, read);
                } catch (Throwable t) {
                    break;
                }
                if (finalResult) {
                    String json = rec.getResult();
                    main.post(() -> onAsrJson(json, true));
                } else {
                    String json = rec.getPartialResult();
                    main.post(() -> onAsrJson(json, false));
                }
            }

            if (!failed && shouldRecord.get() && !restartRecognition.get() && mode.get() != MODE_IDLE) {
                try {
                    String json = rec.getFinalResult();
                    main.post(() -> onAsrJson(json, true));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            failed = true;
            LogStore.add(this, "voice recognition failed: " + t);
            main.post(() -> state = "error");
        } finally {
            releaseAudioEffects();
            if (record != null) {
                try {
                    record.stop();
                } catch (Throwable ignored) {
                }
                try {
                    record.release();
                } catch (Throwable ignored) {
                }
            }
            if (rec != null) {
                try {
                    rec.close();
                } catch (Throwable ignored) {
                }
            }
            audioRecord = null;
            recognizer = null;
            listening.set(false);
            activeKind = null;
            if (failed) {
                main.post(this::stopRecognition);
            } else {
                main.post(this::afterRecognitionStopped);
            }
        }
    }

    private Recognizer createRecognizer(Model localModel, String kind) throws Exception {
        if ("wake".equals(kind)) {
            return new Recognizer(localModel, SAMPLE_RATE, wakeGrammar());
        }
        try {
            String grammar = VoiceGrammar.build(this);
            LogStore.add(this, "voice command grammar chars=" + grammar.length());
            return new Recognizer(localModel, SAMPLE_RATE, grammar);
        } catch (Throwable t) {
            LogStore.add(this, "voice grammar failed: " + t);
            try {
                return new Recognizer(localModel, SAMPLE_RATE, VoiceGrammar.build(this, false));
            } catch (Throwable t2) {
                LogStore.add(this, "voice base grammar failed: " + t2);
                return new Recognizer(localModel, SAMPLE_RATE);
            }
        }
    }

    private AudioRecord createAudioRecord() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 4096;
        int buffer = Math.max(min * 4, min);
        int[] sources = {
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
        };
        for (int source : sources) {
            AudioRecord record = null;
            try {
                record = new AudioRecord(source, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, buffer);
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    LogStore.add(this, "voice audio source=" + source);
                    return record;
                }
            } catch (Throwable t) {
                LogStore.add(this, "voice audio source " + source + " failed: " + t);
            }
            if (record != null) {
                try {
                    record.release();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private void attachAudioEffects(AudioRecord record) {
        releaseAudioEffects();
        int session = record.getAudioSessionId();
        if (session == 0) return;
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(session);
                if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
            } catch (Throwable ignored) {
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            try {
                hardwareAgc = AutomaticGainControl.create(session);
                if (hardwareAgc != null) hardwareAgc.setEnabled(true);
            } catch (Throwable ignored) {
            }
        }
        LogStore.add(this, "voice audiofx ns=" + (noiseSuppressor != null)
                + " agc=" + (hardwareAgc != null));
    }

    private void releaseAudioEffects() {
        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.release();
            } catch (Throwable ignored) {
            }
            noiseSuppressor = null;
        }
        if (hardwareAgc != null) {
            try {
                hardwareAgc.release();
            } catch (Throwable ignored) {
            }
            hardwareAgc = null;
        }
    }

    private double applySoftAgc(short[] data, int len) {
        double sum = 0;
        for (int i = 0; i < len; i++) {
            double v = data[i];
            sum += v * v;
        }
        double rms = Math.sqrt(sum / Math.max(1, len));
        if (rms > NOISE_FLOOR_RMS) {
            softAgcGain = (float) Math.max(1.0, Math.min(MAX_SOFT_GAIN, TARGET_RMS / rms)) * 0.18f
                    + softAgcGain * 0.82f;
        } else {
            softAgcGain = Math.max(2.0f, softAgcGain * 0.98f);
        }
        if (softAgcGain > 1.02f) {
            for (int i = 0; i < len; i++) {
                int v = (int) (data[i] * softAgcGain);
                if (v > 32767) v = 32767;
                else if (v < -32768) v = -32768;
                data[i] = (short) v;
            }
        }
        return rms;
    }

    private void onAsrJson(String json, boolean finalResult) {
        if (!running || mode.get() == MODE_IDLE) return;
        String text = textOf(json, finalResult ? "text" : "partial");
        if (text.isEmpty()) return;
        int currentMode = mode.get();
        if (currentMode == MODE_WAKE) {
            if (finalResult) handleWakeText(text);
            return;
        }
        if (currentMode != MODE_COMMAND) return;
        if (finalResult) {
            LogStore.add(this, "ASR final: " + text);
        }
        if (!finalResult) {
            lastText = text;
            lastPartial = text;
            if (overlay != null) overlay.setText(overlayText(text));
            resetTimeout();
            return;
        }
        handleCommandFinal(text);
    }

    private void handleCommandFinal(String text) {
        lastText = text;
        if (text.equals("unk") || text.equals("[unk]")) {
            resetTimeout();
            return;
        }
        if (pendingFinal.length() == 0) {
            String candidate = text;
            String partial = lastPartial;
            if (partial != null && partial.length() > candidate.length()
                    && partial.endsWith(candidate)) {
                candidate = partial;
            }
            VoiceCommands.Result direct = VoiceCommands.execute(this, candidate);
            if (direct.handled) {
                finishWithReply(direct.reply);
                return;
            }
            appendPending(text);
            lastPartial = text;
            if (overlay != null) overlay.setText(lastPartial);
            resetTimeout();
            return;
        }
        appendPending(text);
        String combined = pendingFinal.toString().trim();
        VoiceCommands.Result result = VoiceCommands.execute(this, combined);
        if (result.handled) {
            finishWithReply(result.reply);
            return;
        }
        lastPartial = combined;
        if (overlay != null) overlay.setText(combined);
        resetTimeout();
        if (pendingFinal.length() > 180) {
            pendingFinal.setLength(0);
        }
    }

    private void finishWithReply(String reply) {
        pendingFinal.setLength(0);
        lastPartial = "";
        if (overlay != null) overlay.setText(reply);
        Toast.makeText(getApplicationContext(), reply, Toast.LENGTH_SHORT).show();
        endCommandSession();
    }

    private void handleWakeText(String text) {
        if (!isWake(text)) return;
        long now = System.currentTimeMillis();
        if (now - lastWakeAtMs < WAKE_DEBOUNCE_MS) return;
        lastWakeAtMs = now;
        enterCommandMode(null);
    }

    private String overlayText(String partial) {
        if (pendingFinal.length() == 0) return partial;
        return pendingFinal.toString().trim() + " " + partial;
    }

    private void appendPending(String text) {
        if (pendingFinal.length() > 0) pendingFinal.append(' ');
        pendingFinal.append(text);
    }

    private void resetTimeout() {
        main.removeCallbacks(timeoutRunnable);
        if (mode.get() == MODE_COMMAND) {
            main.postDelayed(timeoutRunnable, LISTEN_TIMEOUT_MS);
        }
    }

    private void onTimeout() {
        if (mode.get() != MODE_COMMAND) return;
        boolean handled = false;
        String text = pendingFinal.length() > 0 ? pendingFinal.toString().trim() : lastPartial;
        if (lastPartial != null && lastPartial.length() > text.length()) text = lastPartial;
        pendingFinal.setLength(0);
        if (text != null && !text.isEmpty()) {
            VoiceCommands.Result result = VoiceCommands.execute(this, text);
            if (result.handled) {
                handled = true;
                Toast.makeText(getApplicationContext(), result.reply, Toast.LENGTH_SHORT).show();
            }
        }
        if (!handled) {
            Toast.makeText(getApplicationContext(), R.string.voice_no_match, Toast.LENGTH_SHORT).show();
        }
        endCommandSession();
    }

    private void requestAudioFocus(boolean exclusive) {
        AudioManager am = audioManager;
        if (am == null) return;
        abandonAudioFocus();
        try {
            AudioFocusRequest request = new AudioFocusRequest.Builder(
                    exclusive ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                            : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .build();
            focusRequest = request;
            am.requestAudioFocus(request);
        } catch (Throwable ignored) {
        }
    }

    private void abandonAudioFocus() {
        AudioManager am = audioManager;
        AudioFocusRequest request = focusRequest;
        focusRequest = null;
        if (am == null || request == null) return;
        try {
            am.abandonAudioFocusRequest(request);
        } catch (Throwable ignored) {
        }
    }

    private void startForegroundSafe() {
        try {
            Notification n = NotificationHelper.voice(this);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NotificationHelper.ID_VOICE, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NotificationHelper.ID_VOICE, n);
            }
        } catch (Throwable t) {
            LogStore.add(this, "voice foreground failed: " + t);
        }
    }

    private void registerKeyReceiver() {
        try {
            IntentFilter filter = new IntentFilter(ACTION_ECARX_KEY);
            filter.setPriority(999);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(keyReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(keyReceiver, filter);
            }
            LogStore.add(this, "voice key receiver registered");
        } catch (Throwable t) {
            LogStore.add(this, "voice key receiver failed: " + t);
        }
    }

    private void unregisterKeyReceiver() {
        try {
            unregisterReceiver(keyReceiver);
        } catch (Throwable ignored) {
        }
    }

    private void maybeStartKeyserver() {
        if (!Prefs.voiceEnabled(this) || !Prefs.voiceMicKey(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastKeyserverAt < 15000L) return;
        lastKeyserverAt = now;
        new Thread(() -> {
            ComponentName component = new ComponentName(KEYSERVER_PACKAGE, KEYSERVER_SERVICE);
            for (int i = 0; i < 2 && running; i++) {
                try {
                    startForegroundService(new Intent().setComponent(component));
                    LogStore.add(this, "keyserver start requested");
                } catch (Throwable t) {
                    LogStore.add(this, "keyserver start failed: " + t);
                    startKeyserverFallback();
                }
                try {
                    Intent boot = new Intent(KEYSERVER_BOOT_ACTION);
                    boot.setPackage(KEYSERVER_PACKAGE);
                    sendBroadcast(boot);
                } catch (Throwable t) {
                    LogStore.add(this, "keyserver broadcast failed: " + t);
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "keyserver-keepalive").start();
    }

    private void startKeyserverFallback() {
        try {
            String cmd = "am start-foreground-service -n " + KEYSERVER_PACKAGE + "/" + KEYSERVER_SERVICE
                    + " 2>/dev/null || am startservice -n " + KEYSERVER_PACKAGE + "/" + KEYSERVER_SERVICE
                    + " 2>/dev/null; am startservice -a " + KEYSERVER_BOOT_ACTION + " 2>/dev/null";
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            LogStore.add(this, "keyserver fallback requested");
        } catch (Throwable t) {
            LogStore.add(this, "keyserver fallback failed: " + t);
        }
    }

    private static boolean isWake(String text) {
        for (String phrase : VoiceCommands.WAKE) if (text.contains(phrase)) return true;
        return false;
    }

    private static String textOf(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "").toLowerCase(java.util.Locale.ROOT)
                    .replace('ё', 'е').trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private String wakeGrammar() {
        return "[\"слушай джили\",\"слушай джилли\",\"слушай джил\",\"[unk]\"]";
    }

    @Override
    public void onDestroy() {
        running = false;
        ready = false;
        state = "disabled";
        mode.set(MODE_IDLE);
        shouldRecord.set(false);
        restartRecognition.set(false);
        main.removeCallbacks(timeoutRunnable);
        unregisterKeyReceiver();
        if (overlay != null) {
            overlay.destroy();
            overlay = null;
        }
        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (Throwable ignored) {
            }
        }
        Thread thread = recognitionThread;
        recognitionThread = null;
        if (thread != null) {
            try {
                thread.join(2000L);
            } catch (InterruptedException ignored) {
            }
        }
        releaseAudioEffects();
        abandonAudioFocus();
        executor.shutdownNow();
        Model m = model;
        model = null;
        if (m != null) {
            try {
                m.close();
            } catch (Throwable ignored) {
            }
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
