package com.ex2tools.iv.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.ex2tools.iv.R;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.lock.LockActivity;

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
        v.setTextViewText(R.id.txt_drive_header, c.getString(R.string.wg_drive_header));
        v.setTextViewText(R.id.txt_regen_header, c.getString(R.string.wg_regen_header));
        v.setTextViewText(R.id.txt_drive_eco, c.getString(R.string.wg_drive_eco));
        v.setTextViewText(R.id.txt_drive_comfort, c.getString(R.string.wg_drive_comfort));
        v.setTextViewText(R.id.txt_drive_sport, c.getString(R.string.wg_drive_sport));
        v.setTextViewText(R.id.txt_regen_low, c.getString(R.string.wg_regen_low));
        v.setTextViewText(R.id.txt_regen_mid, c.getString(R.string.wg_regen_mid));
        v.setTextViewText(R.id.txt_regen_high, c.getString(R.string.wg_regen_high));
        chip(v, c, R.id.chip_drive_eco, R.id.txt_drive_eco,
                drive != null && drive == CarProps.DRIVE_ECO, 1);
        chip(v, c, R.id.chip_drive_comfort, R.id.txt_drive_comfort,
                drive != null && drive == CarProps.DRIVE_COMFORT, 0);
        chip(v, c, R.id.chip_drive_sport, R.id.txt_drive_sport,
                drive != null && drive == CarProps.DRIVE_SPORT, 2);
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
            icon = R.drawable.wg_ic_drive_eco;
            label = R.string.wg_drive_eco;
        } else if (drive != null && drive == CarProps.DRIVE_SPORT) {
            icon = R.drawable.wg_ic_drive_sport;
            label = R.string.wg_drive_sport;
        } else {
            icon = R.drawable.wg_ic_drive_comfort;
            label = R.string.wg_drive_comfort;
        }
        v.setImageViewResource(R.id.img_drive_mode, icon);
        v.setTextViewText(R.id.tv_drive_mode_label, c.getString(label));
        v.setTextViewText(R.id.tv_drive_mode_footer, c.getString(R.string.wg_footer_drive));
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
            icon = R.drawable.wg_ic_regen_low;
            label = R.string.wg_regen_low;
        } else if (regen != null && regen == CarProps.REGEN_STRONG) {
            icon = R.drawable.wg_ic_regen_high;
            label = R.string.wg_regen_high;
        } else {
            icon = R.drawable.wg_ic_regen_mid;
            label = R.string.wg_regen_mid;
        }
        v.setImageViewResource(R.id.img_regen_level, icon);
        v.setTextViewText(R.id.tv_regen_level_label, c.getString(label));
        v.setTextViewText(R.id.tv_regen_level_footer, c.getString(R.string.wg_footer_regen));
        click(v, c, R.id.btn_regen_next, WidgetsCommon.REGEN_NEXT);
        click(v, c, R.id.btn_regen_prev, WidgetsCommon.REGEN_PREV);
        return v;
    }

    public static RemoteViews fan(Context c, Integer fan, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        bg(v, c);
        v.setTextViewText(R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        v.setTextViewText(R.id.tv_fan_label, c.getString(R.string.wg_fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            v.setInt(FAN_DOTS[i], "setBackgroundResource",
                    fan != null && i < fan ? R.drawable.bg_wg_dot_on : R.drawable.bg_wg_dot_off);
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
        v.setTextViewText(R.id.tv_temp_label, c.getString(R.string.wg_temp));
        if (temp != null) {
            int progress = Math.max(0, Math.min(15, Math.round(temp) - 17));
            v.setProgressBar(R.id.temp_bar, 16, progress, false);
        }
        click(v, c, R.id.btn_temp_minus, WidgetsCommon.TEMP_MINUS);
        click(v, c, R.id.btn_temp_plus, WidgetsCommon.TEMP_PLUS);
        return v;
    }

    public static RemoteViews vent(Context c, Integer vent, Boolean recirc) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction);
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        v.setTextViewText(R.id.txt_vent_header, c.getString(R.string.wg_vent_header));
        v.setTextViewText(R.id.txt_vent_face, c.getString(R.string.wg_vent_face));
        v.setTextViewText(R.id.txt_vent_floor, c.getString(R.string.wg_vent_floor));
        v.setTextViewText(R.id.txt_vent_windshield, c.getString(R.string.wg_vent_windshield));
        v.setTextViewText(R.id.txt_recirc, c.getString(R.string.wg_recirc));
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        int mode = vent == null ? 0 : vent;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, v);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        v.setInt(R.id.btn_vent_mode, "setBackgroundResource",
                ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_chip));
        v.setTextViewText(R.id.btn_vent_mode, c.getString(directions
                ? R.string.widgets_vent_directions : R.string.widgets_vent_presets));
        v.setViewVisibility(R.id.chip_vent_face_floor, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        v.setViewVisibility(R.id.chip_vent_floor_windshield, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(v, icons, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(v, icons, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(v, icons, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        ventChip(v, icons, transparent, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        click(v, c, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
        click(v, c, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
        click(v, c, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
        click(v, c, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
        click(v, c, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
        click(v, c, R.id.btn_vent_mode, WidgetsCommon.VENT_WIDGET_MODE);
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventVertical(Context c, Integer vent, Boolean recirc) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction_vertical);
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        int mode = vent == null ? 1 : vent;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, v);
        icons.vent(R.id.img_vent_mode, mode, vent != null);
        v.setTextViewText(R.id.tv_vent_mode_label, c.getString(WidgetsCommon.ventLabel(mode)));
        v.setTextViewText(R.id.tv_vent_mode_footer, c.getString(R.string.wg_vent_short));
        v.setTextViewText(R.id.txt_recirc_v, c.getString(R.string.wg_recirc));
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        int controlBackground = ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_round);
        v.setInt(R.id.btn_vent_next, "setBackgroundResource", controlBackground);
        v.setInt(R.id.btn_vent_prev, "setBackgroundResource", controlBackground);
        ventChip(v, icons, transparent, R.id.chip_recirc_v,
                R.id.txt_recirc_v, Boolean.TRUE.equals(recirc));
        click(v, c, R.id.btn_vent_next, WidgetsCommon.VENT_NEXT);
        click(v, c, R.id.btn_vent_prev, WidgetsCommon.VENT_PREV);
        click(v, c, R.id.chip_recirc_v, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventFan(Context c, Integer vent, Boolean recirc,
                                      Integer fan, Boolean ac) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hvac_direction_fan);
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        int mode = vent == null ? 1 : vent;
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, v);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        v.setInt(R.id.btn_vent_mode, "setBackgroundResource",
                ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_chip));
        v.setTextViewText(R.id.btn_vent_mode, c.getString(directions
                ? R.string.widgets_vent_directions : R.string.widgets_vent_presets));
        v.setViewVisibility(R.id.chip_vent_face_floor, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        v.setViewVisibility(R.id.chip_vent_floor_windshield, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(v, icons, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(v, icons, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(v, icons, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        ventChip(v, icons, transparent, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        v.setTextViewText(R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            v.setInt(FAN_DOTS[i], "setBackgroundResource",
                    fan != null && i < fan ? R.drawable.bg_wg_dot_on : R.drawable.bg_wg_dot_off);
        }
        v.setTextColor(R.id.tv_fan_value, c.getColor(R.color.wg_text));
        click(v, c, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
        click(v, c, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
        click(v, c, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
        click(v, c, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
        click(v, c, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
        click(v, c, R.id.btn_vent_mode, WidgetsCommon.VENT_WIDGET_MODE);
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        int controlBackground = ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_round);
        v.setInt(R.id.btn_fan_minus, "setBackgroundResource", controlBackground);
        v.setInt(R.id.btn_fan_plus, "setBackgroundResource", controlBackground);
        click(v, c, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
        return v;
    }

    public static RemoteViews heating(Context c, Boolean wheel, Boolean rear, Boolean front,
                                      Boolean windshield, Integer seatDriver, Integer seatPassenger,
                                      boolean wheelPending) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_heating_test);
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, v);
        icons.heating(wheel, rear, front, windshield, seatDriver, seatPassenger);
        v.setTextViewText(R.id.txt_heat_header, c.getString(R.string.wg_heat_header));
        toggle(v, c, icons, R.id.chip_wheel_heat, R.id.txt_wheel_heat,
                Boolean.TRUE.equals(wheel), wheelPending, c.getString(R.string.wg_heat_wheel));
        toggle(v, c, icons, R.id.chip_front_rear_defrost, R.id.txt_front_rear_defrost,
                Boolean.TRUE.equals(rear), c.getString(R.string.wg_heat_rear));
        toggle(v, c, icons, R.id.chip_front_max_defrost, R.id.txt_front_max_defrost,
                Boolean.TRUE.equals(front), c.getString(icons.frontAirLabel()));
        toggle(v, c, icons, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(windshield), c.getString(icons.electricWindshieldLabel()));
        seat(v, c, icons, R.id.chip_seat_heat_driver, R.id.txt_seat_heat_driver,
                seatDriver, c.getString(R.string.wg_heat_seat_driver), icons.usesOutline());
        seat(v, c, icons, R.id.chip_seat_heat_passenger, R.id.txt_seat_heat_passenger,
                seatPassenger, c.getString(R.string.wg_heat_seat_passenger), icons.usesOutline());
        click(v, c, R.id.chip_wheel_heat, WidgetsCommon.WHEEL_HEAT_TOGGLE);
        click(v, c, R.id.chip_front_rear_defrost, WidgetsCommon.REAR_DEFROST_TOGGLE);
        click(v, c, R.id.chip_front_max_defrost, WidgetsCommon.FRONT_MAX_DEFROST_TOGGLE);
        click(v, c, R.id.chip_windshield_heat, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
        click(v, c, R.id.chip_seat_heat_driver, WidgetsCommon.SEAT_DRIVER_CYCLE);
        click(v, c, R.id.chip_seat_heat_passenger, WidgetsCommon.SEAT_PASSENGER_CYCLE);
        return v;
    }

    public static RemoteViews batteryRange(Context c, Integer soc, Float range, Float energyKwh) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_battery_range);
        bg(v, c);
        Context tc = OverlayWidgetViews.themeContext(c);
        int primary = tc.getColor(R.color.wg_text);
        int secondary = tc.getColor(R.color.wg_text2);

        int percent = soc == null ? 0 : Math.max(0, Math.min(100, soc));
        v.setTextColor(R.id.tv_battery_range_percent, primary);
        v.setTextColor(R.id.tv_battery_range_energy, secondary);
        v.setTextColor(R.id.tv_battery_range_km, primary);

        v.setTextViewText(R.id.tv_battery_range_percent,
                soc == null ? "-" : c.getString(R.string.widget_battery_range_percent, percent));

        if (energyKwh == null || Float.isNaN(energyKwh)) {
            v.setTextViewText(R.id.tv_battery_range_energy, "-");
        } else {
            String value = String.format(new java.util.Locale("ru"), "%.1f", energyKwh);
            v.setTextViewText(R.id.tv_battery_range_energy,
                    c.getString(R.string.widget_battery_range_energy, value));
        }

        if (range == null || Float.isNaN(range)) {
            v.setTextViewText(R.id.tv_battery_range_km, "-");
        } else {
            v.setTextViewText(R.id.tv_battery_range_km,
                    c.getString(R.string.widget_battery_range_est,
                            String.valueOf(Math.round(range))));
        }

        v.setProgressBar(R.id.pb_battery_range, 100, soc == null ? 0 : percent, false);
        return v;
    }

    public static RemoteViews lock(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lock);
        bg(v, c);
        Context tc = OverlayWidgetViews.themeContext(c);
        v.setInt(R.id.img_lock, "setColorFilter", tc.getColor(R.color.wg_text));
        Intent intent = new Intent(c, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(c, 0x4c4f434b, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.lock_body, pending);
        return v;
    }

    private static void bg(RemoteViews v, Context c) {
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.bgDrawableResForPct(c));
    }

    private static void chip(RemoteViews v, Context c, int chipId, int textId, boolean active) {
        chip(v, c, chipId, textId, active, 0);
    }

    /** accent: 0 — синий, 1 — зелёный, 2 — красный. */
    private static void chip(RemoteViews v, Context c, int chipId, int textId,
                             boolean active, int accent) {
        int background;
        if (!active) {
            background = R.drawable.bg_wg_chip;
        } else if (accent == 1) {
            background = R.drawable.bg_wg_chip_on_green;
        } else if (accent == 2) {
            background = R.drawable.bg_wg_chip_on_red;
        } else {
            background = R.drawable.bg_wg_chip_on;
        }
        v.setInt(chipId, "setBackgroundResource", background);
        v.setTextColor(textId, c.getColor(R.color.wg_text));
    }

    private static void ventChip(RemoteViews v, ClimateWidgetIcons icons, boolean transparent,
                                 int chipId, int textId, boolean active) {
        v.setInt(chipId, "setBackgroundResource",
                icons.buttonBackground(transparent, active, false, true));
        v.setTextColor(textId, icons.buttonTextColor(active, true));
    }

    private static void toggle(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, String label) {
        toggle(v, c, icons, chipId, textId, active, false, label);
    }

    /** Три состояния: выключено / ожидание подтверждения (синяя обводка) / включено. */
    private static void toggle(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, boolean pending, String label) {
        boolean airflow = chipId == R.id.chip_front_max_defrost && icons.usesOutline();
        v.setInt(chipId, "setBackgroundResource", icons.buttonBackground(
                Prefs.widgetBgOpacity(c) < 10, active, pending, airflow));
        v.setTextColor(textId, pending && !active
                ? c.getColor(R.color.wg_blue_light)
                : icons.buttonTextColor(active, airflow));
        v.setTextViewText(textId, label);
    }

    private static void seat(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                             Integer level, String label, boolean outline) {
        boolean active = level != null && level > 0;
        v.setInt(chipId, "setBackgroundResource", icons.buttonBackground(
                Prefs.widgetBgOpacity(c) < 10, active, false, false));
        v.setTextColor(textId, icons.buttonTextColor(active, false));
        String value = level == null ? "—" : String.valueOf(level);
        String labelWithLevel = label + " " + value;
        v.setTextViewText(textId, outline ? label : labelWithLevel);
        v.setContentDescription(textId, outline ? labelWithLevel : null);
    }

    private static void click(RemoteViews v, Context c, int viewId, String action) {
        Intent intent = new Intent(c, WidgetUpdateService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(c, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pi);
    }
}
