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
import com.ex2tools.iv.body.BodyController;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.util.Arrays;

/**
 * «Окно следует за дверью»: при открытии двери на стоянке опускает стекло
 * этой двери, при закрытии возвращает прежнее положение.
 *
 * Зоны подобраны по данным Sapo/прошивки: двери 1/4/16/64,
 * стёкла 16/64/256/1024. Требует проверки на конкретной машине.
 */
public final class WindowFollowService extends Service {

    private static final int[] DOOR_AREAS = {1, 4, 16, 64};
    private static final int[] WINDOW_AREAS = {16, 64, 256, 1024};
    private static final int OPEN_POSITION = 12;

    private volatile boolean running;
    private Thread worker;

    /** Функция временно скрыта: код сохранён, запуск отключён. */
    private static final boolean FEATURE_ENABLED = false;

    public static void startIfEnabled(Context c) {
        if (!FEATURE_ENABLED || !Prefs.windowFollowDoor(c)) return;
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, WindowFollowService.class));
        } catch (Throwable t) {
            LogStore.add(c, "window follow start failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_WINDOW,
                NotificationHelper.windowFollow(this));
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker != null) return START_STICKY;
        worker = new Thread(this::runLoop, "ex2-window-follow");
        worker.start();
        return START_STICKY;
    }

    private void runLoop() {
        boolean[] open = new boolean[DOOR_AREAS.length];
        int[] saved = new int[DOOR_AREAS.length];
        Arrays.fill(saved, -1);
        try {
            for (int i = 0; i < 10; i++) {
                if (com.ex2tools.iv.state.VehicleState.get().ensure(this)) break;
                Thread.sleep(2000L);
            }
            while (running && Prefs.windowFollowDoor(this)) {
                boolean parked = Telemetry.gear(this) == CarProps.GEAR_PARK
                        || WidgetsCommon.readSpeed(this) < 5f;
                for (int i = 0; i < DOOR_AREAS.length; i++) {
                    Integer door = readAny(CarProps.DOOR_POS, CarProps.DOOR_MOVE,
                            DOOR_AREAS[i]);
                    boolean now = door != null && door > 0;
                    if (now && !open[i] && parked) {
                        Integer pos = readAny(CarProps.WINDOW_POSITION,
                                CarProps.WINDOW_MOVE, WINDOW_AREAS[i]);
                        saved[i] = pos == null ? -1 : pos;
                        if (saved[i] > OPEN_POSITION) {
                            boolean ok = BodyController.get().setWindow(OPEN_POSITION,
                                    new int[]{WINDOW_AREAS[i]}) != null;
                            LogStore.add(this, "window follow: door area " + DOOR_AREAS[i]
                                    + " raw=" + door + " open, window " + WINDOW_AREAS[i]
                                    + " " + saved[i] + " -> " + OPEN_POSITION
                                    + " ok=" + ok);
                        }
                    } else if (!now && open[i]) {
                        if (saved[i] >= 0) {
                            BodyController.get().setWindow(saved[i],
                                    new int[]{WINDOW_AREAS[i]});
                            LogStore.add(this, "window follow: door area " + DOOR_AREAS[i]
                                    + " closed, restore " + saved[i]);
                        }
                        saved[i] = -1;
                    }
                    open[i] = now;
                }
                Thread.sleep(500L);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogStore.add(this, "window follow error: " + t);
        } finally {
            stopSelf();
        }
    }

    private Integer readAny(int primary, int fallback, int area) {
        Integer value = read(primary, area);
        return value != null ? value : read(fallback, area);
    }

    private Integer read(int id, int area) {
        com.ex2tools.iv.state.VehicleState state = com.ex2tools.iv.state.VehicleState.get();
        Integer value = state.intValue(id, area);
        if (value != null) return value;
        int raw = com.ex2tools.iv.vehicle.VhalGateway.get().readInt(id, area);
        return raw == Integer.MIN_VALUE ? null : raw;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_WINDOW);
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
