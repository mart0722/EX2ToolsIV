package com.ex2tools.iv.widgets;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
import com.ex2tools.iv.car.RangeModel;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.state.VehicleState;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WidgetUpdateService extends Service {

    private static final long POLL_MS = 3000L;
    private static final long SPEED_POLL_MS = 500L;
    private static final long RETRY_MS = 6000L;
    private static final long CACHE_TTL_MS = 60000L;

    private static final Class<?>[] PROVIDERS = {
            ClockWidgetProvider.class,
            LargeClockWidgetProvider.class,
            OutsideTemperatureWidgetProvider.class,
            OutsideTemperatureWideWidgetProvider.class,
            ClockDateWidgetProvider.class,
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
            HeatingTestWidgetProvider.class,
            WindshieldHeatWidgetProvider.class,
            WheelHeatWidgetProvider.class,
            RecircWidgetProvider.class,
            BatteryRangeWidgetProvider.class,
            RealRangeWidgetProvider.class,
            WipersWidgetProvider.class,
            PowerOffWidgetProvider.class,
            LockWidgetProvider.class
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingActions = new ConcurrentLinkedQueue<>();
    private final VehicleState.Listener stateListener = this::requestUpdate;
    private long lastTemperatureActionAt;
    private long lastFanActionAt;
    private volatile boolean updatePending;

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

    private final Runnable speedTick = new Runnable() {
        @Override
        public void run() {
            if (!hasSpeedWidget(WidgetUpdateService.this)) {
                handler.postDelayed(this, SPEED_POLL_MS);
                return;
            }
            executor.execute(() -> {
                float speed = WidgetsCommon.readSpeed(WidgetUpdateService.this);
                if (!Float.isNaN(speed)) {
                    if (speedChanged(WidgetUpdateService.this, speed)) {
                        Prefs.setSpeedCache(WidgetUpdateService.this, speed);
                    }
                } else {
                    speed = Prefs.speedCache(WidgetUpdateService.this);
                }
                SpeedWidgetProvider.updateCached(WidgetUpdateService.this, speed);
            });
            handler.postDelayed(this, SPEED_POLL_MS);
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
        WidgetsCommon.subscribeAll(this);
        VehicleState.get().addListener(stateListener);
        handler.post(tick);
        handler.post(speedTick);
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

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        requestUpdate();
    }

    private void requestUpdate() {
        updatePending = true;
        if (executor.isShutdown() || !busy.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                do {
                    updatePending = false;
                    updateAll();
                } while (updatePending && !executor.isShutdown());
            } catch (Throwable t) {
                LogStore.add(this, "widget update failed: " + t);
            } finally {
                busy.set(false);
                if (updatePending && !executor.isShutdown()) requestUpdate();
            }
        });
    }

    private void updateAll() {
        VehicleState state = VehicleState.get();
        boolean carReady = state.ensure(this);
        if (!pendingActions.isEmpty()) {
            if (!carReady) {
                if (pendingActions.removeIf(WidgetsCommon.WIPERS_TOGGLE::equals)) {
                    showWipersUnavailable();
                }
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
            OverlayWidgetService.requestUpdate(this);
        }

        if (!hasAnyWidget(this)) {
            stopSelf();
            return;
        }

        // Обогрев руля: подтвердить или повторить отложенный запрос (нужен D).
        if (WidgetsCommon.wheelHeatPending(this)) {
            WidgetsCommon.checkWheelPending(this);
        }
        // Модель запаса хода: накапливаем дельты SOC/одометра (сама себя троттлит).
        RangeModel.sample(this);

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
        int[] outsideTempIds = ids(awm, OutsideTemperatureWidgetProvider.class);
        int[] outsideTempWideIds = ids(awm, OutsideTemperatureWideWidgetProvider.class);
        int[] recircIds = ids(awm, RecircWidgetProvider.class);
        int[] wheelIds = ids(awm, WheelHeatWidgetProvider.class);
        int[] windshieldIds = ids(awm, WindshieldHeatWidgetProvider.class);
        int[] heatingIds = ids(awm, HeatingTestWidgetProvider.class);
        int[] batteryRangeIds = ids(awm, BatteryRangeWidgetProvider.class);
        int[] realRangeIds = ids(awm, RealRangeWidgetProvider.class);
        int[] lockIds = ids(awm, LockWidgetProvider.class);

        boolean needDrive = driveRegenIds.length > 0 || driveVerticalIds.length > 0;
        boolean needRegen = driveRegenIds.length > 0 || regenVerticalIds.length > 0;
        boolean needFan = fanIds.length > 0 || fanVerticalIds.length > 0 || ventFanIds.length > 0;
        boolean needTemp = tempIds.length > 0 || tempVerticalIds.length > 0;
        boolean needVent = ventIds.length > 0 || ventVerticalIds.length > 0 || ventFanIds.length > 0;
        boolean needAc = ventFanIds.length > 0;
        boolean needHeating = heatingIds.length > 0;
        boolean needBatteryRange = batteryRangeIds.length > 0;

        Integer drive = needDrive ? WidgetsCommon.readDrive() : null;
        Integer regen = needRegen ? WidgetsCommon.readRegen() : null;
        Integer fan = needFan ? readFanCached() : null;
        Float temp = needTemp ? readTempCached() : null;
        float outsideTemp = outsideTempIds.length > 0 || outsideTempWideIds.length > 0
                ? Telemetry.outsideTemp(this) : Float.NaN;
        Integer vent = needVent ? WidgetsCommon.readVent() : null;
        Boolean recirc = needVent || recircIds.length > 0 ? WidgetsCommon.readRecirc() : null;
        Boolean ac = needAc ? WidgetsCommon.readAc() : null;
        Integer batteryPercent = needBatteryRange ? WidgetsCommon.readSoc() : null;
        if (batteryPercent != null && batteryPercent < 0) batteryPercent = null;
        float rangeKm = needBatteryRange ? WidgetsCommon.readRangeKm() : Float.NaN;

        Boolean rear = null;
        Boolean windshield = needHeating || windshieldIds.length > 0
                ? WidgetsCommon.readWindshieldHeat() : null;
        Boolean wheelHeat = needHeating || wheelIds.length > 0 ? WidgetsCommon.readWheelHeat() : null;
        Integer seatDriver = null;
        Integer seatPassenger = null;
        if (needHeating) {
            rear = WidgetsCommon.readRearDefrost();
            seatDriver = WidgetsCommon.readSeatHeat(1);
            seatPassenger = WidgetsCommon.readSeatHeat(4);
        }

        Boolean rearDefrost = rear;
        Integer driverHeat = seatDriver;
        Integer passengerHeat = seatPassenger;
        boolean any = false;
        any |= update(awm, recircIds, c -> WidgetsRenderer.recirc(c, recirc));
        any |= update(awm, wheelIds, c -> WidgetsRenderer.wheelHeat(c, wheelHeat));
        any |= update(awm, windshieldIds, c -> WidgetsRenderer.windshieldHeat(c, windshield));
        any |= update(awm, driveRegenIds, c -> WidgetsRenderer.driveRegen(c, drive, regen));
        any |= update(awm, driveVerticalIds, c -> WidgetsRenderer.driveVertical(c, drive));
        any |= update(awm, regenVerticalIds, c -> WidgetsRenderer.regenVertical(c, regen));
        any |= update(awm, fanIds, c -> WidgetsRenderer.fan(c, fan, R.layout.widget_fan_speed));
        any |= update(awm, fanVerticalIds, c -> WidgetsRenderer.fan(c, fan, R.layout.widget_fan_speed_vertical));
        any |= update(awm, tempIds, c -> WidgetsRenderer.temperature(c, temp, R.layout.widget_temperature));
        any |= update(awm, tempVerticalIds,
                c -> WidgetsRenderer.temperature(c, temp, R.layout.widget_temperature_vertical));
        any |= update(awm, outsideTempIds,
                c -> WidgetsRenderer.outsideTemperature(c, outsideTemp, R.layout.widget_outside_temp));
        any |= update(awm, outsideTempWideIds,
                c -> WidgetsRenderer.outsideTemperature(c, outsideTemp, R.layout.widget_outside_temp_wide));
        any |= update(awm, ventIds, c -> WidgetsRenderer.vent(c, vent, recirc));
        any |= update(awm, ventVerticalIds, c -> WidgetsRenderer.ventVertical(c, vent, recirc));
        any |= update(awm, ventFanIds,
                c -> WidgetsRenderer.ventFan(c, vent, recirc, fan, ac));
        any |= update(awm, heatingIds,
                c -> WidgetsRenderer.heating(c, wheelHeat, rearDefrost, windshield,
                        driverHeat, passengerHeat, WidgetsCommon.wheelHeatPending(this)));
        any |= SpeedWidgetProvider.updateAll(this, awm, Prefs.speedCache(this));
        float learnedKm = needBatteryRange || realRangeIds.length > 0
                ? RangeModel.estimateKm(this) : Float.NaN;
        float displayRange = Float.isNaN(learnedKm) ? rangeKm : learnedKm;
        Float energyKwh = batteryPercent == null ? null
                : batteryPercent * com.ex2tools.iv.car.CarProps.CAPACITY_KWH / 100f;
        Integer soc = batteryPercent;
        any |= update(awm, batteryRangeIds,
                c -> WidgetsRenderer.batteryRange(c, soc,
                        Float.isNaN(displayRange) ? null : displayRange, energyKwh));
        any |= update(awm, realRangeIds, c -> WidgetsRenderer.realRange(c, learnedKm));
        any |= update(awm, ids(awm, WipersWidgetProvider.class), WidgetsRenderer::wipers);
        any |= update(awm, ids(awm, PowerOffWidgetProvider.class), WidgetsRenderer::powerOff);
        any |= update(awm, lockIds, WidgetsRenderer::lock);
        if (ids(awm, ClockWidgetProvider.class).length > 0
                || ids(awm, ClockDateWidgetProvider.class).length > 0
                || ids(awm, LargeClockWidgetProvider.class).length > 0) {
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

    private static boolean hasSpeedWidget(Context c) {
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(c);
            return SpeedWidgetProvider.hasWidgets(c, awm);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasAnyWidget(Context c) {
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(c);
            for (Class<?> provider : PROVIDERS) {
                int[] ids = awm.getAppWidgetIds(new ComponentName(c, provider));
                if (ids != null && ids.length > 0) return true;
            }
            return SpeedWidgetProvider.hasWidgets(c, awm);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean update(AppWidgetManager awm, int[] ids,
                           java.util.function.Function<Context, RemoteViews> build) {
        if (ids.length == 0) return false;
        record RenderSize(float scale, float iconScale, boolean labels) {}
        java.util.Map<RenderSize, RemoteViews> rendered = new java.util.HashMap<>();
        for (int id : ids) {
            WidgetGrid.Slot slot = WidgetGrid.appWidgetSlot(this, awm, id);
            Context widget = WidgetIconSizes.appWidgetContext(this, id, slot.scale());
            RenderSize size = new RenderSize(slot.scale(), WidgetIconSizes.appWidgetIconScale(widget),
                    WidgetIconSizes.appWidgetLabels(widget));
            RemoteViews views = rendered.computeIfAbsent(size, key -> {
                RemoteViews content = build.apply(widget);
                WidgetGrid.scaleContent(this, content, key.scale());
                return content;
            });
            awm.updateAppWidget(id, slot.wrap(this, views));
        }
        return true;
    }

    private void scheduleRetry() {
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, RETRY_MS);
    }

    private void adjustClimate(boolean temperature, int direction) {
        if (!Prefs.fastWidgetResponse(this)) {
            if (temperature) WidgetsCommon.stepTemp(this, direction);
            else WidgetsCommon.stepFan(direction);
            return;
        }
        long previous = temperature ? lastTemperatureActionAt : lastFanActionAt;
        long delay = 130L - (android.os.SystemClock.elapsedRealtime() - previous);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        com.ex2tools.iv.climate.ClimateController climate =
                com.ex2tools.iv.climate.ClimateController.get();
        com.ex2tools.iv.vehicle.CommandDispatcher.Result result;
        if (temperature) {
            lastTemperatureActionAt = android.os.SystemClock.elapsedRealtime();
            result = climate.adjustTempInteractive(direction);
        } else {
            lastFanActionAt = android.os.SystemClock.elapsedRealtime();
            result = climate.adjustFanInteractive(direction);
        }
        if (result != null && result.ok()) VehicleState.get().requestDisplayRefresh();
    }

    private void showWipersUnavailable() {
        handler.post(() -> android.widget.Toast.makeText(this,
                R.string.widget_wipers_unavailable, android.widget.Toast.LENGTH_SHORT).show());
    }

    private void handleAction(String action) {
        switch (action) {
            case WidgetsCommon.WIPERS_TOGGLE:
                if (!com.ex2tools.iv.body.BodyController.get().toggleWiperServicePosition()) {
                    showWipersUnavailable();
                }
                break;
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
            case WidgetsCommon.FAN_MINUS:
                adjustClimate(false, WidgetsCommon.FAN_PLUS.equals(action) ? 1 : -1);
                break;
            case WidgetsCommon.TEMP_PLUS:
            case WidgetsCommon.TEMP_MINUS:
                adjustClimate(true, WidgetsCommon.TEMP_PLUS.equals(action) ? 1 : -1);
                break;
            case WidgetsCommon.CLIMATE_TARGET_PLUS:
                WidgetsCommon.stepClimateTarget(this, 1f);
                break;
            case WidgetsCommon.CLIMATE_TARGET_MINUS:
                WidgetsCommon.stepClimateTarget(this, -1f);
                break;
            case WidgetsCommon.CLIMATE_TOGGLE:
                WidgetsCommon.toggleClimate(this);
                break;
            case WidgetsCommon.VENT_FACE:
                if (Prefs.ventDirectionButtons(this)) WidgetsCommon.toggleVent(1);
                else WidgetsCommon.setVent(1);
                break;
            case WidgetsCommon.VENT_FLOOR:
                if (Prefs.ventDirectionButtons(this)) WidgetsCommon.toggleVent(2);
                else WidgetsCommon.setVent(2);
                break;
            case WidgetsCommon.VENT_FACE_FLOOR:
                WidgetsCommon.setVent(3);
                break;
            case WidgetsCommon.VENT_WINDSHIELD:
                if (Prefs.ventDirectionButtons(this)) WidgetsCommon.toggleVent(4);
                else WidgetsCommon.setVent(4);
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
            case WidgetsCommon.TRIP_RESET_DIALOG:
                com.ex2tools.iv.status.TripResetService.start(this);
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

    private static boolean speedChanged(Context c, float speed) {
        float cached = Prefs.speedCache(c);
        return Float.isNaN(cached) || Math.abs(cached - speed) >= 0.4f;
    }

    @Override
    public void onDestroy() {
        VehicleState.get().removeListener(stateListener);
        handler.removeCallbacks(speedTick);
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
