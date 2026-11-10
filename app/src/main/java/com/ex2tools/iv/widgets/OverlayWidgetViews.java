package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;

/**
 * Normal views for the floating widgets. They intentionally mirror the
 * AppWidget renderer, but are inflated as regular Android views so the
 * overlay does not depend on RemoteViews/AppWidgetHost behaviour.
 */
final class OverlayWidgetViews {

    private static final int[] FAN_DOTS = {
            R.id.dot_fan_1, R.id.dot_fan_2, R.id.dot_fan_3, R.id.dot_fan_4,
            R.id.dot_fan_5, R.id.dot_fan_6, R.id.dot_fan_7
    };

    private OverlayWidgetViews() {
    }

    static View create(Context c, String key) {
        View root = LayoutInflater.from(themeContext(c)).inflate(layout(key), null);
        bindClicks(c, root, key);
        return root;
    }

    static void update(Context context, View root, String key) {
        Context c = themeContext(context);
        View bg = root.findViewById(R.id.widget_bg_layer);
        if (bg != null) bg.setBackground(OverlayBackground.create(c, key));

        switch (key) {
            case WidgetsCommon.SIZE_DRIVE_REGEN:
                updateDriveRegen(c, root);
                break;
            case WidgetsCommon.SIZE_DRIVE_MODE:
                updateDrive(c, root);
                break;
            case WidgetsCommon.SIZE_REGEN:
                updateRegen(c, root);
                break;
            case WidgetsCommon.SIZE_FAN:
                updateFan(c, root);
                break;
            case WidgetsCommon.SIZE_TEMP:
                updateTemp(c, root);
                break;
            case WidgetsCommon.SIZE_VENT:
                updateVent(c, root);
                break;
            case WidgetsCommon.SIZE_VENT_FAN:
                updateVentFan(c, root);
                break;
            case WidgetsCommon.SIZE_HEATING:
                updateHeating(c, root);
                break;
            default:
                break;
        }
    }

    static Context themeContext(Context c) {
        try {
            int mode = Prefs.themeMode(c);
            Configuration cfg = new Configuration(c.getResources().getConfiguration());
            int night;
            if (mode == 1) {
                night = Configuration.UI_MODE_NIGHT_NO;
            } else if (mode == 2) {
                night = Configuration.UI_MODE_NIGHT_YES;
            } else {
                night = cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            }
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
            return c.createConfigurationContext(cfg);
        } catch (Throwable ignored) {
            return c;
        }
    }

    private static int layout(String key) {
        switch (key) {
            case WidgetsCommon.SIZE_DRIVE_REGEN:
                return R.layout.widget_drive_regen;
            case WidgetsCommon.SIZE_DRIVE_MODE:
                return R.layout.widget_drive_mode_vertical;
            case WidgetsCommon.SIZE_REGEN:
                return R.layout.widget_regen_vertical;
            case WidgetsCommon.SIZE_FAN:
                return R.layout.widget_fan_speed;
            case WidgetsCommon.SIZE_TEMP:
                return R.layout.widget_temperature;
            case WidgetsCommon.SIZE_VENT:
                return R.layout.widget_hvac_direction;
            case WidgetsCommon.SIZE_VENT_FAN:
                return R.layout.widget_hvac_direction_fan;
            case WidgetsCommon.SIZE_HEATING:
                return R.layout.widget_heating_test;
            default:
                return R.layout.widget_drive_regen;
        }
    }

