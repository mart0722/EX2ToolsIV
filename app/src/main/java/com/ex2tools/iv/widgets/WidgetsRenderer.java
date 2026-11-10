package com.ex2tools.iv.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarProps;

public final class WidgetsRenderer {

    private static final int[] FAN_DOTS = {
            R.id.dot_fan_1, R.id.dot_fan_2, R.id.dot_fan_3, R.id.dot_fan_4,
            R.id.dot_fan_5, R.id.dot_fan_6, R.id.dot_fan_7
    };

    private WidgetsRenderer() {
    }

    public static RemoteViews driveRegen(Context c, Integer drive, Integer regen) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_drive_regen);
        bg(v, c);
        v.setTextViewText(R.id.txt_drive_header, c.getString(R.string.k_widget_lbl_drive_header));
        v.setTextViewText(R.id.txt_regen_header, c.getString(R.string.k_widget_lbl_regen_header));
        v.setTextViewText(R.id.txt_drive_eco, c.getString(R.string.k_widget_lbl_drive_eco));
        v.setTextViewText(R.id.txt_drive_comfort, c.getString(R.string.k_widget_lbl_drive_comfort));
        v.setTextViewText(R.id.txt_drive_sport, c.getString(R.string.k_widget_lbl_drive_sport));
        v.setTextViewText(R.id.txt_regen_low, c.getString(R.string.k_widget_lbl_regen_low));
        v.setTextViewText(R.id.txt_regen_mid, c.getString(R.string.k_widget_lbl_regen_mid));
        v.setTextViewText(R.id.txt_regen_high, c.getString(R.string.k_widget_lbl_regen_high));
        chip(v, c, R.id.chip_drive_eco, R.id.txt_drive_eco, drive != null && drive == CarProps.DRIVE_ECO);
        chip(v, c, R.id.chip_drive_comfort, R.id.txt_drive_comfort, drive != null && drive == CarProps.DRIVE_COMFORT);
        chip(v, c, R.id.chip_drive_sport, R.id.txt_drive_sport, drive != null && drive == CarProps.DRIVE_SPORT);
        chip(v, c, R.id.chip_regen_low, R.id.txt_regen_low, regen != null && regen == CarProps.REGEN_WEAK);
        chip(v, c, R.id.chip_regen_mid, R.id.txt_regen_mid, regen != null && regen == CarProps.REGEN_MEDIUM);
        chip(v, c, R.id.chip_regen_high, R.id.txt_regen_high, regen != null && regen == CarProps.REGEN_STRONG);
        click(v, c, R.id.chip_drive_eco, WidgetsCommon.DRIVE_ECO);
        click(v, c, R.id.chip_drive_comfort, WidgetsCommon.DRIVE_COMFORT);
        click(v, c, R.id.chip_drive_sport, WidgetsCommon.DRIVE_SPORT);
        click(v, c, R.id.chip_regen_low, WidgetsCommon.REGEN_LOW);
        click(v, c, R.id.chip_regen_mid, WidgetsCommon.REGEN_MID);
        click(v, c, R.id.chip_regen_high, WidgetsCommon.REGEN_HIGH);
        return v;
    }

    public static RemoteViews driveVertical(Context c, Integer drive) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_drive_mode_vertical);
        bg(v, c);
        int icon;
        int label;
        if (drive != null && drive == CarProps.DRIVE_ECO) {
            icon = R.drawable.ic_setting_drive_eco;
            label = R.string.k_widget_lbl_drive_eco;
        } else if (drive != null && drive == CarProps.DRIVE_SPORT) {
            icon = R.drawable.ic_setting_drive_sport;
            label = R.string.k_widget_lbl_drive_sport;
        } else {
            icon = R.drawable.ic_setting_drive_comfort;
            label = R.string.k_widget_lbl_drive_comfort;
        }
        v.setImageViewResource(R.id.img_drive_mode, icon);
        v.setTextViewText(R.id.tv_drive_mode_label, c.getString(label));
        v.setTextViewText(R.id.tv_drive_mode_footer, c.getString(R.string.k_qa_driving_label));
        click(v, c, R.id.btn_drive_next, WidgetsCommon.DRIVE_NEXT);
        click(v, c, R.id.btn_drive_prev, WidgetsCommon.DRIVE_PREV);
        return v;
    }

    public static RemoteViews regenVertical(Context c, Integer regen) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_regen_vertical);
        bg(v, c);
        int icon;
        int label;
        if (regen != null && regen == CarProps.REGEN_WEAK) {
            icon = R.drawable.ic_widget_regen_weak;
            label = R.string.k_widget_lbl_regen_low;
        } else if (regen != null && regen == CarProps.REGEN_STRONG) {
            icon = R.drawable.ic_widget_regen_strong;
            label = R.string.k_widget_lbl_regen_high;
        } else {
            icon = R.drawable.ic_widget_regen_mid;
            label = R.string.k_widget_lbl_regen_mid;
        }
        v.setImageViewResource(R.id.img_regen_level, icon);
        v.setTextViewText(R.id.tv_regen_level_label, c.getString(label));
        v.setTextViewText(R.id.tv_regen_level_footer, c.getString(R.string.k_qa_regen_label));
        click(v, c, R.id.btn_regen_next, WidgetsCommon.REGEN_NEXT);
        click(v, c, R.id.btn_regen_prev, WidgetsCommon.REGEN_PREV);
        return v;
    }

    public static RemoteViews fan(Context c, Integer fan, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        bg(v, c);
        v.setTextViewText(R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        v.setTextViewText(R.id.tv_fan_label, c.getString(R.string.k_widget_lbl_fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            v.setInt(FAN_DOTS[i], "setBackgroundResource",
                    fan != null && i < fan ? R.drawable.widget_dot_on : R.drawable.widget_dot_off);
        }
        click(v, c, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
        return v;
    }

    public static RemoteViews temperature(Context c, Float temp, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        bg(v, c);
        v.setTextViewText(R.id.tv_temp_value,
                temp == null ? "—" : String.valueOf(Math.round(temp)));
        v.setTextViewText(R.id.tv_temp_unit, "°C");
        v.setTextViewText(R.id.tv_temp_label, c.getString(R.string.k_widget_lbl_temp));
        click(v, c, R.id.btn_temp_minus, WidgetsCommon.TEMP_MINUS);
        click(v, c, R.id.btn_temp_plus, WidgetsCommon.TEMP_PLUS);
        return v;
    }

    public static RemoteViews vent(Context c, Integer vent, Boolean recirc) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction);
        bg(v, c);
        v.setTextViewText(R.id.txt_vent_header, c.getString(R.string.k_widget_lbl_vent_header));
        v.setTextViewText(R.id.txt_vent_face, c.getString(R.string.k_widget_lbl_vent_face));
        v.setTextViewText(R.id.txt_vent_floor, c.getString(R.string.k_widget_lbl_vent_floor));
        v.setTextViewText(R.id.txt_vent_face_floor, c.getString(R.string.k_widget_lbl_vent_face_floor));
        v.setTextViewText(R.id.txt_vent_windshield, c.getString(R.string.k_widget_lbl_vent_windshield));
        v.setTextViewText(R.id.txt_vent_floor_windshield, c.getString(R.string.k_widget_lbl_vent_floor_windshield));
        v.setTextViewText(R.id.txt_recirc, c.getString(R.string.k_widget_lbl_recirc));
        chip(v, c, R.id.chip_vent_face, R.id.txt_vent_face, vent != null && vent == 1);
        chip(v, c, R.id.chip_vent_floor, R.id.txt_vent_floor, vent != null && vent == 2);
        chip(v, c, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, vent != null && vent == 3);
        chip(v, c, R.id.chip_vent_windshield, R.id.txt_vent_windshield, vent != null && vent == 4);
        chip(v, c, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, vent != null && vent == 6);
        chip(v, c, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        click(v, c, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
        click(v, c, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
        click(v, c, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
        click(v, c, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
        click(v, c, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventVertical(Context c, Integer vent, Boolean recirc) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction_vertical);
        bg(v, c);
        int mode = vent == null ? 1 : vent;
        v.setImageViewResource(R.id.img_vent_mode, WidgetsCommon.ventIcon(mode));
        v.setTextViewText(R.id.tv_vent_mode_label, c.getString(WidgetsCommon.ventLabel(mode)));
        v.setTextViewText(R.id.tv_vent_mode_footer, c.getString(R.string.k_widget_lbl_vent_short));
        v.setTextViewText(R.id.txt_recirc_v, c.getString(R.string.k_widget_lbl_recirc));
        v.setInt(R.id.chip_recirc_v, "setBackgroundResource",
                Boolean.TRUE.equals(recirc) ? R.drawable.widget_chip_active : R.drawable.widget_chip_idle);
        v.setTextColor(R.id.txt_recirc_v, c.getColor(Boolean.TRUE.equals(recirc)
                ? R.color.cx_on_accent : R.color.cx_text_primary));
        click(v, c, R.id.btn_vent_next, WidgetsCommon.VENT_NEXT);
        click(v, c, R.id.btn_vent_prev, WidgetsCommon.VENT_PREV);
        click(v, c, R.id.chip_recirc_v, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventFan(Context c, Integer vent, Boolean recirc,
                                      Integer fan, Boolean ac) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction_fan);
        bg(v, c);
        int mode = vent == null ? 1 : vent;
        chip(v, c, R.id.chip_vent_face, R.id.txt_vent_face, mode == 1);
        chip(v, c, R.id.chip_vent_floor, R.id.txt_vent_floor, mode == 2);
        chip(v, c, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        chip(v, c, R.id.chip_vent_windshield, R.id.txt_vent_windshield, mode == 4);
        chip(v, c, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        chip(v, c, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        v.setTextViewText(R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            v.setInt(FAN_DOTS[i], "setBackgroundResource",
                    fan != null && i < fan ? R.drawable.widget_dot_on : R.drawable.widget_dot_off);
        }
        v.setInt(R.id.chip_fan_ac, "setBackgroundResource",
                Boolean.TRUE.equals(ac) ? R.drawable.widget_chip_active : R.drawable.widget_chip_ac_off);
        v.setTextColor(R.id.tv_fan_value, c.getColor(Boolean.TRUE.equals(ac)
                ? R.color.cx_on_accent : R.color.cx_text_primary));
        click(v, c, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
        click(v, c, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
        click(v, c, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
        click(v, c, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
        click(v, c, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        click(v, c, R.id.chip_fan_ac, WidgetsCommon.AC_TOGGLE);
        click(v, c, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
        return v;
    }

    public static RemoteViews heating(Context c, Boolean wheel, Boolean rear, Boolean front,
                                      Boolean windshield, Integer seatDriver, Integer seatPassenger) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_heating_test);
        bg(v, c);
        v.setTextViewText(R.id.txt_heat_header, c.getString(R.string.k_widget_lbl_heat_header));
        toggle(v, c, R.id.chip_wheel_heat, R.id.txt_wheel_heat,
                Boolean.TRUE.equals(wheel), c.getString(R.string.k_widget_lbl_heat_wheel));
        toggle(v, c, R.id.chip_front_rear_defrost, R.id.txt_front_rear_defrost,
                Boolean.TRUE.equals(rear), c.getString(R.string.k_widget_lbl_rear_defrost));
        toggle(v, c, R.id.chip_front_max_defrost, R.id.txt_front_max_defrost,
                Boolean.TRUE.equals(front), c.getString(R.string.k_widget_lbl_heat_front_max));
        toggle(v, c, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(windshield), c.getString(R.string.k_widget_lbl_heat_windshield));
        seat(v, c, R.id.chip_seat_heat_driver, R.id.txt_seat_heat_driver,
                seatDriver, c.getString(R.string.k_widget_lbl_heat_seat_driver));
        seat(v, c, R.id.chip_seat_heat_passenger, R.id.txt_seat_heat_passenger,
                seatPassenger, c.getString(R.string.k_widget_lbl_heat_seat_passenger));
        click(v, c, R.id.chip_wheel_heat, WidgetsCommon.WHEEL_HEAT_TOGGLE);
        click(v, c, R.id.chip_front_rear_defrost, WidgetsCommon.REAR_DEFROST_TOGGLE);
        click(v, c, R.id.chip_front_max_defrost, WidgetsCommon.FRONT_MAX_DEFROST_TOGGLE);
        click(v, c, R.id.chip_windshield_heat, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
        click(v, c, R.id.chip_seat_heat_driver, WidgetsCommon.SEAT_DRIVER_CYCLE);
        click(v, c, R.id.chip_seat_heat_passenger, WidgetsCommon.SEAT_PASSENGER_CYCLE);
        return v;
    }

    private static void bg(RemoteViews v, Context c) {
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.bgDrawableResForPct(c));
    }

    private static void chip(RemoteViews v, Context c, int chipId, int textId, boolean active) {
        v.setInt(chipId, "setBackgroundResource",
                active ? R.drawable.widget_chip_active : R.drawable.widget_chip_idle);
        v.setTextColor(textId, c.getColor(active ? R.color.cx_on_accent : R.color.cx_text_primary));
    }

    private static void toggle(RemoteViews v, Context c, int chipId, int textId,
                               boolean active, String label) {
        chip(v, c, chipId, textId, active);
        v.setTextViewText(textId, label);
    }

    private static void seat(RemoteViews v, Context c, int chipId, int textId,
                             Integer level, String label) {
        boolean active = level != null && level > 0;
        chip(v, c, chipId, textId, active);
        String value = level == null ? "—" : String.valueOf(level);
        v.setTextViewText(textId, label + " " + value);
    }

    private static void click(RemoteViews v, Context c, int viewId, String action) {
        Intent intent = new Intent(c, WidgetUpdateService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(c, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pi);
    }
}
