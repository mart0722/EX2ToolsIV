package com.ex2tools.iv.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.DriveRegenController;
import com.ex2tools.iv.car.Telemetry;

public final class DriveRegenWatchService extends Service {

    private static volatile boolean running;
    private volatile boolean forceApply;
    private Thread worker;
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            forceApply = true;
        }
    };

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, DriveRegenWatchService.class));
        } catch (Throwable t) {
            LogStore.add(context, "start drive watch failed: " + t);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, DriveRegenWatchService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context context) {
        if (Prefs.autoApply(context)) start(context);
        else stop(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_DRIVE_WATCH, NotificationHelper.driveWatch(this));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            ContextCompat.registerReceiver(this, wakeReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            LogStore.add(this, "drive watch receiver failed: " + t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.autoApply(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        forceApply = true;
        if (!running) {
            running = true;
            worker = new Thread(this::runWatch, "ex2-drive-watch");
            worker.start();
        }
        return START_STICKY;
    }

    private void runWatch() {
        try {
            DriveRegenController controller = new DriveRegenController(this);
            long[] delays = {0L, 1500L, 4000L, 8000L, 16000L};
            for (long delay : delays) {
                if (Thread.currentThread().isInterrupted()) return;
                if (delay > 0) Thread.sleep(delay);
                DriveRegenController.Result r = controller.applySaved();
                LogStore.add(this, "watch apply: " + r.message + " exact=" + r.exact);
                if (r.exact) break;
            }

            int lastGear = Telemetry.gear(this);
            while (!Thread.currentThread().isInterrupted()) {
                if (!Prefs.autoApply(this)) break;
                if (forceApply) {
                    forceApply = false;
                    DriveRegenController.Result r = controller.applySaved();
                    LogStore.add(this, "watch wake apply: " + r.message + " exact=" + r.exact);
                }
                int gear = Telemetry.gear(this);
                if (gear != Integer.MIN_VALUE && gear != lastGear) {
                    lastGear = gear;
                    DriveRegenController.Result r = controller.applySaved();
                    LogStore.add(this, "watch gear " + gear + ": " + r.message + " exact=" + r.exact);
                }
                Thread.sleep(2000L);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogStore.add(this, "drive watch error: " + t);
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
            unregisterReceiver(wakeReceiver);
        } catch (Throwable ignored) {
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_DRIVE_WATCH);
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
