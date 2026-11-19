package com.ex2tools.iv.widgets;

import android.content.Context;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.body.BodyController;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.climate.ClimateController;
import com.ex2tools.iv.drive.DriveController;
import com.ex2tools.iv.state.VehicleState;
import com.ex2tools.iv.vehicle.VhalGateway;

public final class WidgetsCommon {

    public static final String DRIVE_ECO = "com.ex2tools.iv.widget.DRIVE_ECO";
    public static final String DRIVE_COMFORT = "com.ex2tools.iv.widget.DRIVE_COMFORT";
    public static final String DRIVE_SPORT = "com.ex2tools.iv.widget.DRIVE_SPORT";
    public static final String DRIVE_NEXT = "com.ex2tools.iv.widget.DRIVE_NEXT";
    public static final String DRIVE_PREV = "com.ex2tools.iv.widget.DRIVE_PREV";
    public static final String REGEN_LOW = "com.ex2tools.iv.widget.REGEN_LOW";
    public static final String REGEN_MID = "com.ex2tools.iv.widget.REGEN_MID";
    public static final String REGEN_HIGH = "com.ex2tools.iv.widget.REGEN_HIGH";
    public static final String REGEN_NEXT = "com.ex2tools.iv.widget.REGEN_NEXT";
    public static final String REGEN_PREV = "com.ex2tools.iv.widget.REGEN_PREV";
    public static final String FAN_MINUS = "com.ex2tools.iv.widget.FAN_MINUS";
    public static final String FAN_PLUS = "com.ex2tools.iv.widget.FAN_PLUS";
    public static final String TEMP_MINUS = "com.ex2tools.iv.widget.TEMP_MINUS";
    public static final String TEMP_PLUS = "com.ex2tools.iv.widget.TEMP_PLUS";
    public static final String VENT_FACE = "com.ex2tools.iv.widget.VENT_FACE";
    public static final String VENT_FLOOR = "com.ex2tools.iv.widget.VENT_FLOOR";
    public static final String VENT_FACE_FLOOR = "com.ex2tools.iv.widget.VENT_FACE_FLOOR";
    public static final String VENT_WINDSHIELD = "com.ex2tools.iv.widget.VENT_WINDSHIELD";
    public static final String VENT_FLOOR_WINDSHIELD = "com.ex2tools.iv.widget.VENT_FLOOR_WINDSHIELD";
    public static final String VENT_NEXT = "com.ex2tools.iv.widget.VENT_NEXT";
    public static final String VENT_PREV = "com.ex2tools.iv.widget.VENT_PREV";
    public static final String RECIRC_TOGGLE = "com.ex2tools.iv.widget.RECIRC_TOGGLE";
    public static final String AC_TOGGLE = "com.ex2tools.iv.widget.AC_TOGGLE";
    public static final String REAR_DEFROST_TOGGLE = "com.ex2tools.iv.widget.REAR_DEFROST_TOGGLE";
    public static final String FRONT_MAX_DEFROST_TOGGLE = "com.ex2tools.iv.widget.FRONT_MAX_DEFROST_TOGGLE";
    public static final String WINDSHIELD_HEAT_TOGGLE = "com.ex2tools.iv.widget.WINDSHIELD_HEAT_TOGGLE";
    public static final String WHEEL_HEAT_TOGGLE = "com.ex2tools.iv.widget.WHEEL_HEAT_TOGGLE";
    public static final String TRIP_RESET_DIALOG = "com.ex2tools.iv.widget.TRIP_RESET_DIALOG";
    public static final String SEAT_DRIVER_CYCLE = "com.ex2tools.iv.widget.SEAT_DRIVER_CYCLE";
    public static final String SEAT_PASSENGER_CYCLE = "com.ex2tools.iv.widget.SEAT_PASSENGER_CYCLE";
    public static final String REFRESH_OPACITY = "com.ex2tools.iv.widget.REFRESH_OPACITY";
    public static final String WHEEL_MODE_ENTERED = "com.ex2tools.iv.widget.WHEEL_MODE_ENTERED";
    public static final String WHEEL_MODE_EXITED = "com.ex2tools.iv.widget.WHEEL_MODE_EXITED";

