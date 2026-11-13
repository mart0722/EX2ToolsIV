package com.ex2tools.iv.car;

import android.content.Context;

import com.ex2tools.iv.R;

public final class CarProps {

    public static final int DRIVE_MODE = 570491136;      // 0x22010100
    public static final int DRIVE_ECO = 570491137;       // 0x22010101
    public static final int DRIVE_COMFORT = 570491138;   // 0x22010102
    public static final int DRIVE_SPORT = 570491139;     // 0x22010103

    public static final int REGEN_MODE = 537003264;      // 0x20020500
    public static final int REGEN_WEAK = 537003265;      // 0x20020501
    public static final int REGEN_MEDIUM = 537003266;    // 0x20020502
    public static final int REGEN_STRONG = 537003267;    // 0x20020503

    public static final int GEAR = 289408001;            // 0x11400401
    public static final int PERF_VEHICLE_SPEED = 291504647;
    public static final int ESC_SPEED = 557884185;
    public static final int SOC = 557885165;
    public static final int RANGE = 289407752;
    public static final int AVG_CONSUMPTION = 557885176;
    public static final int AUX_12V = 557875206;
    public static final int HVAC_TEMP_SET = 557885148;
    public static final int HVAC_TEMP_SET_AOSP = 354419971;
    public static final int HVAC_TEMP_CURRENT = 358614274;
    public static final int HVAC_TEMP_AOSP = 358614275;
    public static final int HVAC_TEMP_ADJUST = 624955446;
    public static final int HVAC_FAN_SPEED = 356517120;
    public static final int HVAC_FAN_DIRECTION = 356517121;
    public static final int HVAC_AC_ON = 358613145;
    public static final int HVAC_RECIRC_ON = 557846566;
    public static final int HVAC_REAR_DEFROSTER_ON = 354419988;
    public static final int HVAC_FRONT_MAX_DEFROST_ON = 354419975;
    public static final int HVAC_WINDSHIELD_HEAT = 270535680;
    public static final int STEERING_WHEEL_HEAT = 289408269;
    public static final int SEAT_HEAT = 356517131;
    public static final int ODOMETER_VENDOR = 289407492;
    public static final int ODOMETER = 291504644;
    public static final float CAPACITY_KWH = 39.0f;
    public static final int AC_AMBIENT_TEMP = 557884279;
    public static final int ENV_OUTSIDE_TEMPERATURE = 291505923;
    public static final int DRIVE_MILEAGE = 658548345;

    public static final int SPEED_AUTO_LOCK = 557850645;   // 0x21402015, as in Sapo
    public static final int WINDOW_POSITION = 322964416;
    public static final int DAY_NIGHT_SETTING = 538247424;
    public static final int DAY_NIGHT_DAY = 538247425;
    public static final int DAY_NIGHT_NIGHT = 538247426;
    public static final int DAY_NIGHT_AUTO = 538247427;

    public static final int[] AREAS = new int[]{0, 1};
    public static final int[] CABIN_AREAS = new int[]{1, 4, 0, 7};
    public static final int[] HVAC_AREAS = new int[]{1, 4, 0};
    public static final int[] FAN_AREAS = new int[]{1, 49, 5, 0};
    public static final int[] FAN_DIRECTION_AREAS = new int[]{1, 4, 16, 64};
    public static final int[] AC_AREAS = new int[]{1, 5, 49, 0};
    public static final int[] SEAT_AREAS = new int[]{1, 4};
    public static final int[] WHEEL_HEAT_AREAS = new int[]{0, 1};
    public static final int GEAR_PARK = 4;

    private CarProps() {
    }

    public static String driveLabel(Context c, int value) {
        switch (value) {
            case DRIVE_ECO:
                return c.getString(R.string.drive_eco);
            case DRIVE_SPORT:
                return c.getString(R.string.drive_sport);
            case DRIVE_COMFORT:
            default:
                return c.getString(R.string.drive_comfort);
        }
    }

    public static String regenLabel(Context c, int value) {
        switch (value) {
            case REGEN_WEAK:
                return c.getString(R.string.regen_weak);
            case REGEN_STRONG:
                return c.getString(R.string.regen_strong);
            case REGEN_MEDIUM:
            default:
                return c.getString(R.string.regen_medium);
        }
    }
}

