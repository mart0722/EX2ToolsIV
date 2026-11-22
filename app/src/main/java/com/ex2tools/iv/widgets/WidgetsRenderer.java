package com.ex2tools.iv.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
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
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_drive_regen));
        bg(v, c);
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons.forRemote(c, OverlayWidgetViews.themeContext(c), v).driveAndRegen();
        }
        titles(v, c, R.id.txt_drive_header, R.id.txt_regen_header);
        v.setTextViewText(R.id.txt_drive_header, c.getString(R.string.wg_drive_header));
        v.setTextViewText(R.id.txt_regen_header, c.getString(R.string.wg_regen_header));
        chip(v, c, R.id.chip_drive_eco, R.id.txt_drive_eco, R.string.wg_drive_eco,
                drive != null && drive == CarProps.DRIVE_ECO, 1);
        chip(v, c, R.id.chip_drive_comfort, R.id.txt_drive_comfort, R.string.wg_drive_comfort,
                drive != null && drive == CarProps.DRIVE_COMFORT, 0);
        chip(v, c, R.id.chip_drive_sport, R.id.txt_drive_sport, R.string.wg_drive_sport,
                drive != null && drive == CarProps.DRIVE_SPORT, 2);
        chip(v, c, R.id.chip_regen_low, R.id.txt_regen_low, R.string.wg_regen_low, regen != null && regen == CarProps.REGEN_WEAK);
        chip(v, c, R.id.chip_regen_mid, R.id.txt_regen_mid, R.string.wg_regen_mid, regen != null && regen == CarProps.REGEN_MEDIUM);
        chip(v, c, R.id.chip_regen_high, R.id.txt_regen_high, R.string.wg_regen_high, regen != null && regen == CarProps.REGEN_STRONG);
        click(v, c, R.id.chip_drive_eco, WidgetsCommon.DRIVE_ECO);
        click(v, c, R.id.chip_drive_comfort, WidgetsCommon.DRIVE_COMFORT);
        click(v, c, R.id.chip_drive_sport, WidgetsCommon.DRIVE_SPORT);
        click(v, c, R.id.chip_regen_low, WidgetsCommon.REGEN_LOW);
        click(v, c, R.id.chip_regen_mid, WidgetsCommon.REGEN_MID);
        click(v, c, R.id.chip_regen_high, WidgetsCommon.REGEN_HIGH);
        return v;
    }

    public static RemoteViews driveVertical(Context c, Integer drive) {
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_drive_mode_vertical));
        bg(v, c);
        titles(v, c, R.id.widget_title);
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
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, OverlayWidgetViews.themeContext(c), v);
            icons.chromeGlyph(R.id.img_drive_mode, icon, ClimateWidgetIcons.ACTION_ICON_DP);
            climateCaption(v, icons, R.id.img_drive_mode, R.id.tv_drive_mode_label, c.getString(label));
        } else {
            v.setImageViewResource(R.id.img_drive_mode, icon);
            v.setTextViewText(R.id.tv_drive_mode_label, c.getString(label));
        }
        v.setTextViewText(R.id.widget_title, c.getString(R.string.wg_footer_drive));
        roundControls(v, c, R.id.btn_drive_next, R.id.btn_drive_prev);
        click(v, c, R.id.btn_drive_next, WidgetsCommon.DRIVE_NEXT);
        click(v, c, R.id.btn_drive_prev, WidgetsCommon.DRIVE_PREV);
        return v;
    }

    public static RemoteViews regenVertical(Context c, Integer regen) {
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_regen_vertical));
        bg(v, c);
        titles(v, c, R.id.widget_title);
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
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(c, OverlayWidgetViews.themeContext(c), v);
            icons.regen(R.id.img_regen_level,
                    regen != null && regen == CarProps.REGEN_WEAK ? 1
                            : regen != null && regen == CarProps.REGEN_STRONG ? 3 : 2);
            climateCaption(v, icons, R.id.img_regen_level, R.id.tv_regen_level_label, c.getString(label));
        } else {
            v.setImageViewResource(R.id.img_regen_level, icon);
            v.setTextViewText(R.id.tv_regen_level_label, c.getString(label));
        }
        v.setTextViewText(R.id.widget_title, c.getString(R.string.wg_footer_regen));
        roundControls(v, c, R.id.btn_regen_next, R.id.btn_regen_prev);
        click(v, c, R.id.btn_regen_next, WidgetsCommon.REGEN_NEXT);
        click(v, c, R.id.btn_regen_prev, WidgetsCommon.REGEN_PREV);
        return v;
    }

    public static RemoteViews fan(Context c, Integer fan, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, layout));
        bg(v, c);
        titles(v, c, R.id.widget_title);
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons.forRemote(c, OverlayWidgetViews.themeContext(c), v)
                    .chromeGlyph(R.id.img_fan_title, R.drawable.wg_ic_fan, 20);
        }
        v.setTextViewText(R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        v.setTextViewText(layout == R.layout.widget_fan_speed_vertical
                ? R.id.widget_title : R.id.tv_fan_label, c.getString(R.string.wg_fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            v.setInt(FAN_DOTS[i], "setBackgroundResource",
                    fan != null && i < fan ? R.drawable.bg_wg_dot_on : R.drawable.bg_wg_dot_off);
        }
        roundControls(v, c, R.id.btn_fan_minus, R.id.btn_fan_plus);
        click(v, c, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
        return v;
    }

    public static RemoteViews temperature(Context c, Float temp, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, layout));
        bg(v, c);
        titles(v, c, R.id.widget_title);
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons.forRemote(c, OverlayWidgetViews.themeContext(c), v)
                    .chromeGlyph(R.id.img_temp_title, R.drawable.wg_ic_temp, 20);
        }
        v.setTextViewText(R.id.tv_temp_value,
                temp == null ? "—" : String.valueOf(Math.round(temp)));
        v.setTextViewText(R.id.tv_temp_unit, "°C");
        v.setTextViewText(layout == R.layout.widget_temperature_vertical
                ? R.id.widget_title : R.id.tv_temp_label, c.getString(R.string.wg_temp));
        if (temp != null) {
            int progress = Math.max(0, Math.min(15, Math.round(temp) - 17));
            v.setProgressBar(R.id.temp_bar, 16, progress, false);
        }
        roundControls(v, c, R.id.btn_temp_minus, R.id.btn_temp_plus);
        click(v, c, R.id.btn_temp_minus, WidgetsCommon.TEMP_MINUS);
        click(v, c, R.id.btn_temp_plus, WidgetsCommon.TEMP_PLUS);
        return v;
    }

    public static RemoteViews vent(Context c, Integer vent, Boolean recirc) {
        Context widgetContext = c;
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_hvac_direction));
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        if (WidgetAppearance.usesGlass(c)) {
            v.setViewVisibility(R.id.widget_vent_fan_row, View.GONE);
            titles(v, c, R.id.txt_vent_header);
        } else {
            titles(v, c, R.id.widget_title);
        }
        v.setTextViewText(R.id.txt_vent_header, c.getString(R.string.wg_vent_header));
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        int mode = vent == null ? 0 : vent;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, v);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        v.setViewVisibility(R.id.chip_vent_face_floor, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        v.setViewVisibility(R.id.chip_vent_floor_windshield, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(v, icons, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(v, icons, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(v, icons, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        v.setViewVisibility(R.id.chip_recirc, Prefs.showVentRecirc(c) ? View.VISIBLE : View.GONE);
        ventChip(v, icons, transparent, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        click(v, c, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
        click(v, c, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
        click(v, c, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
        click(v, c, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
        click(v, c, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventVertical(Context c, Integer vent, Boolean recirc) {
        Context widgetContext = c;
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_hvac_direction_vertical));
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        titles(v, c, R.id.widget_title);
        int mode = vent == null ? 1 : vent;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, v);
        icons.vent(R.id.img_vent_mode, mode, vent != null);
        climateCaption(v, icons, R.id.chip_vent_current, R.id.tv_vent_mode_label,
                icons.ventModeCaption(mode));
        icons.recirculation(R.id.img_recirc_v);
        v.setTextViewText(R.id.widget_title, c.getString(R.string.wg_vent_short));
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        v.setInt(R.id.chip_vent_current, "setBackgroundResource",
                icons.ventModeBackground(transparent, vent != null));
        int controlBackground = WidgetAppearance.controlBackground(c, transparent, R.drawable.bg_wg_round);
        v.setInt(R.id.btn_vent_next, "setBackgroundResource", controlBackground);
        v.setInt(R.id.btn_vent_prev, "setBackgroundResource", controlBackground);
        v.setViewVisibility(R.id.chip_recirc_v, Prefs.showVentRecirc(c) ? View.VISIBLE : View.GONE);
        ventChip(v, icons, transparent, R.id.chip_recirc_v,
                R.id.txt_recirc_v, Boolean.TRUE.equals(recirc));
        click(v, c, R.id.btn_vent_next, WidgetsCommon.VENT_NEXT);
        click(v, c, R.id.btn_vent_prev, WidgetsCommon.VENT_PREV);
        click(v, c, R.id.chip_recirc_v, WidgetsCommon.RECIRC_TOGGLE);
        return v;
    }

    public static RemoteViews ventFan(Context c, Integer vent, Boolean recirc,
                                      Integer fan, Boolean ac) {
        Context widgetContext = c;
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_hvac_direction_fan));
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        if (WidgetAppearance.usesGlass(c)) {
            v.setViewVisibility(R.id.widget_vent_fan_row, View.VISIBLE);
            v.setTextViewText(R.id.txt_vent_header, c.getString(R.string.widgets_size_vent_fan));
            titles(v, c, R.id.txt_vent_header);
        } else {
            titles(v, c, R.id.widget_title);
        }
        int mode = vent == null ? 1 : vent;
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = Prefs.widgetBgOpacity(c) < 10;
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, v);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        v.setViewVisibility(R.id.chip_vent_face_floor, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        v.setViewVisibility(R.id.chip_vent_floor_windshield, directions ? android.view.View.GONE : android.view.View.VISIBLE);
        ventChip(v, icons, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(v, icons, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(v, icons, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(v, icons, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        v.setViewVisibility(R.id.chip_recirc, Prefs.showVentRecirc(c) ? View.VISIBLE : View.GONE);
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
        click(v, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        int controlBackground = WidgetAppearance.controlBackground(c, transparent, R.drawable.bg_wg_round);
        v.setInt(R.id.btn_fan_minus, "setBackgroundResource", controlBackground);
        v.setInt(R.id.btn_fan_plus, "setBackgroundResource", controlBackground);
        click(v, c, R.id.btn_fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.btn_fan_plus, WidgetsCommon.FAN_PLUS);
        return v;
    }

    public static RemoteViews heating(Context c, Boolean wheel, Boolean rear,
                                      Boolean windshield, Integer seatDriver, Integer seatPassenger,
                                      boolean wheelPending) {
        Context widgetContext = c;
        boolean singleRow = Prefs.heatingSingleRow(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, singleRow
                ? R.layout.widget_heating_single_row : R.layout.widget_heating_test));
        c = OverlayWidgetViews.themeContext(c);
        bg(v, c);
        titles(v, c, R.id.widget_title);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, v);
        if (icons.usesOutline()) {
            icons.chromeGlyph(R.id.img_heating_title, R.drawable.wg_ic_heat, singleRow ? 14 : 20);
        }
        icons.heating(wheel, rear, windshield, seatDriver, seatPassenger);
        boolean showMaxAir = Prefs.showHeatingMaxAir(c);
        v.setViewVisibility(R.id.chip_front_max_defrost, showMaxAir ? View.VISIBLE : View.GONE);
        if (showMaxAir) {
            boolean front = Boolean.TRUE.equals(WidgetsCommon.readFrontMaxDefrost());
            icons.frontMaxDefrost(front);
            toggle(v, c, icons, R.id.chip_front_max_defrost, R.id.txt_front_max_defrost,
                    front, c.getString(icons.frontAirLabel()));
            click(v, c, R.id.chip_front_max_defrost, WidgetsCommon.FRONT_MAX_DEFROST_TOGGLE);
        }
        v.setTextViewText(R.id.txt_heat_header, c.getString(R.string.wg_heat_header));
        toggle(v, c, icons, R.id.chip_wheel_heat, R.id.txt_wheel_heat,
                Boolean.TRUE.equals(wheel), wheelPending, c.getString(R.string.wg_heat_wheel));
        toggle(v, c, icons, R.id.chip_front_rear_defrost, R.id.txt_front_rear_defrost,
                Boolean.TRUE.equals(rear), c.getString(R.string.wg_heat_rear));
        toggle(v, c, icons, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(windshield), c.getString(icons.electricWindshieldLabel()));
        seat(v, c, icons, R.id.chip_seat_heat_driver, R.id.txt_seat_heat_driver,
                seatDriver, c.getString(R.string.wg_heat_seat_driver), icons.usesOutline());
        seat(v, c, icons, R.id.chip_seat_heat_passenger, R.id.txt_seat_heat_passenger,
                seatPassenger, c.getString(R.string.wg_heat_seat_passenger), icons.usesOutline());
        click(v, c, R.id.chip_wheel_heat, WidgetsCommon.WHEEL_HEAT_TOGGLE);
        click(v, c, R.id.chip_front_rear_defrost, WidgetsCommon.REAR_DEFROST_TOGGLE);
        click(v, c, R.id.chip_windshield_heat, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
        click(v, c, R.id.chip_seat_heat_driver, WidgetsCommon.SEAT_DRIVER_CYCLE);
        click(v, c, R.id.chip_seat_heat_passenger, WidgetsCommon.SEAT_PASSENGER_CYCLE);
        return v;
    }

    static RemoteViews outsideTemperature(Context widgetContext, float value, int layout) {
        Context c = OverlayWidgetViews.themeContext(widgetContext);
        RemoteViews views = new RemoteViews(c.getPackageName(), layout);
        bg(views, c);
        ClimateWidgetIcons.forRemote(widgetContext, c, views).outsideTemperature();
        views.setTextViewText(R.id.tv_outside_temp, WidgetsCommon.outsideTemperatureText(c, value));
        return views;
    }

    static RemoteViews recirc(Context widgetContext, Boolean state) {
        Context c = OverlayWidgetViews.themeContext(widgetContext);
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_recirc);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, views);
        icons.recirculation(R.id.img_recirc);
        toggle(views, c, icons, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(state),
                false, c.getString(R.string.widget_name_recirc), false);
        click(views, c, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
        return views;
    }

    static RemoteViews wheelHeat(Context widgetContext, Boolean state) {
        Context c = OverlayWidgetViews.themeContext(widgetContext);
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_wheel_heat);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, views);
        icons.wheelHeat(state);
        toggle(views, c, icons, R.id.chip_wheel_heat, R.id.txt_wheel_heat, Boolean.TRUE.equals(state),
                WidgetsCommon.wheelHeatPending(c), c.getString(R.string.widget_name_wheel_heat), false);
        click(views, c, R.id.chip_wheel_heat, WidgetsCommon.WHEEL_HEAT_TOGGLE);
        return views;
    }

    static RemoteViews windshieldHeat(Context widgetContext, Boolean state) {
        Context c = OverlayWidgetViews.themeContext(widgetContext);
        int layout = WidgetGrid.layout(c, R.layout.widget_windshield_heat);
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        boolean glass = layout == R.layout.widget_glass_windshield_heat;
        if (!glass) bg(v, c);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, v);
        icons.windshieldHeat(state);
        toggle(v, c, icons, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(state), false, c.getString(R.string.widget_name_windshield_heat),
                !glass && Prefs.widgetBgOpacity(c) < 10);
        click(v, c, R.id.chip_windshield_heat, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
        return v;
    }

    static RemoteViews wipers(Context c) {
        return vehicleControl(c, false);
    }

    static RemoteViews powerOff(Context c) {
        return vehicleControl(c, true);
    }

    private static RemoteViews vehicleControl(Context widgetContext, boolean powerOff) {
        Context c = OverlayWidgetViews.themeContext(widgetContext);
        RemoteViews views = new RemoteViews(c.getPackageName(),
                powerOff ? R.layout.widget_power_off : R.layout.widget_wipers);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forRemote(widgetContext, c, views);
        icons.vehicleControl(powerOff);
        boolean active = !powerOff && WidgetsCommon.wiperServicePositionActive();
        int chip = powerOff ? R.id.chip_power_off : R.id.chip_wipers;
        toggle(views, c, icons, chip, powerOff ? R.id.txt_power_off : R.id.txt_wipers,
                active, false,
                c.getString(powerOff ? R.string.widget_name_power_off : R.string.widget_name_wipers),
                false);
        if (powerOff) {
            views.setOnClickPendingIntent(chip, PendingIntent.getActivity(c, chip,
                    VehiclePowerOffActivity.widgetIntent(c), PendingIntent.FLAG_IMMUTABLE));
        } else {
            click(views, c, chip, WidgetsCommon.WIPERS_TOGGLE);
        }
        return views;
    }

    public static RemoteViews batteryRange(Context c, Integer soc, Float range, Float energyKwh) {
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_battery_range));
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
        RemoteViews v = new RemoteViews(c.getPackageName(), WidgetGrid.layout(c, R.layout.widget_lock));
        bg(v, c);
        Context tc = OverlayWidgetViews.themeContext(c);
        if (WidgetAppearance.usesGlass(c)) {
            ClimateWidgetIcons.forRemote(c, tc, v)
                    .chromeGlyph(R.id.img_lock, R.drawable.wg_ic_lock, ClimateWidgetIcons.ACTION_ICON_DP);
        } else {
            v.setInt(R.id.img_lock, "setColorFilter", tc.getColor(R.color.wg_text));
        }
        Intent intent = new Intent(c, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(c, 0x4c4f434b, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.lock_body, pending);
        return v;
    }

    private static void titles(RemoteViews v, Context c, int... titleIds) {
        int visibility = Prefs.widgetTitles(c) ? View.VISIBLE : View.GONE;
        for (int id : titleIds) v.setViewVisibility(id, visibility);
    }

    private static void bg(RemoteViews v, Context c) {
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.bgDrawableResForPct(c));
    }

    private static void chip(RemoteViews v, Context c, int chipId, int textId, int label, boolean active) {
        chip(v, c, chipId, textId, label, active, 0);
    }

    /** accent: 0 — синий, 1 — зелёный, 2 — красный. */
    private static void chip(RemoteViews v, Context c, int chipId, int textId, int label,
                             boolean active, int accent) {
        v.setInt(chipId, "setBackgroundResource", WidgetAppearance.chipBackground(
                c, Prefs.widgetBgOpacity(c) < 10, active, accent));
        v.setTextColor(textId, WidgetAppearance.chipTextColor(c, active, accent));
        boolean showLabel = !WidgetAppearance.usesGlass(c) || WidgetIconSizes.appWidgetLabels(c);
        String caption = c.getString(label);
        v.setTextViewText(textId, caption);
        v.setViewVisibility(textId, showLabel ? View.VISIBLE : View.GONE);
        v.setContentDescription(chipId, showLabel ? null : caption);
    }

    private static void roundControls(RemoteViews v, Context c, int first, int second) {
        int background = WidgetAppearance.controlBackground(c, Prefs.widgetBgOpacity(c) < 10,
                R.drawable.bg_wg_round);
        v.setInt(first, "setBackgroundResource", background);
        v.setInt(second, "setBackgroundResource", background);
    }

    private static void ventChip(RemoteViews v, ClimateWidgetIcons icons, boolean transparent,
                                 int chipId, int textId, boolean active) {
        v.setInt(chipId, "setBackgroundResource",
                icons.buttonBackground(transparent, active, false, true));
        if (v.getLayoutId() == R.layout.widget_glass_vent) {
            v.setViewPadding(chipId, 2, 0, 2, 2);
        }
        climateCaption(v, icons, chipId, textId, icons.ventCaption(textId));
        v.setTextColor(textId, icons.buttonTextColor(active, true));
    }

    private static void toggle(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, String label) {
        toggle(v, c, icons, chipId, textId, active, false, label);
    }

    /** Три состояния: выключено / ожидание подтверждения (синяя обводка) / включено. */
    private static void toggle(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, boolean pending, String label) {
        toggle(v, c, icons, chipId, textId, active, pending, label, Prefs.widgetBgOpacity(c) < 10);
    }

    private static void toggle(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, boolean pending, String label, boolean transparent) {
        boolean airflow = chipId == R.id.chip_front_max_defrost && icons.usesOutline();
        v.setInt(chipId, "setBackgroundResource", icons.buttonBackground(transparent, active, pending, airflow));
        v.setTextColor(textId, pending && !active
                ? c.getColor(R.color.wg_blue_light)
                : icons.buttonTextColor(active, airflow));
        climateCaption(v, icons, chipId, textId, icons.caption(label));
    }

    private static void seat(RemoteViews v, Context c, ClimateWidgetIcons icons, int chipId, int textId,
                             Integer level, String label, boolean outline) {
        boolean active = level != null && level > 0;
        v.setInt(chipId, "setBackgroundResource", icons.buttonBackground(
                Prefs.widgetBgOpacity(c) < 10, active, false, false));
        v.setTextColor(textId, icons.buttonTextColor(active, false));
        String value = level == null ? "—" : String.valueOf(level);
        String labelWithLevel = label + " " + value;
        climateCaption(v, icons, chipId, textId, icons.caption(outline ? label : labelWithLevel),
                labelWithLevel);
        v.setContentDescription(textId, outline && icons.showsLabels()
                ? labelWithLevel : null);
    }

    private static void climateCaption(RemoteViews v, ClimateWidgetIcons icons, int chipId,
                                       int textId, CharSequence caption) {
        climateCaption(v, icons, chipId, textId, caption, caption);
    }

    private static void climateCaption(RemoteViews v, ClimateWidgetIcons icons, int chipId,
                                       int textId, CharSequence caption, CharSequence description) {
        boolean hideLabel = !icons.showsLabels();
        v.setViewVisibility(textId, hideLabel ? View.GONE : View.VISIBLE);
        v.setTextViewText(textId, caption);
        v.setContentDescription(chipId, hideLabel ? description : null);
    }

    private static void click(RemoteViews v, Context c, int viewId, String action) {
        Intent intent = new Intent(c, WidgetUpdateService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(c, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pi);
    }
}
