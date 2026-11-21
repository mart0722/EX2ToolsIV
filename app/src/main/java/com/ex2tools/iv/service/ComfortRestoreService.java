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
import com.ex2tools.iv.climate.ClimateController;
import com.ex2tools.iv.state.VehicleState;

/** Восстанавливает обогрев руля и сидений после загрузки/пробуждения. */
public final class ComfortRestoreService extends Service {

    private Thread worker;

    public static void startIfEnabled(Context c) {
        if (!Prefs.comfortRestore(c)) return;
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, ComfortRestoreService.class));
        } catch (Throwable t) {
            LogStore.add(c, "comfort restore start failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_COMFORT,
                NotificationHelper.comfort(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker != null) return START_NOT_STICKY;
        worker = new Thread(this::runRestore, "ex2-comfort");
        worker.start();
        return START_NOT_STICKY;
    }

    private void runRestore() {
        try {
            VehicleState state = VehicleState.get();
            for (int i = 0; i < 12; i++) {
                if (state.ensure(this)) break;
                Thread.sleep(2000L);
            }
            if (!state.isReady()) {
                LogStore.add(this, "comfort restore: VHAL not ready");
                return;
            }
            Thread.sleep(1500L);
            int wheel = Prefs.savedWheelHeat(this);
            int driver = Prefs.savedSeatHeat(this, 1);
            int passenger = Prefs.savedSeatHeat(this, 4);
            ClimateController controller = ClimateController.get();
            if (wheel > 0) controller.setWheelHeatLevel(wheel);
            if (driver > 0) controller.setSeatHeat(1, driver);
            if (passenger > 0) controller.setSeatHeat(4, passenger);
            LogStore.add(this, "comfort restore: wheel=" + wheel
                    + " seat1=" + driver + " seat4=" + passenger);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogStore.add(this, "comfort restore error: " + t);
        } finally {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (worker != null) worker.interrupt();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_COMFORT);
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
