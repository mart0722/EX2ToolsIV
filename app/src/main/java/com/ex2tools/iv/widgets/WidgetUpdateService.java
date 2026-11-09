package com.ex2tools.iv.widgets;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.Telemetry;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WidgetUpdateService extends Service {

    private static final long POLL_MS = 3000L;
    private static final long RETRY_MS = 6000L;
    private static final long CACHE_TTL_MS = 60000L;

    private static final Class<?>[] PROVIDERS = {
            ClockWidgetProvider.class,
            DriveRegenWidgetProvider.class,
            DriveModeVerticalWidgetProvider.class,
            RegenVerticalWidgetProvider.class,
            FanSpeedWidgetProvider.class,
            FanSpeedVerticalWidgetProvider.class,
            TemperatureWidgetProvider.class,
            TemperatureVerticalWidgetProvider.class,
            HvacDirectionWidgetProvider.class,
            HvacDirectionVerticalWidgetProvider.class,
            HvacDirectionFanWidgetProvider.class,
            HeatingTestWidgetProvider.class
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingActions = new ConcurrentLinkedQueue<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!hasAnyWidget(WidgetUpdateService.this)) {
                if (pendingActions.isEmpty()) {
                    stopSelf();
                    return;
                }
                handler.postDelayed(this, RETRY_MS);
                return;
            }
            requestUpdate();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            requestUpdate();
        }
    };

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, WidgetUpdateService.class));
        } catch (Throwable t) {
            LogStore.add(c, "widget service start failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WidgetUpdateService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context c) {
        if (hasAnyWidget(c)) start(c);
        else stop(c);
    }

    public static void refreshOpacity(Context c) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, WidgetUpdateService.class).setAction(WidgetsCommon.REFRESH_OPACITY));
        } catch (Throwable t) {
            LogStore.add(c, "widget opacity refresh failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_WIDGETS, NotificationHelper.widgets(this));
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action != null && !WidgetsCommon.REFRESH_OPACITY.equals(action)) {
            pendingActions.add(action);
        }
        requestUpdate();
        return START_STICKY;
    }

    private void requestUpdate() {
        if (!busy.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                updateAll();
            } catch (Throwable t) {
                LogStore.add(this, "widget update failed: " + t);
            } finally {
                busy.set(false);
            }
        });
    }

    private void updateAll() {
        if (!hasAnyWidget(this)) {
            if (pendingActions.isEmpty()) stopSelf();
            else scheduleRetry();
            return;
        }

        CarConnection cc = CarConnection.get();
        boolean carReady = cc.ensure(this);
        if (!pendingActions.isEmpty()) {
            if (!carReady) {
                scheduleRetry();
                return;
            }
            String action;
            while ((action = pendingActions.poll()) != null) {
                try {
                    handleAction(action);
                } catch (Throwable t) {
                    LogStore.add(this, "widget action failed " + action + ": " + t);
                }
            }
        }

        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        int[] driveRegenIds = ids(awm, DriveRegenWidgetProvider.class);
        int[] driveVerticalIds = ids(awm, DriveModeVerticalWidgetProvider.class);
        int[] regenVerticalIds = ids(awm, RegenVerticalWidgetProvider.class);
        int[] fanIds = ids(awm, FanSpeedWidgetProvider.class);
        int[] fanVerticalIds = ids(awm, FanSpeedVerticalWidgetProvider.class);
        int[] tempIds = ids(awm, TemperatureWidgetProvider.class);
        int[] tempVerticalIds = ids(awm, TemperatureVerticalWidgetProvider.class);
        int[] ventIds = ids(awm, HvacDirectionWidgetProvider.class);
        int[] ventVerticalIds = ids(awm, HvacDirectionVerticalWidgetProvider.class);
        int[] ventFanIds = ids(awm, HvacDirectionFanWidgetProvider.class);
        int[] heatingIds = ids(awm, HeatingTestWidgetProvider.class);

        boolean needDrive = driveRegenIds.length > 0 || driveVerticalIds.length > 0;
        boolean needRegen = driveRegenIds.length > 0 || regenVerticalIds.length > 0;
        boolean needFan = fanIds.length > 0 || fanVerticalIds.length > 0 || ventFanIds.length > 0;
        boolean needTemp = tempIds.length > 0 || tempVerticalIds.length > 0;
        boolean needVent = ventIds.length > 0 || ventVerticalIds.length > 0 || ventFanIds.length > 0;
        boolean needAc = ventFanIds.length > 0;
        boolean needHeating = heatingIds.length > 0;

        Integer drive = needDrive ? intOrNull(Telemetry.driveNow(this)) : null;
        Integer regen = needRegen ? intOrNull(Telemetry.regenNow(this)) : null;
        Integer fan = needFan ? readFanCached() : null;
        Float temp = needTemp ? readTempCached() : null;
        Integer vent = needVent ? WidgetsCommon.readVent() : null;
        Boolean recirc = needVent ? WidgetsCommon.readRecirc() : null;
        Boolean ac = needAc ? WidgetsCommon.readAc() : null;

        Boolean rear = null;
        Boolean front = null;
        Boolean windshield = null;
        Boolean wheel = null;
        Integer seatDriver = null;
        Integer seatPassenger = null;
        if (needHeating) {
            rear = WidgetsCommon.readRearDefrost();
            front = WidgetsCommon.readFrontMaxDefrost();
            windshield = WidgetsCommon.readWindshieldHeat();
            wheel = WidgetsCommon.readWheelHeat();
            seatDriver = WidgetsCommon.readSeatHeat(1);
            seatPassenger = WidgetsCommon.readSeatHeat(4);
        }

        boolean any = false;
        any |= update(awm, driveRegenIds, WidgetsRenderer.driveRegen(this, drive, regen));
        any |= update(awm, driveVerticalIds, WidgetsRenderer.driveVertical(this, drive));
        any |= update(awm, regenVerticalIds, WidgetsRenderer.regenVertical(this, regen));
        any |= update(awm, fanIds, WidgetsRenderer.fan(this, fan, R.layout.widget_fan_speed));
        any |= update(awm, fanVerticalIds, WidgetsRenderer.fan(this, fan, R.layout.widget_fan_speed_vertical));
        any |= update(awm, tempIds, WidgetsRenderer.temperature(this, temp, R.layout.widget_temperature));
        any |= update(awm, tempVerticalIds,
                WidgetsRenderer.temperature(this, temp, R.layout.widget_temperature_vertical));
        any |= update(awm, ventIds, WidgetsRenderer.vent(this, vent, recirc));
        any |= update(awm, ventVerticalIds, WidgetsRenderer.ventVertical(this, vent, recirc));
        any |= update(awm, ventFanIds,
                WidgetsRenderer.ventFan(this, vent, recirc, fan, ac));
        any |= update(awm, heatingIds,
                WidgetsRenderer.heating(this, wheel, rear, front, windshield,
                        seatDriver, seatPassenger));
        if (ids(awm, ClockWidgetProvider.class).length > 0) {
            ClockWidgetProvider.refreshAll(this);
            any = true;
        }

        if (!any && pendingActions.isEmpty()) stopSelf();
    }

    private Integer readFanCached() {
        Integer value = WidgetsCommon.readFan();
        if (value != null) {
            Prefs.setWidgetFanCache(this, value);
            return value;
        }
        long ts = Prefs.widgetFanCacheTs(this);
        if (ts > 0 && System.currentTimeMillis() - ts <= CACHE_TTL_MS) {
            int cached = Prefs.widgetFanCache(this);
            if (cached >= 0) return cached;
        }
        return null;
    }

    private Float readTempCached() {
        Float value = WidgetsCommon.readTemp(this);
        if (value != null) {
            Prefs.setWidgetTempCache(this, value);
            return value;
        }
        long ts = Prefs.widgetTempCacheTs(this);
        if (ts > 0 && System.currentTimeMillis() - ts <= CACHE_TTL_MS) {
            float cached = Prefs.widgetTempCache(this);
            if (!Float.isNaN(cached)) return cached;
        }
        return null;
    }

    private int[] ids(AppWidgetManager awm, Class<?> provider) {
        try {
            int[] ids = awm.getAppWidgetIds(new ComponentName(this, provider));
            return ids == null ? new int[0] : ids;
        } catch (Throwable t) {
            return new int[0];
        }
    }

    private static boolean hasAnyWidget(Context c) {
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(c);
            for (Class<?> provider : PROVIDERS) {
                int[] ids = awm.getAppWidgetIds(new ComponentName(c, provider));
                if (ids != null && ids.length > 0) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean update(AppWidgetManager awm, int[] ids, RemoteViews views) {
        if (ids.length == 0) return false;
        for (int id : ids) awm.updateAppWidget(id, views);
        return true;
    }

    private void scheduleRetry() {
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, RETRY_MS);
    }

    private void handleAction(String action) {
        switch (action) {
            case WidgetsCommon.DRIVE_ECO:
                WidgetsCommon.applyDrive(this, 570491137);
                break;
            case WidgetsCommon.DRIVE_COMFORT:
                WidgetsCommon.applyDrive(this, 570491138);
                break;
            case WidgetsCommon.DRIVE_SPORT:
                WidgetsCommon.applyDrive(this, 570491139);
                break;
            case WidgetsCommon.DRIVE_NEXT:
                WidgetsCommon.stepDrive(this, 1);
                break;
            case WidgetsCommon.DRIVE_PREV:
                WidgetsCommon.stepDrive(this, -1);
                break;
            case WidgetsCommon.REGEN_LOW:
                WidgetsCommon.applyRegen(this, 537003265);
                break;
            case WidgetsCommon.REGEN_MID:
                WidgetsCommon.applyRegen(this, 537003266);
                break;
            case WidgetsCommon.REGEN_HIGH:
                WidgetsCommon.applyRegen(this, 537003267);
                break;
            case WidgetsCommon.REGEN_NEXT:
                WidgetsCommon.stepRegen(this, 1);
                break;
            case WidgetsCommon.REGEN_PREV:
                WidgetsCommon.stepRegen(this, -1);
                break;
            case WidgetsCommon.FAN_PLUS:
                WidgetsCommon.stepFan(1);
                break;
            case WidgetsCommon.FAN_MINUS:
                WidgetsCommon.stepFan(-1);
                break;
            case WidgetsCommon.TEMP_PLUS:
                WidgetsCommon.stepTemp(this, 1f);
                break;
            case WidgetsCommon.TEMP_MINUS:
                WidgetsCommon.stepTemp(this, -1f);
                break;
            case WidgetsCommon.VENT_FACE:
                WidgetsCommon.setVent(1);
                break;
            case WidgetsCommon.VENT_FLOOR:
                WidgetsCommon.setVent(2);
                break;
            case WidgetsCommon.VENT_FACE_FLOOR:
                WidgetsCommon.setVent(3);
                break;
            case WidgetsCommon.VENT_WINDSHIELD:
                WidgetsCommon.setVent(4);
                break;
            case WidgetsCommon.VENT_FLOOR_WINDSHIELD:
                WidgetsCommon.setVent(6);
                break;
            case WidgetsCommon.VENT_NEXT:
                WidgetsCommon.stepVent(1);
                break;
            case WidgetsCommon.VENT_PREV:
                WidgetsCommon.stepVent(-1);
                break;
            case WidgetsCommon.RECIRC_TOGGLE:
                WidgetsCommon.toggleRecirc(this);
                break;
            case WidgetsCommon.AC_TOGGLE:
                WidgetsCommon.toggleAc(this);
                break;
            case WidgetsCommon.REAR_DEFROST_TOGGLE:
                WidgetsCommon.toggleRearDefrost(this);
                break;
            case WidgetsCommon.FRONT_MAX_DEFROST_TOGGLE:
                WidgetsCommon.toggleFrontMaxDefrost(this);
                break;
            case WidgetsCommon.WINDSHIELD_HEAT_TOGGLE:
                WidgetsCommon.toggleWindshieldHeat(this);
                break;
            case WidgetsCommon.WHEEL_HEAT_TOGGLE:
                WidgetsCommon.toggleWheelHeat(this);
                break;
            case WidgetsCommon.SEAT_DRIVER_CYCLE:
                WidgetsCommon.cycleSeat(this, 1);
                break;
            case WidgetsCommon.SEAT_PASSENGER_CYCLE:
                WidgetsCommon.cycleSeat(this, 4);
                break;
            default:
                break;
        }
    }

    private static Integer intOrNull(int value) {
        return value == Integer.MIN_VALUE ? null : value;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
