package com.ex2tools.iv.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.climate.ClimateLog;
import com.ex2tools.iv.climate.Thermostat;
import com.ex2tools.iv.sensor.CabinSensor;
import com.ex2tools.iv.sensor.MiBeacon;

/**
 * Фоновый сервис климат-контроля: держит BLE-скан датчика и передаёт
 * показания в {@link Thermostat}. Работает только когда режим включён.
 */
public final class CabinClimateService extends Service implements CabinSensor.Listener {

    private static final long TICK_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static volatile boolean running;
    private long lastNotifiedAt;

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, CabinClimateService.class));
        } catch (Throwable t) {
            LogStore.add(context, "climate service start failed: " + t);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, CabinClimateService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context context) {
        if (Prefs.climateEnabled(context) && Prefs.climateUiVisible(context)) {
            start(context);
        } else {
            stop(context);
        }
        try {
            com.ex2tools.iv.widgets.OverlayWidgetService.sync(context);
        } catch (Throwable t) {
            LogStore.add(context, "climate overlay sync failed: " + t);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            CabinSensor sensor = CabinSensor.get(CabinClimateService.this);
            if (Prefs.climateEnabled(CabinClimateService.this) && !sensor.isScanning()
                    && !Prefs.sensorMac(CabinClimateService.this).isEmpty()) {
                sensor.start();
            }
            updateNotification();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(NotificationHelper.ID_CLIMATE,
                NotificationHelper.climate(this, "запуск"));
        CabinSensor.get(this).addListener(this);
        if (Prefs.climateEnabled(this) && !Prefs.sensorMac(this).isEmpty()) {
            CabinSensor.get(this).start();
        }
        handler.post(tick);
        LogStore.add(this, "climate service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.climateEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Prefs.sensorMac(this).isEmpty()) {
            CabinSensor.get(this).start();
        }
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onReading(MiBeacon.Reading reading) {
        if (!Prefs.climateEnabled(this)) return;
        ClimateLog.append(this, "packet", reading);
        Thermostat.onReading(this, reading);
        long now = System.currentTimeMillis();
        if (now - lastNotifiedAt > 15_000L) {
            lastNotifiedAt = now;
            updateNotification();
        }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            String text = Thermostat.status(this);
            MiBeacon.Reading reading = CabinSensor.get(this).last();
            if (reading != null && reading.hasData()) {
                StringBuilder sb = new StringBuilder(text);
                if (reading.humidity >= 0) sb.append(" · ").append(reading.humidity).append('%');
                if (reading.battery >= 0) sb.append(" · батарея ").append(reading.battery).append('%');
                text = sb.toString();
            }
            nm.notify(NotificationHelper.ID_CLIMATE,
                    NotificationHelper.climate(this, text));
        } catch (Throwable t) {
            LogStore.add(this, "climate notification failed: " + t);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        CabinSensor.get(this).removeListener(this);
        CabinSensor.get(this).stop();
        if (!Prefs.climateEnabled(this)) {
            Thermostat.restore(this);
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_CLIMATE);
        } catch (Throwable ignored) {
        }
        stopForeground(true);
        LogStore.add(this, "climate service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
