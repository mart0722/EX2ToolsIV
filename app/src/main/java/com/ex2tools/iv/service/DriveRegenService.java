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
import com.ex2tools.iv.drive.DriveController;
import com.ex2tools.iv.state.VehicleState;

/**
 * «Применить сейчас»: один раз отправляет сохранённые drive/regen.
 * Никаких циклов и повторных попыток по событиям — этим занимается
 * DriveRegenWatchService, и только если пользователь его включил.
 */
public final class DriveRegenService extends Service {

    public static final String EXTRA_ONCE = "once";

    private static volatile boolean running;
    private Thread worker;

    public static void start(Context context, boolean once) {
        Intent i = new Intent(context, DriveRegenService.class);
        i.putExtra(EXTRA_ONCE, once);
        try {
            if (once || Prefs.autoApply(context)) {
                ContextCompat.startForegroundService(context, i);
            }
        } catch (Throwable t) {
            LogStore.add(context, "start drive service failed: " + t);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, DriveRegenService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_DRIVE, NotificationHelper.drive(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            LogStore.add(this, "drive service already running");
            return START_NOT_STICKY;
        }
        running = true;
        worker = new Thread(this::runApply, "ex2-drive");
        worker.start();
        return START_NOT_STICKY;
    }

    private void runApply() {
        try {
            VehicleState.get().ensure(this);
            DriveController controller = DriveController.get();
            long[] delays = {0L, 900L, 2200L};
            for (long delay : delays) {
                if (Thread.currentThread().isInterrupted()) break;
                if (delay > 0) Thread.sleep(delay);
                DriveController.Outcome r = controller.applySaved(this);
                LogStore.add(this, "apply now: " + r.describe());
                if (r.exact) break;
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