    public static final String SIZE_CLOCK = "clock";
    public static final String SIZE_DRIVE_REGEN = "drive_regen";
    public static final String SIZE_DRIVE_MODE = "drive_mode";
    public static final String SIZE_REGEN = "regen";
    public static final String SIZE_FAN = "fan";
    public static final String SIZE_TEMP = "temp";
    public static final String SIZE_VENT = "vent";
    public static final String SIZE_VENT_FAN = "vent_fan";
    public static final String SIZE_HEATING = "heating";
    public static final String SIZE_SPEED = "speed";
    public static final String SIZE_BATTERY_RANGE = "battery_range";
    public static final String SIZE_LOCK = "lock";
    public static final String SIZE_APPS = "apps";

    private static final int[] DRIVE_ORDER = {CarProps.DRIVE_ECO, CarProps.DRIVE_COMFORT, CarProps.DRIVE_SPORT};
    private static final int[] REGEN_ORDER = {CarProps.REGEN_WEAK, CarProps.REGEN_MEDIUM, CarProps.REGEN_STRONG};
    private static final int[] VENT_ORDER = {1, 2, 3, 4, 6};
    private static final int SEAT_HEAT_MAX_LEVEL = 3;

    public static final int WINDOW_FRONT_LEFT = 16;
    public static final int WINDOW_FRONT_RIGHT = 64;
    public static final int WINDOW_REAR_LEFT = 256;
    public static final int WINDOW_REAR_RIGHT = 1024;

    private WidgetsCommon() {
    }

