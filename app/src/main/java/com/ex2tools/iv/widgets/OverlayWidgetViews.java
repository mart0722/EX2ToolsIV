package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.climate.Thermostat;
import com.ex2tools.iv.lock.LockActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        return create(c, key, 1f);
    }

    /**
     * scale — коэффициент размера оверлея. Меняем плотность контекста, чтобы
     * вместе с карточкой увеличивались иконки, отступы и текст.
     */
    static View create(Context c, String key, float scale) {
        View root = LayoutInflater.from(themeContext(c, scale)).inflate(layout(key), null);
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
            case WidgetsCommon.SIZE_BATTERY_RANGE:
                updateBatteryRange(c, root);
                break;
            case WidgetsCommon.SIZE_APPS:
                // Built once in create(); the selection only changes on restart.
                break;
            case WidgetsCommon.SIZE_CLIMATE:
                updateClimate(c, root);
                break;
            case WidgetsCommon.SIZE_CLOCK:
                updateClock(c, root);
                break;
            default:
                break;
        }
    }

    static Context themeContext(Context c) {
        return themeContext(c, 1f);
    }

    static Context themeContext(Context c, float scale) {
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
            if (scale > 1.02f || scale < 0.98f) {
                int baseDpi = cfg.densityDpi > 0
                        ? cfg.densityDpi
                        : c.getResources().getDisplayMetrics().densityDpi;
                cfg.densityDpi = Math.max(72, Math.round(baseDpi * scale));
            }
            return c.createConfigurationContext(cfg);
        } catch (Throwable ignored) {
            return c;
        }
    }

    private static int layout(String key) {
        return WidgetCatalog.layout(key);
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
                click(c, root, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
                click(c, root, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
                click(c, root, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
                click(c, root, R.id.btn_vent_mode, WidgetsCommon.VENT_WIDGET_MODE);
                click(c, root, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
                break;
            case WidgetsCommon.SIZE_VENT_FAN:
                click(c, root, R.id.chip_vent_face, WidgetsCommon.VENT_FACE);
                click(c, root, R.id.chip_vent_floor, WidgetsCommon.VENT_FLOOR);
                click(c, root, R.id.chip_vent_windshield, WidgetsCommon.VENT_WINDSHIELD);
                click(c, root, R.id.chip_vent_face_floor, WidgetsCommon.VENT_FACE_FLOOR);
                click(c, root, R.id.chip_vent_floor_windshield, WidgetsCommon.VENT_FLOOR_WINDSHIELD);
                click(c, root, R.id.btn_vent_mode, WidgetsCommon.VENT_WIDGET_MODE);
                click(c, root, R.id.chip_recirc, WidgetsCommon.RECIRC_TOGGLE);
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
            case WidgetsCommon.SIZE_LOCK:
                View lock = root.findViewById(R.id.lock_body);
                if (lock == null) lock = root;
                lock.setOnClickListener(v -> openLock(c));
                break;
            case WidgetsCommon.SIZE_CLIMATE:
                click(c, root, R.id.wg_climate_minus, WidgetsCommon.CLIMATE_TARGET_MINUS);
                click(c, root, R.id.wg_climate_plus, WidgetsCommon.CLIMATE_TARGET_PLUS);
                click(c, root, R.id.wg_climate_status, WidgetsCommon.CLIMATE_TOGGLE);
                break;
            case WidgetsCommon.SIZE_APPS:
                bindApps(c, root);
                break;
            default:
                break;
        }
    }

    private static void bindApps(Context base, View root) {
        Context c = themeContext(base);
        View containerView = root.findViewById(R.id.apps_container);
        if (!(containerView instanceof LinearLayout)) return;
        LinearLayout container = (LinearLayout) containerView;
        container.removeAllViews();
        List<String> packages = AppsOverlay.validSelected(c);
        if (packages.isEmpty()) return;

        int count = packages.size();
        int columns = AppsOverlay.columns(c, count);
        float scale = Prefs.overlaySizePct(c, WidgetsCommon.SIZE_APPS) / 100f;
        int padding = dp(c, Math.round(AppsOverlay.PADDING_DP * scale));
        int gap = dp(c, Math.round(AppsOverlay.GAP_DP * scale));
        int cell = dp(c, Math.round(AppsOverlay.CELL_DP * scale));
        int iconSize = dp(c, Math.round(AppsOverlay.ICON_DP * scale));
        int corner = dp(c, Math.round(AppsOverlay.CORNER_DP * scale));
        container.setPadding(padding, padding, padding, padding);
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = gap;
                container.addView(row, rowParams);
            }
            ImageView icon = appIcon(c, packages.get(i), cell, iconSize, corner);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(cell, cell);
            if (i % columns != 0) iconParams.setMarginStart(gap);
            if (row != null) row.addView(icon, iconParams);
        }
    }

    private static ImageView appIcon(Context c, String pkg, int cellPx, int iconPx, int cornerPx) {
        ImageView icon = new ImageView(c);
        Drawable drawable = AppsOverlay.icon(c, pkg);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        } else {
            icon.setImageResource(R.drawable.ic_apps);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setContentDescription(AppsOverlay.label(c, pkg));
        int inset = Math.max(0, (cellPx - iconPx) / 2);
        icon.setPadding(inset, inset, inset, inset);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(cornerPx);
        shape.setColor(Color.TRANSPARENT);
        icon.setBackground(shape);
        icon.setClipToOutline(true);
        icon.setOnClickListener(v -> AppsOverlay.launch(c, pkg));
        return icon;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private static void updateDriveRegen(Context c, View root) {
        Integer drive = WidgetsCommon.readDrive();
        Integer regen = WidgetsCommon.readRegen();
        text(root, R.id.txt_drive_header, c.getString(R.string.wg_drive_header));
        text(root, R.id.txt_regen_header, c.getString(R.string.wg_regen_header));
        text(root, R.id.txt_drive_eco, c.getString(R.string.wg_drive_eco));
        text(root, R.id.txt_drive_comfort, c.getString(R.string.wg_drive_comfort));
        text(root, R.id.txt_drive_sport, c.getString(R.string.wg_drive_sport));
        text(root, R.id.txt_regen_low, c.getString(R.string.wg_regen_low));
        text(root, R.id.txt_regen_mid, c.getString(R.string.wg_regen_mid));
        text(root, R.id.txt_regen_high, c.getString(R.string.wg_regen_high));
        chip(c, root, R.id.chip_drive_eco, R.id.txt_drive_eco,
                drive != null && drive == CarProps.DRIVE_ECO, 1);
        chip(c, root, R.id.chip_drive_comfort, R.id.txt_drive_comfort,
                drive != null && drive == CarProps.DRIVE_COMFORT, 0);
        chip(c, root, R.id.chip_drive_sport, R.id.txt_drive_sport,
                drive != null && drive == CarProps.DRIVE_SPORT, 2);
        chip(c, root, R.id.chip_regen_low, R.id.txt_regen_low,
                regen != null && regen == CarProps.REGEN_WEAK);
        chip(c, root, R.id.chip_regen_mid, R.id.txt_regen_mid,
                regen != null && regen == CarProps.REGEN_MEDIUM);
        chip(c, root, R.id.chip_regen_high, R.id.txt_regen_high,
                regen != null && regen == CarProps.REGEN_STRONG);
    }

    private static void updateDrive(Context c, View root) {
        Integer read = WidgetsCommon.readDrive();
        int drive = read == null ? 0 : read;
        int icon;
        int label;
        if (drive == CarProps.DRIVE_ECO) {
            icon = R.drawable.wg_ic_drive_eco;
            label = R.string.wg_drive_eco;
        } else if (drive == CarProps.DRIVE_SPORT) {
            icon = R.drawable.wg_ic_drive_sport;
            label = R.string.wg_drive_sport;
        } else {
            icon = R.drawable.wg_ic_drive_comfort;
            label = R.string.wg_drive_comfort;
        }
        image(root, R.id.img_drive_mode, icon);
        text(root, R.id.tv_drive_mode_label, c.getString(label));
        text(root, R.id.tv_drive_mode_footer, c.getString(R.string.wg_footer_drive));
    }

    private static void updateRegen(Context c, View root) {
        Integer read = WidgetsCommon.readRegen();
        int regen = read == null ? 0 : read;
        int icon;
        int label;
        if (regen == CarProps.REGEN_WEAK) {
            icon = R.drawable.wg_ic_regen_low;
            label = R.string.wg_regen_low;
        } else if (regen == CarProps.REGEN_STRONG) {
            icon = R.drawable.wg_ic_regen_high;
            label = R.string.wg_regen_high;
        } else {
            icon = R.drawable.wg_ic_regen_mid;
            label = R.string.wg_regen_mid;
        }
        image(root, R.id.img_regen_level, icon);
        text(root, R.id.tv_regen_level_label, c.getString(label));
        text(root, R.id.tv_regen_level_footer, c.getString(R.string.wg_footer_regen));
    }

    private static void updateFan(Context c, View root) {
        Integer fan = WidgetsCommon.readFan();
        text(root, R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        text(root, R.id.tv_fan_label, c.getString(R.string.wg_fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            View dot = root.findViewById(FAN_DOTS[i]);
            if (dot != null) {
                dot.setBackgroundResource(fan != null && i < fan
                        ? R.drawable.bg_wg_dot_on : R.drawable.bg_wg_dot_off);
            }
        }
    }

    private static void updateTemp(Context c, View root) {
        Float temp = WidgetsCommon.readTemp(c);
        text(root, R.id.tv_temp_value, temp == null ? "—" : String.valueOf(Math.round(temp)));
        text(root, R.id.tv_temp_unit, "\u00B0C");
        text(root, R.id.tv_temp_label, c.getString(R.string.wg_temp));
        View bar = root.findViewById(R.id.temp_bar);
        if (bar instanceof ProgressBar && temp != null) {
            int progress = Math.max(0, Math.min(15, Math.round(temp) - 17));
            ((ProgressBar) bar).setProgress(progress);
        }
    }

    private static void updateVent(Context c, View root) {
        Integer vent = WidgetsCommon.readVent();
        Boolean recirc = WidgetsCommon.readRecirc();
        int mode = vent == null ? 1 : vent;
        text(root, R.id.txt_vent_header, c.getString(R.string.wg_vent_header));
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = OverlayBackground.isTransparent(c, WidgetsCommon.SIZE_VENT);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forOverlay(c, root);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        root.findViewById(R.id.btn_vent_mode).setBackgroundResource(
                ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_chip));
        text(root, R.id.btn_vent_mode, c.getString(directions
                ? R.string.widgets_vent_directions : R.string.widgets_vent_presets));
        root.findViewById(R.id.chip_vent_face_floor).setVisibility(directions ? View.GONE : View.VISIBLE);
        ventChip(icons, root, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        root.findViewById(R.id.chip_vent_floor_windshield).setVisibility(directions ? View.GONE : View.VISIBLE);
        ventChip(icons, root, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(icons, root, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(icons, root, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(icons, root, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        ventChip(icons, root, transparent, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
    }

    private static void updateVentFan(Context c, View root) {
        Integer vent = WidgetsCommon.readVent();
        Boolean recirc = WidgetsCommon.readRecirc();
        Integer fan = WidgetsCommon.readFan();
        int mode = vent == null ? 1 : vent;
        boolean directions = Prefs.ventDirectionButtons(c);
        boolean transparent = OverlayBackground.isTransparent(c, WidgetsCommon.SIZE_VENT_FAN);
        ClimateWidgetIcons icons = ClimateWidgetIcons.forOverlay(c, root);
        icons.ventButtons(vent == null ? 0 : vent, directions);
        root.findViewById(R.id.btn_vent_mode).setBackgroundResource(
                ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_chip));
        text(root, R.id.btn_vent_mode, c.getString(directions
                ? R.string.widgets_vent_directions : R.string.widgets_vent_presets));
        root.findViewById(R.id.chip_vent_face_floor).setVisibility(directions ? View.GONE : View.VISIBLE);
        ventChip(icons, root, transparent, R.id.chip_vent_face_floor, R.id.txt_vent_face_floor, mode == 3);
        root.findViewById(R.id.chip_vent_floor_windshield).setVisibility(directions ? View.GONE : View.VISIBLE);
        ventChip(icons, root, transparent, R.id.chip_vent_floor_windshield, R.id.txt_vent_floor_windshield, mode == 6);
        ventChip(icons, root, transparent, R.id.chip_vent_face, R.id.txt_vent_face, directions ? (mode & 1) != 0 : mode == 1);
        ventChip(icons, root, transparent, R.id.chip_vent_floor, R.id.txt_vent_floor, directions ? (mode & 2) != 0 : mode == 2);
        ventChip(icons, root, transparent, R.id.chip_vent_windshield, R.id.txt_vent_windshield, directions ? (mode & 4) != 0 : mode == 4);
        ventChip(icons, root, transparent, R.id.chip_recirc, R.id.txt_recirc, Boolean.TRUE.equals(recirc));
        int controlBackground = ClimateWidgetIcons.controlBackground(c, transparent, R.drawable.bg_wg_round);
        root.findViewById(R.id.btn_fan_minus).setBackgroundResource(controlBackground);
        root.findViewById(R.id.btn_fan_plus).setBackgroundResource(controlBackground);
        text(root, R.id.tv_fan_value, fan == null ? "—" : String.valueOf(fan));
        for (int i = 0; i < FAN_DOTS.length; i++) {
            View dot = root.findViewById(FAN_DOTS[i]);
            if (dot != null) {
                dot.setBackgroundResource(fan != null && i < fan
                        ? R.drawable.bg_wg_dot_on : R.drawable.bg_wg_dot_off);
            }
        }
        View fanValue = root.findViewById(R.id.tv_fan_value);
        if (fanValue instanceof TextView) {
            ((TextView) fanValue).setTextColor(c.getColor(R.color.wg_text));
        }
    }

    private static void updateBatteryRange(Context c, View root) {
        Integer soc = WidgetsCommon.readSoc();
        float factory = WidgetsCommon.readRangeKm();
        float learned = com.ex2tools.iv.car.RangeModel.estimateKm(c);
        float display = Float.isNaN(learned) ? factory : learned;

        text(root, R.id.tv_battery_range_percent,
                soc == null ? "-"
                        : c.getString(R.string.widget_battery_range_percent, soc));
        if (soc == null) {
            text(root, R.id.tv_battery_range_energy, "-");
        } else {
            String energy = String.format(new java.util.Locale("ru"), "%.1f",
                    soc * com.ex2tools.iv.car.CarProps.CAPACITY_KWH / 100f);
            text(root, R.id.tv_battery_range_energy,
                    c.getString(R.string.widget_battery_range_energy, energy));
        }
        text(root, R.id.tv_battery_range_km,
                Float.isNaN(display) ? "-"
                        : c.getString(R.string.widget_battery_range_est,
                        String.valueOf(Math.round(display))));
        View bar = root.findViewById(R.id.pb_battery_range);
        if (bar instanceof android.widget.ProgressBar) {
            ((android.widget.ProgressBar) bar).setProgress(
                    soc == null ? 0 : Math.max(0, Math.min(100, soc)));
        }
    }

    private static void openLock(Context c) {
        try {
            Intent intent = new Intent(c, LockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(intent);
        } catch (Throwable t) {
            LogStore.add(c, "lock overlay open failed: " + t);
        }
    }

    private static void updateHeating(Context c, View root) {
        Boolean wheel = WidgetsCommon.readWheelHeat();
        Boolean rear = WidgetsCommon.readRearDefrost();
        Boolean front = WidgetsCommon.readFrontMaxDefrost();
        Boolean windshield = WidgetsCommon.readWindshieldHeat();
        Integer seatDriver = WidgetsCommon.readSeatHeat(1);
        Integer seatPassenger = WidgetsCommon.readSeatHeat(4);

        text(root, R.id.txt_heat_header, c.getString(R.string.wg_heat_header));
        ClimateWidgetIcons icons = ClimateWidgetIcons.forOverlay(c, root);
        icons.heating(wheel, rear, front, windshield, seatDriver, seatPassenger);
        toggle(c, root, icons, R.id.chip_wheel_heat, R.id.txt_wheel_heat,
                Boolean.TRUE.equals(wheel), WidgetsCommon.wheelHeatPending(c),
                c.getString(R.string.wg_heat_wheel));
        toggle(c, root, icons, R.id.chip_front_rear_defrost, R.id.txt_front_rear_defrost,
                Boolean.TRUE.equals(rear), c.getString(R.string.wg_heat_rear));
        toggle(c, root, icons, R.id.chip_front_max_defrost, R.id.txt_front_max_defrost,
                Boolean.TRUE.equals(front), c.getString(icons.frontAirLabel()));
        toggle(c, root, icons, R.id.chip_windshield_heat, R.id.txt_windshield_heat,
                Boolean.TRUE.equals(windshield), c.getString(icons.electricWindshieldLabel()));
        seat(c, root, icons, R.id.chip_seat_heat_driver, R.id.txt_seat_heat_driver,
                seatDriver, c.getString(R.string.wg_heat_seat_driver), icons.usesOutline());
        seat(c, root, icons, R.id.chip_seat_heat_passenger, R.id.txt_seat_heat_passenger,
                seatPassenger, c.getString(R.string.wg_heat_seat_passenger), icons.usesOutline());
    }

    private static void updateClock(Context c, View root) {
        int color = Prefs.clockColor(c) == 1 ? android.graphics.Color.WHITE
                : Prefs.clockColor(c) == 2 ? android.graphics.Color.BLACK
                : c.getColor(R.color.clock_text);
        String dateText;
        try {
            String value = new SimpleDateFormat("EEEE, d MMMM", new java.util.Locale("ru"))
                    .format(new Date());
            dateText = value.isEmpty() ? value
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        } catch (Throwable t) {
            dateText = "";
        }
        android.graphics.Typeface typeface = android.graphics.Typeface.create(
                Prefs.clockFont(c) == 1 ? "sans-serif-condensed" : "sans-serif-medium",
                android.graphics.Typeface.NORMAL);
        View time = root.findViewById(R.id.clockTime);
        if (time instanceof TextView) {
            ((TextView) time).setTextColor(color);
            ((TextView) time).setTypeface(typeface);
        }
        View date = root.findViewById(R.id.clockDate);
        if (date instanceof TextView) {
            ((TextView) date).setText(dateText);
            ((TextView) date).setTextColor(color);
            ((TextView) date).setTypeface(android.graphics.Typeface.create(
                    Prefs.clockFont(c) == 1 ? "sans-serif-condensed" : "sans-serif",
                    android.graphics.Typeface.NORMAL));
        }
    }

    private static void updateClimate(Context c, View root) {
        boolean enabled = Prefs.climateEnabled(c);
        float temp = Prefs.sensorTemp(c);
        long at = Prefs.sensorAt(c);
        boolean fresh = at > 0 && System.currentTimeMillis() - at <= Thermostat.STALE_MS;
        text(root, R.id.wg_climate_cabin_temp,
                Float.isNaN(temp) ? "--" : String.format(Locale.US, "%.1f°", temp));

        int humidity = Prefs.sensorHumidity(c);
        int battery = Prefs.sensorBattery(c);
        String humidityText = "--";
        if (humidity >= 0 && battery >= 0) {
            humidityText = humidity + "% · " + battery + "%";
        } else if (humidity >= 0) {
            humidityText = humidity + "%";
        }
        text(root, R.id.wg_climate_humidity, humidityText);
        text(root, R.id.wg_climate_target_value,
                String.format(Locale.US, "%.0f°", Prefs.climateTarget(c)));

        String status;
        if (!enabled) {
            status = c.getString(R.string.wg_climate_off);
        } else if (!fresh) {
            status = c.getString(R.string.wg_climate_wait);
        } else {
            status = c.getString(R.string.wg_climate_auto);
        }
        text(root, R.id.wg_climate_status, status);
        View statusView = root.findViewById(R.id.wg_climate_status);
        if (statusView instanceof TextView) {
            ((TextView) statusView).setTextColor(c.getColor(enabled && fresh
                    ? R.color.wg_text : R.color.wg_text2));
        }
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
        chip(c, root, chipId, textId, active, 0);
    }

    /** accent: 0 — синий, 1 — зелёный, 2 — красный. */
    private static void chip(Context c, View root, int chipId, int textId,
                             boolean active, int accent) {
        View chip = root.findViewById(chipId);
        if (chip != null) {
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
            chip.setBackgroundResource(background);
        }
        View text = root.findViewById(textId);
        if (text instanceof TextView) {
            ((TextView) text).setTextColor(c.getColor(R.color.wg_text));
        }
    }

    private static void ventChip(ClimateWidgetIcons icons, View root, boolean transparent,
                                 int chipId, int textId, boolean active) {
        root.findViewById(chipId).setBackgroundResource(
                icons.buttonBackground(transparent, active, false, true));
        TextView caption = root.findViewById(textId);
        caption.setText(icons.ventCaption(textId));
        caption.setTextColor(icons.buttonTextColor(active, true));
    }

    private static void toggle(Context c, View root, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, String label) {
        toggle(c, root, icons, chipId, textId, active, false, label);
    }

    private static void toggle(Context c, View root, ClimateWidgetIcons icons, int chipId, int textId,
                               boolean active, boolean pending, String label) {
        boolean airflow = chipId == R.id.chip_front_max_defrost && icons.usesOutline();
        boolean transparent = OverlayBackground.isTransparent(c, WidgetsCommon.SIZE_HEATING);
        root.findViewById(chipId).setBackgroundResource(
                icons.buttonBackground(transparent, active, pending, airflow));
        TextView caption = root.findViewById(textId);
        caption.setText(icons.caption(label));
        caption.setTextColor(pending && !active
                ? c.getColor(R.color.wg_blue_light)
                : icons.buttonTextColor(active, airflow));
    }

    private static void seat(Context c, View root, ClimateWidgetIcons icons, int chipId, int textId,
                             Integer level, String label, boolean outline) {
        boolean active = level != null && level > 0;
        boolean transparent = OverlayBackground.isTransparent(c, WidgetsCommon.SIZE_HEATING);
        root.findViewById(chipId).setBackgroundResource(
                icons.buttonBackground(transparent, active, false, false));
        String value = level == null ? "—" : String.valueOf(level);
        String labelWithLevel = label + " " + value;
        TextView caption = root.findViewById(textId);
        if (caption != null) {
            caption.setTextColor(icons.buttonTextColor(active, false));
            caption.setText(icons.caption(outline ? label : labelWithLevel));
            caption.setContentDescription(outline ? labelWithLevel : null);
        }
    }

    private static void click(Context c, View root, int id, String action) {
        View v = root.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(x -> startAction(c, action));
    }

    private static void clickParent(Context c, View root, int id, String action) {
        View v = root.findViewById(id);
        if (v == null) return;
        android.view.ViewParent parent = v.getParent();
        if (parent instanceof View) {
            ((View) parent).setOnClickListener(x -> startAction(c, action));
        }
    }

    private static void startAction(Context c, String action) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, WidgetUpdateService.class).setAction(action));
        } catch (Throwable t) {
            LogStore.add(c, "overlay click failed " + action + ": " + t);
        }
    }
}
