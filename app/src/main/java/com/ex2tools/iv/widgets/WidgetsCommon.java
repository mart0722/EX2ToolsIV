package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.service.DriveRegenService;

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
        float v = Telemetry.hvacTarget(c);
        return Float.isNaN(v) ? null : v;
    }

    public static float readSpeed(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        float perf = cc.readFloat(CarProps.PERF_VEHICLE_SPEED, 0);
        if (!Float.isNaN(perf) && perf >= 0f) return perf;
        float esc = cc.readFloat(CarProps.ESC_SPEED, 0);
        if (!Float.isNaN(esc) && esc >= 0f) return esc * 0.05625f;
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
        return readBoolAny(CarProps.HVAC_RECIRC_ON, new int[]{117});
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
        return readIntAny(CarProps.SEAT_HEAT, new int[]{area});
    }

    public static void applyDrive(Context c, int value) {
        Prefs.setDrive(c, value);
        DriveRegenService.start(c, true);
    }

    public static void applyRegen(Context c, int value) {
        Prefs.setRegen(c, value);
        DriveRegenService.start(c, true);
    }

    public static void stepDrive(Context c, int direction) {
        int current = Telemetry.driveNow(c);
        int next = step(DRIVE_ORDER, current, direction);
        applyDrive(c, next);
    }

    public static void stepRegen(Context c, int direction) {
        int current = Telemetry.regenNow(c);
        int next = step(REGEN_ORDER, current, direction);
        applyRegen(c, next);
    }

    public static void stepFan(int direction) {
        adjustFan(direction);
    }

    public static void adjustFan(int delta) {
        Integer current = readFan();
        int value = current == null ? 3 : current;
        value = Math.max(0, Math.min(7, value + delta));
        writeIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS, value);
    }

    public static void stepTemp(Context c, float direction) {
        Float current = readTemp(c);
        float value = current == null ? 22f : Math.round(current);
        value = Math.max(16f, Math.min(30f, Math.round(value + direction)));
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        cc.writeFloat(CarProps.HVAC_TEMP_ADJUST, 1, value);
        cc.writeFloat(CarProps.HVAC_TEMP_ADJUST, 4, value);
    }

    public static void setFan(int level) {
        writeIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS,
                Math.max(0, Math.min(7, level)));
    }

    public static void setTemp(Context c, float value) {
        float v = Math.max(16f, Math.min(30f, Math.round(value)));
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        cc.writeFloat(CarProps.HVAC_TEMP_ADJUST, 1, v);
        cc.writeFloat(CarProps.HVAC_TEMP_ADJUST, 4, v);
    }

    public static void adjustTemp(Context c, float delta) {
        Float current = readTemp(c);
        float base = current == null ? 22f : Math.round(current);
        setTemp(c, base + delta);
    }

    public static void setAc(Context c, boolean value) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        for (int area : CarProps.AC_AREAS) {
            if (cc.writeBool(CarProps.HVAC_AC_ON, area, value)) return;
        }
        for (int area : CarProps.AC_AREAS) {
            if (cc.writeInt(CarProps.HVAC_AC_ON, area, value ? 1 : 0)) return;
        }
    }

    public static void setRecirc(Context c, boolean value) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_RECIRC_ON, 117, value);
    }

    public static void setRearDefrost(Context c, boolean value) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_REAR_DEFROSTER_ON, 2, value);
    }

    public static void setWindshieldHeat(Context c, boolean value) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_WINDSHIELD_HEAT, 117, value);
    }

    public static void setWheelHeat(Context c, boolean value) {
        setWheelHeatLevel(c, value ? 1 : 0);
    }

    public static void setWheelHeatLevel(Context c, int level) {
        writeIntAny(CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS,
                Math.max(0, Math.min(3, level)));
    }

    public static boolean setWindow(Context c, int position, int[] areas) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return false;
        int value = Math.max(0, Math.min(88, position));
        boolean any = false;
        for (int area : areas) {
            if (cc.writeInt(CarProps.WINDOW_POSITION, area, value)) any = true;
        }
        return any;
    }

    public static void setSeatHeat(Context c, int area, int level) {
        writeIntAny(CarProps.SEAT_HEAT, new int[]{area},
                Math.max(0, Math.min(SEAT_HEAT_MAX_LEVEL, level)));
    }

    public static void stepVent(int direction) {
        Integer current = readVent();
        int next = step(VENT_ORDER, current == null ? 1 : current, direction);
        writeIntAny(CarProps.HVAC_FAN_DIRECTION, new int[]{1, 4}, next);
    }

    public static void setVent(int value) {
        writeIntAny(CarProps.HVAC_FAN_DIRECTION, new int[]{1, 4}, value);
    }

    public static void toggleAc(Context c) {
        Boolean current = readAc();
        boolean value = current == null || !current;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        for (int area : CarProps.AC_AREAS) {
            if (cc.writeBool(CarProps.HVAC_AC_ON, area, value)) return;
        }
        for (int area : CarProps.AC_AREAS) {
            if (cc.writeInt(CarProps.HVAC_AC_ON, area, value ? 1 : 0)) return;
        }
    }

    public static void toggleRecirc(Context c) {
        Boolean current = readRecirc();
        boolean value = current == null || !current;
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_RECIRC_ON, 117, value);
    }

    public static void toggleRearDefrost(Context c) {
        Boolean current = readRearDefrost();
        boolean value = current == null || !current;
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_REAR_DEFROSTER_ON, 2, value);
    }

    public static void toggleFrontMaxDefrost(Context c) {
        Boolean current = readFrontMaxDefrost();
        boolean value = current == null || !current;
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_FRONT_MAX_DEFROST_ON, 1, value);
    }

    public static void toggleWindshieldHeat(Context c) {
        Boolean current = readWindshieldHeat();
        boolean value = current == null || !current;
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_WINDSHIELD_HEAT, 117, value);
    }

    public static void toggleWheelHeat(Context c) {
        Boolean current = readWheelHeat();
        boolean value = current == null || !current;
        writeIntAny(CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS, value ? 1 : 0);
    }

    public static void cycleSeat(Context c, int area) {
        Integer current = readSeatHeat(area);
        int value = current == null ? 0 : Math.max(0, Math.min(SEAT_HEAT_MAX_LEVEL, current));
        value = (value + 1) % (SEAT_HEAT_MAX_LEVEL + 1);
        writeIntAny(CarProps.SEAT_HEAT, new int[]{area}, value);
    }

    public static int ventLabel(int mode) {
        switch (mode) {
            case 1:
                return R.string.k_widget_lbl_vent_face;
            case 2:
                return R.string.k_widget_lbl_vent_floor;
            case 3:
                return R.string.k_widget_lbl_vent_face_floor;
            case 4:
                return R.string.k_widget_lbl_vent_windshield;
            case 6:
                return R.string.k_widget_lbl_vent_floor_windshield;
            default:
                return R.string.k_widget_lbl_vent_face;
        }
    }

    public static int bgDrawableResForPct(Context c) {
        int pct = Math.round(Prefs.widgetBgOpacity(c) / 20f) * 20;
        switch (pct) {
            case 0:
                return R.drawable.widget_card_bg_000;
            case 20:
                return R.drawable.widget_card_bg_020;
            case 40:
                return R.drawable.widget_card_bg_040;
            case 60:
                return R.drawable.widget_card_bg_060;
            case 80:
                return R.drawable.widget_card_bg_080;
            default:
                return R.drawable.widget_card_bg_100;
        }
    }

    public static int ventIcon(int mode) {
        switch (mode) {
            case 2:
                return R.drawable.ic_climate_vent_floor;
            case 3:
                return R.drawable.ic_climate_vent_face_floor;
            case 4:
                return R.drawable.ic_climate_vent_windshield;
            case 6:
                return R.drawable.ic_climate_vent_floor_windshield;
            case 1:
            default:
                return R.drawable.ic_climate_vent_face;
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

    private static Integer readIntAny(int property, int[] areas) {
        CarConnection cc = CarConnection.get();
        for (int area : areas) {
            int v = cc.readInt(property, area);
            if (v != Integer.MIN_VALUE) return v;
        }
        return null;
    }

    private static Boolean readBoolAny(int property, int[] areas) {
        CarConnection cc = CarConnection.get();
        for (int area : areas) {
            Boolean v = cc.readBool(property, area);
            if (v != null) return v;
        }
        return null;
    }

    private static boolean writeIntAny(int property, int[] areas, int value) {
        CarConnection cc = CarConnection.get();
        for (int area : areas) {
            if (cc.writeInt(property, area, value)) return true;
        }
        return false;
    }
}