    public static Integer readFan() {
        return readIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS);
    }

    public static Float readTemp(Context c) {
        Float pending = ClimateController.pendingTemp();
        if (pending != null) return pending;
        VehicleState state = state(c);
        int[] areas = state.areas(CarProps.HVAC_TEMP_SET, CarProps.HVAC_AREAS);
        Float value = validTemp(state.floatAny(CarProps.HVAC_TEMP_SET, areas));
        if (value == null) value = validTemp(state.floatAny(CarProps.HVAC_TEMP_SET_AOSP, areas));
        if (value == null) value = validTemp(state.floatAny(CarProps.HVAC_TEMP_ADJUST, areas));
        if (value == null) value = validTemp(state.floatAny(CarProps.HVAC_TEMP_AOSP, areas));
        return adjustTempDisplay(c, value);
    }

    /**
     * После пробуждения ECU держит 20, пока штатный HMI не вернёт сохранённую
     * уставку. В это окно показываем нашу последнюю известную, чтобы цифры
     * на виджете и в машине совпадали.
     */
    private static Float adjustTempDisplay(Context c, Float value) {
        if (value == null) return null;
        ClimateController.noteObservedTemp(value);
        int rounded = Math.round(value);
        int cached = Prefs.lastTempSet(c);
        if (rounded == ClimateController.DEFAULT_TEMP && ClimateController.shouldUseCachedTemp()
                && cached >= 17 && cached <= 32 && cached != ClimateController.DEFAULT_TEMP) {
            return (float) cached;
        }
        if (rounded != ClimateController.DEFAULT_TEMP && rounded >= 17 && rounded <= 32
                && rounded != cached) {
            Prefs.setLastTempSet(c, rounded);
        }
        return value;
    }

    public static float readSpeed(Context c) {
        VehicleState state = state(c);
        Float perf = state.floatValue(CarProps.PERF_VEHICLE_SPEED, 0);
        if (perf != null && perf >= 0f) return perf;
        Float esc = state.floatValue(CarProps.ESC_SPEED, 0);
        if (esc != null && esc >= 0f) return esc * 0.05625f;
        return Float.NaN;
    }

    public static Integer readVent() {
        return readIntAny(CarProps.HVAC_FAN_DIRECTION, CarProps.FAN_DIRECTION_AREAS);
    }

    public static Boolean readAc() {
        Boolean b = readBoolAny(CarProps.HVAC_AC_ON, CarProps.AC_AREAS);
        if (b != null) return b;
        Integer i = readIntAny(CarProps.HVAC_AC_ON, CarProps.AC_AREAS);
        return i == null ? null : i != 0;
    }

    public static Boolean readRecirc() {
        return readBoolAny(CarProps.HVAC_RECIRC_ON, CarProps.AC_AREAS);
    }

    public static Boolean readRearDefrost() {
        return readBoolAny(CarProps.HVAC_REAR_DEFROSTER_ON, new int[]{2});
    }

    public static Boolean readFrontMaxDefrost() {
        return readBoolAny(CarProps.HVAC_FRONT_MAX_DEFROST_ON, new int[]{1});
    }

    public static Boolean readWindshieldHeat() {
        return readBoolAny(CarProps.HVAC_WINDSHIELD_HEAT, new int[]{117});
    }

    public static Boolean readWheelHeat() {
        Integer v = readIntAny(CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS);
        return v == null ? null : v != 0;
    }

    public static Integer readWheelHeatLevel() {
        return readIntAny(CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS);
    }

    public static Integer readSeatHeat(int area) {
        // Важно: читаем именно эту зону. Общий readIntAny перебирает все зоны
        // свойства и возвращал уровень водителя для пассажирского сиденья.
        VehicleState state = state(null);
        Integer cached = state.intValue(CarProps.SEAT_HEAT, area);
        if (cached != null) return cached;
        int raw = VhalGateway.get().readInt(CarProps.SEAT_HEAT, area);
        return raw == Integer.MIN_VALUE ? null : raw;
    }

    public static Integer readDrive() {
        return readIntAny(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
    }

    public static Integer readRegen() {
        return readIntAny(CarProps.REGEN_MODE, CarProps.REGEN_AREAS);
    }

    public static Integer readSoc() {
        VehicleState state = state(null);
        Float f = state.floatAny(CarProps.SOC, state.areas(CarProps.SOC, new int[]{0}));
        if (f == null) return null;
        float v = f;
        if (v > 100f) v = v / 10f;
        if (v < 0f || v > 100f) return null;
        return Math.round(v);
    }

    public static float readRangeKm() {
        VehicleState state = state(null);
        Float value = state.floatValue(CarProps.RANGE, 0);
        if (value == null) return Float.NaN;
        return (value < 0f || value > 2000f) ? Float.NaN : value;
    }

    /** Пробег поездки: одометр минус точка сброса. */
    public static float readTripKm(Context c) {
        Float odo = readOdometer(c);
        if (odo == null) return Float.NaN;
        float start = Prefs.tripStartKm(c);
        if (start < 0f) {
            Prefs.setTripStartKm(c, odo);
            return 0f;
        }
        return Math.max(0f, odo - start);
    }

    public static Float readOdometer(Context c) {
        VehicleState state = state(c);
        Float value = state.floatAny(CarProps.ODOMETER_VENDOR, new int[]{0, 1});
        if (value == null || value < 1f) {
            value = state.floatAny(CarProps.ODOMETER, new int[]{0, 1});
        }
        return value;
    }

    public static boolean resetTrip(Context c) {
        Float odo = readOdometer(c);
        if (odo == null || odo < 1f) return false;
        Prefs.setTripStartKm(c, odo);
        return true;
    }

    /**
     * Подписывает слой состояния на всё, что показывают виджеты.
     * Дальше значения приходят событиями, опрос остаётся только страховкой.
     */
    public static void subscribeAll(Context c) {
        VehicleState state = VehicleState.get();
        if (!state.ensure(c)) return;
        subscribe(state, CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS);
        subscribe(state, CarProps.HVAC_FAN_DIRECTION, CarProps.FAN_DIRECTION_AREAS);
        subscribe(state, CarProps.HVAC_AC_ON, CarProps.AC_AREAS);
        subscribe(state, CarProps.HVAC_RECIRC_ON, CarProps.AC_AREAS);
        subscribe(state, CarProps.HVAC_REAR_DEFROSTER_ON, new int[]{2});
        subscribe(state, CarProps.HVAC_FRONT_MAX_DEFROST_ON, new int[]{1});
        subscribe(state, CarProps.HVAC_WINDSHIELD_HEAT, new int[]{117});
        subscribe(state, CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS);
        subscribe(state, CarProps.SEAT_HEAT, CarProps.SEAT_AREAS);
        subscribe(state, CarProps.HVAC_TEMP_ADJUST, CarProps.HVAC_AREAS);
        subscribe(state, CarProps.HVAC_TEMP_AOSP, CarProps.HVAC_AREAS);
        subscribe(state, CarProps.HVAC_TEMP_SET, CarProps.HVAC_AREAS);
        subscribe(state, CarProps.HVAC_TEMP_SET_AOSP, CarProps.HVAC_AREAS);
        subscribe(state, CarProps.PERF_VEHICLE_SPEED, new int[]{0});
        subscribe(state, CarProps.ESC_SPEED, new int[]{0});
        subscribe(state, CarProps.SOC, new int[]{0});
        subscribe(state, CarProps.RANGE, new int[]{0});
        subscribe(state, CarProps.ODOMETER_VENDOR, new int[]{0, 1});
        subscribe(state, CarProps.ODOMETER, new int[]{0, 1});
        subscribe(state, CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
        subscribe(state, CarProps.REGEN_MODE, CarProps.REGEN_AREAS);
        LogStore.add(c, "widget state: " + state.describe());
    }

    private static void subscribe(VehicleState state, int id, int[] fallback) {
        state.subscribe(id, state.areas(id, fallback));
    }

    public static void applyDrive(Context c, int value) {
        Prefs.setDrive(c, value);
        com.ex2tools.iv.vehicle.CommandDispatcher.Result r =
                DriveController.get().setDrive(value);
        LogStore.add(c, "widget drive=" + value + " -> " + r.status + " " + r.message);
    }

    public static void applyRegen(Context c, int value) {
        Prefs.setRegen(c, value);
        com.ex2tools.iv.vehicle.CommandDispatcher.Result r =
                DriveController.get().setRegen(value);
        LogStore.add(c, "widget regen=" + value + " -> " + r.status + " " + r.message);
    }

    public static void stepDrive(Context c, int direction) {
        Integer now = readDrive();
        int next = step(DRIVE_ORDER, now == null ? CarProps.DRIVE_COMFORT : now, direction);
        applyDrive(c, next);
    }

    public static void stepRegen(Context c, int direction) {
        Integer now = readRegen();
        int next = step(REGEN_ORDER, now == null ? CarProps.REGEN_MEDIUM : now, direction);
        applyRegen(c, next);
    }

    public static void stepFan(int direction) {
        adjustFan(direction);
    }

    public static void adjustFan(int delta) {
        ClimateController.get().adjustFan(delta);
    }

    public static void stepTemp(Context c, float direction) {
        ClimateController.get().adjustTemp(direction);
    }

    public static void setFan(int level) {
        ClimateController.get().setFan(level);
    }

    public static void setTemp(Context c, float value) {
        ClimateController.get().setTemp(value);
    }

    public static void adjustTemp(Context c, float delta) {
        ClimateController.get().adjustTemp(delta);
    }

    public static void setAc(Context c, boolean value) {
        ClimateController.get().setAc(value);
    }

    public static void setRecirc(Context c, boolean value) {
        ClimateController.get().setRecirc(value);
    }

    public static void setRearDefrost(Context c, boolean value) {
        ClimateController.get().setRearDefrost(value);
    }

    public static void setWindshieldHeat(Context c, boolean value) {
        ClimateController.get().setWindshieldHeat(value);
    }

    public static void setWheelHeat(Context c, boolean value) {
        ClimateController.get().setWheelHeat(value);
    }

    public static void setWheelHeatLevel(Context c, int level) {
        ClimateController.get().setWheelHeatLevel(level);
    }

    public static boolean setWindow(Context c, int position, int[] areas) {
        com.ex2tools.iv.vehicle.CommandDispatcher.Result result =
                BodyController.get().setWindow(position, areas);
        return result != null && result.ok();
    }

    public static void setSeatHeat(Context c, int area, int level) {
        ClimateController.get().setSeatHeat(area, level);
    }

    public static void stepVent(int direction) {
        ClimateController.get().stepVent(direction);
    }

    public static void toggleVent(int direction) {
        ClimateController.get().toggleVent(direction);
    }

    public static void setVent(int value) {
        ClimateController.get().setVent(value);
    }

    public static void toggleAc(Context c) {
        ClimateController.get().toggleAc();
    }

    public static void toggleRecirc(Context c) {
        ClimateController.get().toggleRecirc();
    }

    public static void toggleRearDefrost(Context c) {
        ClimateController.get().toggleRearDefrost();
    }

    public static void toggleFrontMaxDefrost(Context c) {
        ClimateController.get().toggleFrontMaxDefrost();
    }

    public static void toggleWindshieldHeat(Context c) {
        ClimateController.get().toggleWindshieldHeat();
    }

    public static void toggleWheelHeat(Context c) {
        Boolean on = readWheelHeat();
        if (Prefs.wheelHeatPending(c) || Boolean.TRUE.equals(on)) {
            Prefs.setWheelHeatPending(c, false);
            Prefs.setWheelHeatRetryAt(c, 0L);
            setWheelHeat(c, false);
            LogStore.add(c, "wheel heat: off requested");
        } else {
            setWheelHeat(c, true);
            Prefs.setWheelHeatPending(c, true);
            Prefs.setWheelHeatPendingAt(c, System.currentTimeMillis());
            Prefs.setWheelHeatRetryAt(c, 0L);
            LogStore.add(c, "wheel heat: on requested, waiting for D");
        }
    }

    public static boolean wheelHeatPending(Context c) {
        return Prefs.wheelHeatPending(c);
    }

    /**
     * Обогрев руля машина включает только в D. Запрос из виджета запоминается и
     * повторяется при переходе в D; когда чтение подтвердило включение — снимаем.
     */
    public static void checkWheelPending(Context c) {
        if (!Prefs.wheelHeatPending(c)) return;
        long now = System.currentTimeMillis();
        long at = Prefs.wheelHeatPendingAt(c);
        if (at <= 0L) {
            Prefs.setWheelHeatPendingAt(c, now);
            return;
        }
        if (now - at > 10 * 60 * 1000L) {
            Prefs.setWheelHeatPending(c, false);
            Prefs.setWheelHeatRetryAt(c, 0L);
            LogStore.add(c, "wheel heat: pending expired");
            return;
        }
        Integer level = readWheelHeatLevel();
        if (level != null && level > 0) {
            Prefs.setWheelHeatPending(c, false);
            Prefs.setWheelHeatRetryAt(c, 0L);
            LogStore.add(c, "wheel heat: confirmed by car");
            return;
        }
        if (Telemetry.gear(c) != CarProps.GEAR_DRIVE) return;
        long retryAt = Prefs.wheelHeatRetryAt(c);
        if (now - retryAt < 5_000L) return;
        Prefs.setWheelHeatRetryAt(c, now);
        setWheelHeat(c, true);
        LogStore.add(c, "wheel heat: retry in D");
    }

    public static void cycleSeat(Context c, int area) {
        ClimateController.get().cycleSeat(area);
    }

    public static int ventLabel(int mode) {
        switch (mode) {
            case 1:
                return R.string.wg_vent_face;
            case 2:
                return R.string.wg_vent_floor;
            case 3:
                return R.string.wg_vent_face_floor;
            case 4:
                return R.string.wg_vent_windshield;
            case 6:
                return R.string.wg_vent_floor_windshield;
            default:
                return R.string.wg_vent_face;
        }
    }

    public static int bgDrawableResForPct(Context c) {
        int pct = Math.round(Prefs.widgetBgOpacity(c) / 20f) * 20;
        switch (pct) {
            case 0:
                return R.drawable.bg_wg_card_000;
            case 20:
                return R.drawable.bg_wg_card_020;
            case 40:
                return R.drawable.bg_wg_card_040;
            case 60:
                return R.drawable.bg_wg_card_060;
            case 80:
                return R.drawable.bg_wg_card_080;
            default:
                return R.drawable.bg_wg_card_100;
        }
    }

    public static int ventIcon(int mode) {
        switch (mode) {
            case 2:
                return R.drawable.wg_ic_vent_floor;
            case 3:
                return R.drawable.wg_ic_vent_face_floor;
            case 4:
                return R.drawable.wg_ic_vent_windshield;
            case 6:
                return R.drawable.wg_ic_vent_floor_windshield;
            case 1:
            default:
                return R.drawable.wg_ic_vent_face;
        }
    }

    private static int step(int[] order, int current, int direction) {
        int index = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                index = i;
                break;
            }
        }
        if (index < 0) index = order.length / 2;
        return order[Math.max(0, Math.min(order.length - 1, index + direction))];
    }

    private static Integer readIntAny(int property, int[] fallback) {
        VehicleState state = state(null);
        return state.intAny(property, state.areas(property, fallback));
    }

    private static Boolean readBoolAny(int property, int[] fallback) {
        VehicleState state = state(null);
        return state.boolAny(property, state.areas(property, fallback));
    }

    private static VehicleState state(Context c) {
        VehicleState state = VehicleState.get();
        if (!state.isReady()) {
            Context context = c != null ? c : VhalGateway.get().appContext();
            if (context != null) state.ensure(context);
        }
        return state;
    }

    private static Float validTemp(Float raw) {
        if (raw == null) return null;
        float value = raw;
        if (value > 60f && value <= 400f) value = value / 10f;
        return (value >= 5f && value <= 40f) ? value : null;
    }
}
