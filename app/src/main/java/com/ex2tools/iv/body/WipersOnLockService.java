package com.ex2tools.iv.body;

import android.app.Service;
import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.servicemode.ServiceModeManager;
import com.ex2tools.iv.vehicle.VhalGateway;

public final class WipersOnLockService extends Service {
    private static final int IDLE = 0;
    private static final int LOCK = 1;
    private static final int UNLOCK = 3;
    private static final long MAX_EVENT_AGE_NS = 150_000_000L;

    private final VhalGateway gateway = VhalGateway.get();
    private HandlerThread eventThread;
    private Handler handler;
    private Car connection;
    private CarPropertyManager manager;
    private boolean lockHandled;
    private long listenFromNs;
    private long lastRequestNs;
    private volatile boolean stopping;

    private final Runnable subscribe = new Runnable() {
        @Override
        public void run() {
            if (stopping || manager != null || !enabled(WipersOnLockService.this)) return;
            if (gateway.ensure(WipersOnLockService.this)) {
                try {
                    if (connection == null) connection = Car.createCar(WipersOnLockService.this, handler);
                    if (connection != null
                            && connection.getCarManager("property") instanceof CarPropertyManager current) {
                        // Registration may replay the retained value of this momentary request.
                        listenFromNs = SystemClock.elapsedRealtimeNanos();
                        if (current.registerCallback(callback, CarProps.ALL_DOORS_LOCK_REQUEST, 0f)) {
                            manager = current;
                            LogStore.add(WipersOnLockService.this, "wipers on lock: subscribed");
                            return;
                        }
                    }
                } catch (LinkageError e) {
                    LogStore.add(WipersOnLockService.this, "wipers on lock unsupported: " + e);
                    stopSelf();
                    return;
                } catch (RuntimeException e) {
                    LogStore.add(WipersOnLockService.this, "wipers on lock subscription: " + e);
                }
            }
            handler.postDelayed(this, 2000L);
        }
    };

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (stopping || value == null || value.getPropertyId() != CarProps.ALL_DOORS_LOCK_REQUEST
                            || value.getAreaId() != 0 || !(value.getValue() instanceof Integer request)) {
                        return;
                    }
                    // Flyme marks these request pulses with status 3, including genuine key lock events.
                    handleRequest(request, value.getTimestamp());
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                }
            };

    private void handleRequest(int request, long timestamp) {
        if (request != IDLE && request != LOCK && request != UNLOCK) return;
        long age = SystemClock.elapsedRealtimeNanos() - timestamp;
        if (timestamp < listenFromNs || timestamp <= lastRequestNs || age < 0L) return;
        lastRequestNs = timestamp;

        // The request returns to neutral after each pulse. That transition can arrive
        // after the actuation window and still ends the current pulse.
        if (request == IDLE || request == UNLOCK) {
            lockHandled = false;
            return;
        }
        if (age > MAX_EVENT_AGE_NS) {
            LogStore.add(this, "wipers on lock: stale signal age=" + age / 1_000_000L + " ms");
            return;
        }
        if (lockHandled || !enabled(this)) return;
        lockHandled = true;
        BodyController.WipersOnLockResult result =
                BodyController.get().raiseWipersOnLock(timestamp + MAX_EVENT_AGE_NS);
        long elapsed = (SystemClock.elapsedRealtimeNanos() - timestamp) / 1_000_000L;
        LogStore.add(this, "wipers on lock: " + result
                + ", received=" + age / 1_000_000L + " ms, completed=" + elapsed + " ms");
    }

    private static boolean enabled(Context context) {
        return Prefs.wipersOnLock(context) && !ServiceModeManager.isActive(context);
    }

    public static void sync(Context context) {
        if (!enabled(context)) {
            stop(context);
            return;
        }
        try {
            ContextCompat.startForegroundService(context, new Intent(context, WipersOnLockService.class));
        } catch (RuntimeException e) {
            LogStore.add(context, "start wipers on lock failed: " + e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, WipersOnLockService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        eventThread = new HandlerThread("ex2-wipers-on-lock");
        eventThread.start();
        handler = new Handler(eventThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NotificationHelper.ID_WIPERS_ON_LOCK, NotificationHelper.wipersOnLock(this));
        if (!enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(subscribe);
        handler.post(subscribe);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            try {
                if (manager != null) manager.unregisterCallback(callback);
                if (connection != null) connection.disconnect();
            } catch (RuntimeException ignored) {
            }
        });
        eventThread.quitSafely();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