    private static void bindClicks(Context c, View root, String key) {
        switch (key) {
            case WidgetsCommon.SIZE_DRIVE_REGEN:
                click(c, root, R.id.chip_drive_eco, WidgetsCommon.DRIVE_ECO);
                click(c, root, R.id.chip_drive_comfort, WidgetsCommon.DRIVE_COMFORT);
                click(c, root, R.id.chip_drive_sport, WidgetsCommon.DRIVE_SPORT);
                click(c, root, R.id.chip_regen_low, WidgetsCommon.REGEN_LOW);
                click(c, root, R.id.chip_regen_mid, WidgetsCommon.REGEN_MID);
                click(c, root, R.id.chip_regen_high, WidgetsCommon.REGEN_HIGH);
                break;
            case WidgetsCommon.SIZE_DRIVE_MODE:
                click(c, root, R.id.btn_drive_next, WidgetsCommon.DRIVE_NEXT);
                click(c, root, R.id.btn_drive_prev, WidgetsCommon.DRIVE_PREV);
                break;
            case WidgetsCommon.SIZE_REGEN:
                click(c, root, R.id.btn_regen_next, WidgetsCommon.REGEN_NEXT);
                click(c, root, R.id.btn_regen_prev, WidgetsCommon.REGEN_PREV);
                break;
            case WidgetsCommon.SIZE_FAN:
                click(c, root, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
                click(c, root, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
                break;
            case WidgetsCommon.SIZE_TEMP:
                click(c, root, R.id.btn_temp_minus, WidgetsCommon.TEMP_MINUS);
                click(c, root, R.id.btn_temp_plus, WidgetsCommon.TEMP_PLUS);
                break;
            case WidgetsCommon.SIZE_VENT:
                click(c, root, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
                click(c, root, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
                click(c, root, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
                click(c, root, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
                click(c, root, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
                click(c, root, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
                break;
            case WidgetsCommon.SIZE_VENT_FAN:
                click(c, root, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
                click(c, root, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
                click(c, root, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
                click(c, root, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
                click(c, root, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
                click(c, root, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
                click(c, root, R.id.chip_fan_ac, WidgetsCommon.AC_TOGGLE);
                click(c, root, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
                click(c, root, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
                break;
            case WidgetsCommon.SIZE_HEATING:
                click(c, root, R.id.chip_wheel_heat, WidgetsCommon.WHEEL_HEAT_TOGGLE);
                click(c, root, R.id.chip_front_rear_defrost, WidgetsCommon.REAR_DEFROST_TOGGLE);
                click(c, root, R.id.chip_front_max_defrost, WidgetsCommon.FRONT_MAX_DEFROST_TOGGLE);
                click(c, root, R.id.chip_windshield_heat, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
                click(c, root, R.id.chip_seat_heat_driver, WidgetsCommon.SEAT_DRIVER_CYCLE);
                click(c, root, R.id.chip_seat_heat_passenger, WidgetsCommon.SEAT_PASSENGER_CYCLE);
                break;
            default:
                break;
        }
    }

    private static void updateDriveRegen(Context c, View root) {
        int drive = Telemetry.driveNow(c);
        int regen = Telemetry.regenNow(c);
        text(root, R.id.txt_drive_header, c.getString(R.string.k_widget_lbl_drive_header));
        text(root, R.id.txt_regen_header, c.getString(R.string.k_widget_lbl_regen_header));
        text(root, R.id.txt_drive_eco, c.getString(R.string.k_widget_lbl_drive_eco));
        text(root, R.id.txt_drive_comfort, c.getString(R.string.k_widget_lbl_drive_comfort));
        text(root, R.id.txt_drive_sport, c.getString(R.string.k_widget_lbl_drive_sport));
        text(root, R.id.txt_regen_low, c.getString(R.string.k_widget_lbl_regen_low));
        text(root, R.id.txt_regen_mid, c.getString(R.string.k_widget_lbl_regen_mid));
        text(root, R.id.txt_regen_high, c.getString(R.string.k_widget_lbl_regen_high));
        chip(c, root, R.id.chip_drive_eco, R.id.txt_drive_eco, drive == CarProps.DRIVE_ECO);
        chip(c, root, R.id.chip_drive_comfort, R.id.txt_drive_comfort, drive == CarProps.DRIVE_COMFORT);
        chip(c, root, R.id.chip_drive_sport, R.id.txt_drive_sport, drive == CarProps.DRIVE_SPORT);
        chip(c, root, R.id.chip_regen_low, R.id.txt_regen_low, regen == CarProps.REGEN_WEAK);
        chip(c, root, R.id.chip_regen_mid, R.id.txt_regen_mid, regen == CarProps.REGEN_MEDIUM);
        chip(c, root, R.id.chip_regen_high, R.id.txt_regen_high, regen == CarProps.REGEN_STRONG);
    }

    private static void updateDrive(Context c, View root) {
        int drive = Telemetry.driveNow(c);
        int icon;
        int label;
        if (drive == CarProps.DRIVE_ECO) {
            icon = R.drawable.ic_setting_drive_eco;
            label = R.string.k_widget_lbl_drive_eco;
        } else if (drive == CarProps.DRIVE_SPORT) {
            icon = R.drawable.ic_setting_drive_sport;
            label = R.string.k_widget_lbl_drive_sport;
        } else {
            icon = R.drawable.ic_setting_drive_comfort;
            label = R.string.k_widget_lbl_drive_comfort;
        }
        image(root, R.id.img_drive_mode, icon);
        text(root, R.id.tv_drive_mode_label, c.getString(label));
        text(root, R.id.tv_drive_mode_footer, c.getString(R.string.k_qa_driving_label));
    }

    private static void updateRegen(Context c, View root) {
        int regen = Telemetry.regenNow(c);
        int icon;
        int label;
        if (regen == CarProps.REGEN_WEAK) {
            icon = R.drawable.ic_widget_regen_weak;
            label = R.string.k_widget_lbl_regen_low;
        } else if (regen == CarProps.REGEN_STRONG) {
            icon = R.drawable.ic_widget_regen_strong;
            label = R.string.k_widget_lbl_regen_high;
        } else {
            icon = R.drawable.ic_widget_regen_mid;
            label = R.string.k_widget_lbl_regen_mid;
        }
        image(root, R.id.img_regen_level, icon);
        text(root, R.id.tv_regen_level_label, c.getString(label));
        text(root, R.id.tv_regen_level_footer, c.getString(R.string.k_qa_regen_label));
    }

    private static void updateFan(Context c, View root) {
        Integer fan = WidgetsCommon.readFan();
        text(root, R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        text(root, R.id.tv_fan_label, c.getString(R.string.k_widget_lbl_fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            View dot = root.findViewById(FAN_DOTS[i]);
            if (dot != null) {
                dot.setBackgroundResource(fan != null && i < fan
                        ? R.drawable.widget_dot_on : R.drawable.widget_dot_off);
            }
        }
    }

    private static void updateTemp(Context c, View root) {
        Float temp = WidgetsCommon.readTemp(c);
        text(root, R.id.tv_temp_value, temp == null ? "—" : String.valueOf(Math.round(temp)));
        text(root, R.id.tv_temp_unit, "\u00B0C");
        text(root, R.id.tv_temp_label, c.getString(R.string.k_widget_lbl_temp));
    }

    private static void updateVent(Context c, View root) {
        Integer vent = WidgetsCommon.readVent();
        Boolean recirc = WidgetsCommon.readRecirc();
        int mode = vent == null ? 1 : vent;
        text(root, R.id.txt_vent_header, c.getString(R.string.k_widget_lbl_vent_header));
        text(root, R.id.txt_vent_face, c.getString(R.string.k_widget_lbl_vent_face));
        text(root, R.id.txt_vent_floor, c.getString(R.string.k_widget_lbl_vent_floor));
        text(root, R.id.txt_vent_face_floor, c.getString(R.string.k_widget_lbl_vent_face_floor));
        text(root, R.id.txt_vent_windshield, c.getString(R.string.k_widget_lbl_vent_windshield));
        text(root, R.id.txt_vent_floor_windshield, c.getString(R.string.k_widget_lbl_vent_floor_windshield));
        text(root, R.id.txt_recirc, c.getString(R.string.k_widget_lbl_recirc));
        chip(c, root, R.id.chip_vent_face, R.id.txt_vent_face, mode == 1);
        chip(c, root, R.id.chip_vent_floor, R.id.txt_vent_floor, mode == 2);
        chip(c, root, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        chip(c, root, R.id.chip_vent_windshield, R.id.txt_vent_windshield, mode == 4);
        chip(c, root, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        chip(c, root, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
    }

    private static void updateVentFan(Context c, View root) {
        Integer vent = WidgetsCommon.readVent();
        Boolean recirc = WidgetsCommon.readRecirc();
        Integer fan = WidgetsCommon.readFan();
        Boolean ac = WidgetsCommon.readAc();
        int mode = vent == null ? 1 : vent;
        chip(c, root, R.id.chip_vent_face, R.id.txt_vent_face, mode == 1);
        chip(c, root, R.id.chip_vent_floor, R.id.txt_vent_floor, mode == 2);
        chip(c, root, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        chip(c, root, R.id.chip_vent_windshield, R.id.txt_vent_windshield, mode == 4);
        chip(c, root, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        chip(c, root, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        text(root, R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            View dot = root.findViewById(FAN_DOTS[i]);
            if (dot != null) {
                dot.setBackgroundResource(fan != null && i < fan
                        ? R.drawable.widget_dot_on : R.drawable.widget_dot_off);
            }
        }
        View acChip = root.findViewById(R.id.chip_fan_ac);
        if (acChip != null) {
            acChip.setBackgroundResource(Boolean.TRUE.equals(ac)
                    ? R.drawable.widget_chip_active : R.drawable.widget_chip_ac_off);
        }
        View fanValue = root.findViewById(R.id.tv_fan_value);
        if (fanValue instanceof TextView) {
            ((TextView) fanValue).setTextColor(c.getColor(Boolean.TRUE.equals(ac)
                    ? R.color.cx_on_accent : R.color.cx_text_primary));
        }
    }

    private static void updateHeating(Context c, View root) {
        Boolean wheel = WidgetsCommon.readWheelHeat();
        Boolean rear = WidgetsCommon.readRearDefrost();
        Boolean front = WidgetsCommon.readFrontMaxDefrost();
        Boolean windshield = WidgetsCommon.readWindshieldHeat();
        Integer seatDriver = WidgetsCommon.readSeatHeat(1);
        Integer seatPassenger = WidgetsCommon.readSeatHeat(4);

        text(root, R.id.txt_heat_header, c.getString(R.string.k_widget_lbl_heat_header));
        toggle(c, root, R.id.chip_wheel_heat, R.id.txt_wheel_heat,
                Boolean.TRUE.equals(wheel), c.getString(R.string.k_widget_lbl_heat_wheel));
        toggle(c, root, R.id.chip_front_rear_defrost, R.id.txt_front_rear_defrost,
                Boolean.TRUE.equals(rear), c.getString(R.string.k_widget_lbl_rear_defrost));
        toggle(c, root, R.id.chip_front_max_defrost, R.id.txt_front_max_defrost,
                Boolean.TRUE.equals(front), c.getString(R.string.k_widget_lbl_heat_front_max));
        toggle(c, root, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(windshield), c.getString(R.string.k_widget_lbl_heat_windshield));
        seat(c, root, R.id.chip_seat_heat_driver, R.id.txt_seat_heat_driver,
                seatDriver, c.getString(R.string.k_widget_lbl_heat_seat_driver));
        seat(c, root, R.id.chip_seat_heat_passenger, R.id.txt_seat_heat_passenger,
                seatPassenger, c.getString(R.string.k_widget_lbl_heat_seat_passenger));
    }

    private static void text(View root, int id, String value) {
        View v = root.findViewById(id);
        if (v instanceof TextView) ((TextView) v).setText(value);
    }

    private static void image(View root, int id, int res) {
        View v = root.findViewById(id);
        if (v instanceof ImageView) ((ImageView) v).setImageResource(res);
    }

    private static void chip(Context c, View root, int chipId, int textId, boolean active) {
        View chip = root.findViewById(chipId);
        if (chip != null) {
            chip.setBackgroundResource(active
                    ? R.drawable.widget_chip_active : R.drawable.widget_chip_idle);
        }
        View text = root.findViewById(textId);
        if (text instanceof TextView) {
            ((TextView) text).setTextColor(c.getColor(active
                    ? R.color.cx_on_accent : R.color.cx_text_primary));
        }
    }

    private static void toggle(Context c, View root, int chipId, int textId,
                               boolean active, String label) {
        chip(c, root, chipId, textId, active);
        text(root, textId, label);
    }

    private static void seat(Context c, View root, int chipId, int textId,
                             Integer level, String label) {
        boolean active = level != null && level > 0;
        chip(c, root, chipId, textId, active);
        String value = level == null ? "—" : (level >= 4
                ? c.getString(R.string.k_widget_lbl_heat_auto) : String.valueOf(level));
        text(root, textId, label + " " + value);
    }

    private static void click(Context c, View root, int id, String action) {
        View v = root.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(x -> {
            try {
                ContextCompat.startForegroundService(c,
                        new Intent(c, WidgetUpdateService.class).setAction(action));
            } catch (Throwable t) {
                LogStore.add(c, "overlay click failed " + action + ": " + t);
            }
        });
    }
}
