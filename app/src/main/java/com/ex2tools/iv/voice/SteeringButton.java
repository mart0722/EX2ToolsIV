package com.ex2tools.iv.voice;

import android.content.Context;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.climate.ClimateController;
import com.ex2tools.iv.drive.DriveController;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.widgets.WidgetsCommon;

/** Button codes are part of the installed keyserver patch's broadcast protocol. */
public enum SteeringButton {
    VOLUME_UP(1, R.string.steering_button_volume_up, Action.TEMP_UP),
    VOLUME_DOWN(2, R.string.steering_button_volume_down, Action.TEMP_DOWN),
    NEXT_TRACK(3, R.string.steering_button_next_track, Action.FAN_UP),
    PREVIOUS_TRACK(4, R.string.steering_button_previous_track, Action.FAN_DOWN);

    private final int code;
    public final int label;
    private final Action defaultAction;

    SteeringButton(int code, int label, Action defaultAction) {
        this.code = code;
        this.label = label;
        this.defaultAction = defaultAction;
    }

    static SteeringButton fromCode(int code) {
        for (SteeringButton button : values()) {
            if (button.code == code) return button;
        }
        return null;
    }

    public Action action(Context context) {
        String saved = Prefs.get(context).getString("steering_action_" + code, defaultAction.name());
        try {
            return Action.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return defaultAction;
        }
    }

    public void setAction(Context context, Action action) {
        Prefs.get(context).edit().putString("steering_action_" + code, action.name()).apply();
    }

    public enum Action {
        TEMP_UP(R.string.steering_action_temp_up),
        TEMP_DOWN(R.string.steering_action_temp_down),
        FAN_UP(R.string.steering_action_fan_up),
        FAN_DOWN(R.string.steering_action_fan_down),
        DRIVER_HEAT(R.string.steering_action_driver_heat),
        PASSENGER_HEAT(R.string.steering_action_passenger_heat),
        WHEEL_HEAT(R.string.widget_name_wheel_heat),
        WINDSHIELD_HEAT(R.string.widget_name_windshield_heat),
        REAR_HEAT(R.string.steering_action_rear_heat),
        FRONT_MAX_DEFROST(R.string.steering_action_front_max_defrost),
        DRIVE_MODE(R.string.steering_action_drive),
        REGEN_MODE(R.string.steering_action_regen);

        public final int label;

        Action(int label) {
            this.label = label;
        }

        CommandDispatcher.Result execute(Context context) {
            return switch (this) {
                case TEMP_UP -> ClimateController.get().adjustTempInteractive(1f);
                case TEMP_DOWN -> ClimateController.get().adjustTempInteractive(-1f);
                case FAN_UP -> ClimateController.get().adjustFanInteractive(1);
                case FAN_DOWN -> ClimateController.get().adjustFanInteractive(-1);
                case DRIVER_HEAT -> WidgetsCommon.cycleSeat(context, 1);
                case PASSENGER_HEAT -> WidgetsCommon.cycleSeat(context, 4);
                case WHEEL_HEAT -> ClimateController.get().toggleWheelHeat();
                case WINDSHIELD_HEAT -> ClimateController.get().toggleWindshieldHeat();
                case REAR_HEAT -> ClimateController.get().toggleRearDefrost();
                case FRONT_MAX_DEFROST -> ClimateController.get().toggleFrontMaxDefrost();
                case DRIVE_MODE -> DriveController.get().cycleDrive(context);
                case REGEN_MODE -> DriveController.get().cycleRegen(context);
            };
        }

        String feedback(Context context) {
            return switch (this) {
                case DRIVE_MODE -> DriveController.labelDrive(context, Prefs.drive(context));
                case REGEN_MODE -> DriveController.labelRegen(context, Prefs.regen(context));
                default -> context.getString(label);
            };
        }
    }
}
