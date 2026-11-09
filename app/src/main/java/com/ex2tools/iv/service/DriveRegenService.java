package com.ex2tools.iv.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.DriveRegenController;
import com.ex2tools.iv.car.Telemetry;

public final class DriveRegenService extends Service {

    public static final String EXTRA_ONCE = "once";

    private static volatile boolean running;
    private Thread worker;

    public static void start(Context context, boolean once) {
        Intent i = new Intent(context, DriveRegenService.class);
        i.putExtra(EXTRA_ONCE, once);
        try {
            ContextCompat.startForegroundService(context, i);
        } catch (Throwable t) {
            LogStore.add(context, "start drive service failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_DRIVE, NotificationHelper.drive(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean once = intent != null && intent.getBooleanExtra(EXTRA_ONCE, false);
        if (running) {
            LogStore.add(this, "drive service already running");
            return START_NOT_STICKY;
        }
        if (!once && !Prefs.autoApply(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        worker = new Thread(() -> runApply(once), "ex2-drive");
        worker.start();
        return START_NOT_STICKY;
    }

    private void runApply(boolean once) {
        try {
            long[] delays = once
                    ? new long[]{0L, 900L, 2200L}
                    : new long[]{0L, 1500L, 4000L, 8000L, 16000L};
            DriveRegenController controller = new DriveRegenController(this);
            for (long delay : delays) {
                if (Thread.currentThread().isInterrupted()) break;
                if (delay > 0) Thread.sleep(delay);
                DriveRegenController.Result r = controller.applySaved();
                LogStore.add(this, "apply: " + r.message + " exact=" + r.exact);
                if (once && r.exact) break;
            }

            if (!once && Prefs.autoApply(this)) {
                long end = System.currentTimeMillis() + 180000L;
                int lastGear = Telemetry.gear(this);
                while (!Thread.currentThread().isInterrupted()
                        && System.currentTimeMillis() < end) {
                    Thread.sleep(1000L);
                    int gear = Telemetry.gear(this);
                    if (gear != Integer.MIN_VALUE && gear != lastGear) {
                        lastGear = gear;
                        controller.applySaved();
                        LogStore.add(this, "re-applied after gear " + gear);
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogStore.add(this, "drive service error: " + t);
        } finally {
            running = false;
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_DRIVE);
        } catch (Throwable ignored) {
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
