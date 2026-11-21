package com.ex2tools.iv.lock;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.media.AudioManager;
import android.telecom.TelecomManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Автоблокировка экрана.
 *
 * Если по экрану давно не было касаний/нажатий — открывает LockActivity (тот же
 * экран, что и по кнопке замка). Время последнего действия берём из PowerManager:
 * `dumpsys power` печатает mLastUserActivityTime в миллисекундах uptime. Это
 * глобальный источник, он не требует root и не требует службы доступности.
 *
 * Блокируем в любом режиме: пользователь сам включает функцию и понимает, что
 * навигация тоже закроется.
 */
public final class AutoLockService extends Service {

    private static final long POLL_MS = 5_000L;
    private static final long MIN_LOCK_GAP_MS = 3_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private long lastLockAt;
    private boolean parseWarned;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            check();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, AutoLockService.class));
        } catch (Throwable t) {
            LogStore.add(c, "start auto lock failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, AutoLockService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context c) {
        if (Prefs.autoLock(c)) start(c);
        else stop(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_AUTOLOCK, NotificationHelper.autoLock(this));
        running = true;
        handler.post(tick);
        LogStore.add(this, "auto lock service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.autoLock(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_AUTOLOCK);
        } catch (Throwable ignored) {
        }
        stopForeground(true);
        LogStore.add(this, "auto lock service stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Не закрываем экран вызова блокировкой. */
    private boolean inCall() {
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm != null && tm.isInCall()) return true;
        } catch (Throwable ignored) {
        }
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                return mode == AudioManager.MODE_IN_CALL
                        || mode == AudioManager.MODE_IN_COMMUNICATION
                        || mode == AudioManager.MODE_RINGTONE;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void check() {
        try {
            if (!Prefs.autoLock(this)) {
                stopSelf();
                return;
            }
            if (LockActivity.isShowing()) return;
            if (inCall()) return;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return;

            long now = SystemClock.uptimeMillis();
            if (lastLockAt > 0L && now - lastLockAt < MIN_LOCK_GAP_MS) return;

            long last = lastUserActivity();
            if (last <= 0L) return;

            int timeout = Math.max(15, Prefs.autoLockTimeoutSec(this));
            long idle = now - last;
            if (idle >= timeout * 1000L) {
                lastLockAt = now;
                LogStore.add(this, String.format(Locale.US,
                        "auto lock: idle %ds (timeout %ds)", idle / 1000L, timeout));
                Intent i = new Intent(this, LockActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            }
        } catch (Throwable t) {
            LogStore.add(this, "auto lock check error: " + t);
        }
    }

    /** mLastUserActivityTime из dumpsys power; -1, если не удалось прочитать. */
    private long lastUserActivity() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "dumpsys power"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            long value = -1L;
            String key = "mLastUserActivityTime=";
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf(key);
                if (idx < 0) continue;
                String rest = line.substring(idx + key.length()).trim();
                int sp = rest.indexOf(' ');
                if (sp > 0) rest = rest.substring(0, sp);
                try {
                    value = Long.parseLong(rest);
                } catch (Throwable ignored) {
                }
                break;
            }
            reader.close();
            p.waitFor();
            if (value < 0 && !parseWarned) {
                parseWarned = true;
                LogStore.add(this, "auto lock: mLastUserActivityTime not found");
            }
            return value;
        } catch (Throwable t) {
            if (!parseWarned) {
                parseWarned = true;
                LogStore.add(this, "auto lock: dumpsys power failed: " + t);
            }
            return -1L;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
