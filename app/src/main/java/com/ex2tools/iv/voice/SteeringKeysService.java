package com.ex2tools.iv.voice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.ex2tools.iv.App;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.apps.SteeringPatch;

/** Button handling runs independently of speech recognition and microphone access. */
public final class SteeringKeysService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SteeringMode mode;
    private long lastTap;
    private boolean registered;
    private final BroadcastReceiver keys = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!Prefs.steeringClimate(c) || mode == null || i == null) return;
            if (VoiceService.ACTION_ECARX_KEY.equals(i.getAction())) {
                if (i.getIntExtra("ecarx.extra.ECARX_KEY_ACTION_TYPE", -1) != 0) return;
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastTap < 100) return;
                lastTap = now;
                mode.tap(SteeringKeysService.this::voice);
            } else if ("com.ex2tools.iv.CLIMATE_STEERING_KEY".equals(i.getAction())) {
                mode.key(i.getIntExtra("key", 0));
            }
        }
    };
    private final Runnable keepAlive = new Runnable() {
        @Override public void run() {
            if (!Prefs.steeringClimate(SteeringKeysService.this)) { stopSelf(); return; }
            startKeyServer(SteeringKeysService.this);
            main.postDelayed(this, 15000);
        }
    };

    public static boolean startKeyServer(Context c) {
        try {
            return c.startForegroundService(new Intent().setComponent(new ComponentName(
                    SteeringPatch.PACKAGE, "com.flyme.auto.keyserver.service.AutoKeyService"))) != null;
        } catch (RuntimeException e) {
            LogStore.add(c, "steering keyserver: " + e);
            return false;
        }
    }

    public static void sync(Context c) {
        Intent i = new Intent(c, SteeringKeysService.class);
        if (Prefs.steeringClimate(c)) {
            try { ContextCompat.startForegroundService(c, i); }
            catch (Throwable t) { LogStore.add(c, "steering start: " + t); }
        } else c.stopService(i);
    }
    @Override public void onCreate() {
        super.onCreate();
        startForeground(17041, new NotificationCompat.Builder(this, App.CH_SERVICE)
                .setSmallIcon(R.drawable.ic_voice).setContentTitle(getString(R.string.steering_title))
                .setContentText(getString(R.string.steering_mode_hint)).setOngoing(true).build());
        mode = new SteeringMode(this, main, this::voice);
        IntentFilter filter = new IntentFilter(VoiceService.ACTION_ECARX_KEY);
        filter.addAction("com.ex2tools.iv.CLIMATE_STEERING_KEY");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(keys, filter, "android.permission.WRITE_SECURE_SETTINGS", main, Context.RECEIVER_EXPORTED);
        } else registerReceiver(keys, filter, "android.permission.WRITE_SECURE_SETTINGS", main);
        registered = true;
        main.post(keepAlive);
    }
    private void voice() {
        if (Prefs.voiceEnabled(this) && Prefs.voiceMicKey(this)) {
            ContextCompat.startForegroundService(this,
                    new Intent(this, VoiceService.class).setAction(VoiceService.ACTION_TOGGLE));
        }
    }
    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (!Prefs.steeringClimate(this)) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public void onDestroy() {
        if (mode != null) mode.close();
        main.removeCallbacksAndMessages(null);
        if (registered) unregisterReceiver(keys);
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
