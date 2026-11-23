package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.widget.RemoteViews;

import androidx.core.os.BundleCompat;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import java.util.List;

/** Dimensions of the glass tiles, before applying the shared widget scale. */
final class WidgetGrid {
    static final int UNIT_DP = 96;

    private WidgetGrid() {}

    static int layout(Context context, int original) {
        if (!WidgetAppearance.usesGlass(context)) return original;
        if (original == R.layout.widget_hvac_direction || original == R.layout.widget_hvac_direction_fan) {
            return R.layout.widget_glass_vent;
        }
        return GLASS_LAYOUTS.get(original, original);
    }

    private static final SparseIntArray GLASS_LAYOUTS = new SparseIntArray();
    private static final SparseArray<Size> FOOTPRINTS = new SparseArray<>();
    private static final Size DEFAULT_SIZE = cells(3, 1);

    static {
        GLASS_LAYOUTS.put(R.layout.widget_fan_speed, R.layout.widget_glass_fan_speed);
        GLASS_LAYOUTS.put(R.layout.widget_temperature, R.layout.widget_glass_temperature);
        GLASS_LAYOUTS.put(R.layout.widget_fan_speed_vertical, R.layout.widget_glass_fan_speed_vertical);
        GLASS_LAYOUTS.put(R.layout.widget_temperature_vertical, R.layout.widget_glass_temperature_vertical);
        GLASS_LAYOUTS.put(R.layout.widget_heating_test, R.layout.widget_glass_heating_test);
        GLASS_LAYOUTS.put(R.layout.widget_heating_single_row, R.layout.widget_glass_heating_single_row);
        GLASS_LAYOUTS.put(R.layout.widget_drive_regen, R.layout.widget_glass_drive_regen);
        GLASS_LAYOUTS.put(R.layout.widget_drive_mode_vertical, R.layout.widget_glass_drive_mode_vertical);
        GLASS_LAYOUTS.put(R.layout.widget_regen_vertical, R.layout.widget_glass_regen_vertical);
        GLASS_LAYOUTS.put(R.layout.widget_lock, R.layout.widget_glass_lock);
        GLASS_LAYOUTS.put(R.layout.widget_battery_range, R.layout.widget_glass_battery_range);
        GLASS_LAYOUTS.put(R.layout.widget_windshield_heat, R.layout.widget_glass_windshield_heat);

        for (int panel : new int[]{R.layout.widget_heating_test, R.layout.widget_drive_regen,
                R.layout.widget_climate_control}) {
            FOOTPRINTS.put(panel, cells(3, 2));
        }
        for (int vertical : new int[]{R.layout.widget_drive_mode_vertical, R.layout.widget_regen_vertical,
                R.layout.widget_hvac_direction_vertical, R.layout.widget_fan_speed_vertical,
                R.layout.widget_temperature_vertical}) {
            FOOTPRINTS.put(vertical, cells(1, 3));
        }
        for (int button : new int[]{R.layout.widget_lock, R.layout.widget_wipers, R.layout.widget_power_off,
                R.layout.widget_windshield_heat, R.layout.widget_wheel_heat, R.layout.widget_recirc}) {
            FOOTPRINTS.put(button, cells(1, 1));
        }
        for (int readout : new int[]{R.layout.widget_clock_classic_date,
                R.layout.widget_clock_condensed_date, R.layout.widget_battery_range,
                R.layout.widget_real_range}) {
            FOOTPRINTS.put(readout, cells(2, 1));
        }
        FOOTPRINTS.put(R.layout.widget_speed, cells(2, 2));
        FOOTPRINTS.put(R.layout.widget_clock_large, cells(3, 2));
        FOOTPRINTS.put(R.layout.widget_outside_temp, cells(1, 1));
        FOOTPRINTS.put(R.layout.widget_outside_temp_wide, cells(2, 1));
    }

    static Size size(Context c, int layout) {
        if (layout == R.layout.widget_hvac_direction || layout == R.layout.widget_hvac_direction_fan
                || (layout == R.layout.widget_temperature && Prefs.overlayWideTemperature(c))) {
            int columns = Prefs.ventDirectionButtons(c) ? 3 : 5;
            if (Prefs.showVentRecirc(c)) columns++;
            return cells(columns, layout == R.layout.widget_hvac_direction_fan ? 2 : 1);
        }
        if (layout == R.layout.widget_clock_overlay && Prefs.clockDate(c)) return cells(2, 1);
        if (layout == R.layout.widget_heating_single_row
                || (layout == R.layout.widget_heating_test && Prefs.heatingSingleRow(c))) {
            return cells(Prefs.showHeatingMaxAir(c) ? 6 : 5, 1);
        }
        return FOOTPRINTS.get(layout, DEFAULT_SIZE);
    }

    static Size cells(int columns, int rows) {
        return new Size(columns * UNIT_DP, rows * UNIT_DP);
    }

    static float scale(Context context) {
        return Prefs.glassWidgetSizePct(context) / 100f;
    }

    /**
     * RemoteViews uses the host's resource density, so scale text and fixed round controls
     * explicitly. The floating views inherit these measurements through their density context.
     */
    static void scaleContent(Context context, RemoteViews views, float scale) {
        boolean glass = WidgetAppearance.usesGlass(context);
        float iconScale = glass ? Prefs.glassWidgetIconSizePct(context) / 100f : 1f;
        if (scale == 1f && iconScale == 1f) return;
        int layout = views.getLayoutId();
        if (!glass) {
            scaleStandardContent(views, layout, scale);
            return;
        }
        if (scale != 1f && layout != R.layout.widget_recirc) {
            boolean compact = layout == R.layout.widget_glass_vent || layout == R.layout.widget_glass_fan_speed
                    || layout == R.layout.widget_glass_temperature || layout == R.layout.widget_glass_heating_single_row;
            float inset = context.getResources().getDimension(R.dimen.wg_glass_inset) * scale;
            float vertical = compact
                    ? context.getResources().getDimension(R.dimen.wg_glass_compact_inset) * scale : inset;
            views.setViewPadding(R.id.widget_glass_content, Math.round(inset), Math.round(vertical),
                    Math.round(inset), Math.round(vertical));
        }
        if (layout == R.layout.widget_glass_vent
                || layout == R.layout.widget_hvac_direction
                || layout == R.layout.widget_hvac_direction_fan) {
            textSize(views, scale, 11, R.id.txt_vent_header, R.id.txt_vent_face, R.id.txt_vent_floor,
                    R.id.txt_vent_face_floor, R.id.txt_vent_windshield, R.id.txt_vent_floor_windshield,
                    R.id.txt_recirc);
            textSize(views, scale, 30, R.id.tv_fan_value);
            roundSize(views, scale, iconScale, 44, 26, R.id.btn_fan_minus, R.id.btn_fan_plus);
        } else if (layout == R.layout.widget_glass_heating_test
                || layout == R.layout.widget_glass_heating_single_row
                || layout == R.layout.widget_heating_test
                || layout == R.layout.widget_heating_single_row) {
            textSize(views, scale, 11, R.id.txt_heat_header, R.id.txt_wheel_heat,
                    R.id.txt_front_max_defrost, R.id.txt_front_rear_defrost, R.id.txt_windshield_heat,
                    R.id.txt_seat_heat_driver, R.id.txt_seat_heat_passenger);
        } else if (layout == R.layout.widget_glass_drive_regen
                || layout == R.layout.widget_drive_regen) {
            textSize(views, scale, 11, R.id.txt_drive_header, R.id.txt_regen_header,
                    R.id.txt_drive_eco, R.id.txt_drive_comfort, R.id.txt_drive_sport,
                    R.id.txt_regen_low, R.id.txt_regen_mid, R.id.txt_regen_high);
            size(views, scale, 38, R.id.img_drive_eco, R.id.img_drive_comfort, R.id.img_drive_sport,
                    R.id.img_regen_low, R.id.img_regen_mid, R.id.img_regen_high);
        } else if (layout == R.layout.widget_glass_fan_speed || layout == R.layout.widget_glass_temperature
                || layout == R.layout.widget_glass_fan_speed_vertical
                || layout == R.layout.widget_glass_temperature_vertical
                || layout == R.layout.widget_fan_speed || layout == R.layout.widget_temperature
                || layout == R.layout.widget_fan_speed_vertical
                || layout == R.layout.widget_temperature_vertical) {
            boolean vertical = layout == R.layout.widget_glass_fan_speed_vertical
                    || layout == R.layout.widget_glass_temperature_vertical
                    || layout == R.layout.widget_fan_speed_vertical
                    || layout == R.layout.widget_temperature_vertical;
            textSize(views, scale, vertical ? 44 : 28, R.id.tv_fan_value, R.id.tv_temp_value);
            textSize(views, scale, 14, R.id.tv_temp_unit);
            if (vertical) textSize(views, scale, 11, R.id.widget_title);
            else textSize(views, scale, 11, R.id.tv_fan_label, R.id.tv_temp_label);
            roundSize(views, scale, iconScale, vertical ? 52 : 48, 26, R.id.btn_fan_minus, R.id.btn_fan_plus,
                    R.id.btn_temp_minus, R.id.btn_temp_plus);
        } else if (layout == R.layout.widget_glass_drive_mode_vertical
                || layout == R.layout.widget_glass_regen_vertical
                || layout == R.layout.widget_hvac_direction_vertical
                || layout == R.layout.widget_drive_mode_vertical
                || layout == R.layout.widget_regen_vertical) {
            textSize(views, scale, 11, R.id.widget_title, R.id.txt_recirc_v);
            textSize(views, scale, 17, R.id.tv_drive_mode_label, R.id.tv_regen_level_label);
            textSize(views, scale, 15, R.id.tv_vent_mode_label);
            size(views, scale, 76, R.id.img_drive_mode, R.id.img_regen_level);
            roundSize(views, scale, iconScale, 44, 24, R.id.btn_drive_next, R.id.btn_drive_prev, R.id.btn_regen_next,
                    R.id.btn_regen_prev, R.id.btn_vent_next, R.id.btn_vent_prev);
        } else if (layout == R.layout.widget_glass_battery_range
                || layout == R.layout.widget_battery_range) {
            textSize(views, scale, 22, R.id.tv_battery_range_percent);
            textSize(views, scale, 20, R.id.tv_battery_range_km);
            textSize(views, scale, 12, R.id.tv_battery_range_energy);
        } else if (layout == R.layout.widget_real_range) {
            textSize(views, scale, 30, R.id.tv_real_range);
            textSize(views, scale, 12, R.id.widget_title);
        } else if (layout == R.layout.widget_outside_temp
                || layout == R.layout.widget_outside_temp_wide) {
            textSize(views, scale, layout == R.layout.widget_outside_temp ? 18 : 36,
                    R.id.tv_outside_temp);
        } else if (layout == R.layout.widget_recirc) {
            textSize(views, scale, 11, R.id.txt_recirc);
        } else if (layout == R.layout.widget_wipers || layout == R.layout.widget_power_off
                || layout == R.layout.widget_wheel_heat || layout == R.layout.widget_glass_windshield_heat) {
            textSize(views, scale, 11, R.id.txt_wipers, R.id.txt_power_off,
                    R.id.txt_wheel_heat, R.id.txt_windshield_heat);
        } else if (layout == R.layout.widget_lock) {
            size(views, scale, 64, R.id.img_lock);
        }
    }

    private static void scaleStandardContent(RemoteViews views, int layout, float scale) {
        if (layout == R.layout.widget_heating_test || layout == R.layout.widget_heating_single_row) {
            textSize(views, scale, 16, R.id.txt_heat_header);
            textSize(views, scale, 11, R.id.txt_wheel_heat, R.id.txt_front_max_defrost,
                    R.id.txt_front_rear_defrost, R.id.txt_windshield_heat,
                    R.id.txt_seat_heat_driver, R.id.txt_seat_heat_passenger);
        } else if (layout == R.layout.widget_hvac_direction || layout == R.layout.widget_hvac_direction_fan) {
            textSize(views, scale, 16, R.id.txt_vent_header);
            textSize(views, scale, 11, R.id.txt_vent_face, R.id.txt_vent_floor,
                    R.id.txt_vent_face_floor, R.id.txt_vent_windshield,
                    R.id.txt_vent_floor_windshield, R.id.txt_recirc);
            textSize(views, scale, 30, R.id.tv_fan_value);
            roundSize(views, scale, 1f, 44, 26, R.id.btn_fan_minus, R.id.btn_fan_plus);
        } else if (layout == R.layout.widget_fan_speed || layout == R.layout.widget_temperature) {
            textSize(views, scale, 16, R.id.tv_fan_label, R.id.tv_temp_label);
            textSize(views, scale, 44, R.id.tv_fan_value, R.id.tv_temp_value);
            textSize(views, scale, 14, R.id.tv_temp_unit);
            roundSize(views, scale, 1f, 48, 26, R.id.btn_fan_minus, R.id.btn_fan_plus,
                    R.id.btn_temp_minus, R.id.btn_temp_plus);
        } else if (layout == R.layout.widget_fan_speed_vertical
                || layout == R.layout.widget_temperature_vertical) {
            textSize(views, scale, 11, R.id.widget_title);
            textSize(views, scale, 44, R.id.tv_fan_value, R.id.tv_temp_value);
            textSize(views, scale, 14, R.id.tv_temp_unit);
            roundSize(views, scale, 1f, 48, 26, R.id.btn_fan_minus, R.id.btn_fan_plus,
                    R.id.btn_temp_minus, R.id.btn_temp_plus);
        } else if (layout == R.layout.widget_drive_regen) {
            textSize(views, scale, 11, R.id.txt_drive_header, R.id.txt_regen_header,
                    R.id.txt_drive_eco, R.id.txt_drive_comfort, R.id.txt_drive_sport,
                    R.id.txt_regen_low, R.id.txt_regen_mid, R.id.txt_regen_high);
            size(views, scale, 38, R.id.img_drive_eco, R.id.img_drive_comfort, R.id.img_drive_sport,
                    R.id.img_regen_low, R.id.img_regen_mid, R.id.img_regen_high);
        } else if (layout == R.layout.widget_drive_mode_vertical
                || layout == R.layout.widget_regen_vertical
                || layout == R.layout.widget_hvac_direction_vertical) {
            textSize(views, scale, 11, R.id.widget_title, R.id.txt_recirc_v);
            textSize(views, scale, 17, R.id.tv_drive_mode_label, R.id.tv_regen_level_label);
            textSize(views, scale, 15, R.id.tv_vent_mode_label);
            size(views, scale, 76, R.id.img_drive_mode, R.id.img_regen_level);
            roundSize(views, scale, 1f, 44, 24, R.id.btn_drive_next, R.id.btn_drive_prev,
                    R.id.btn_regen_next, R.id.btn_regen_prev,
                    R.id.btn_vent_next, R.id.btn_vent_prev);
        } else if (layout == R.layout.widget_battery_range) {
            textSize(views, scale, 22, R.id.tv_battery_range_percent);
            textSize(views, scale, 20, R.id.tv_battery_range_km);
            textSize(views, scale, 12, R.id.tv_battery_range_energy);
        } else if (layout == R.layout.widget_real_range) {
            textSize(views, scale, 30, R.id.tv_real_range);
            textSize(views, scale, 12, R.id.widget_title);
        } else if (layout == R.layout.widget_outside_temp || layout == R.layout.widget_outside_temp_wide) {
            textSize(views, scale, layout == R.layout.widget_outside_temp ? 18 : 36,
                    R.id.tv_outside_temp);
        } else if (layout == R.layout.widget_recirc) {
            textSize(views, scale, 11, R.id.txt_recirc);
        } else if (layout == R.layout.widget_wipers || layout == R.layout.widget_power_off
                || layout == R.layout.widget_wheel_heat || layout == R.layout.widget_windshield_heat) {
            textSize(views, scale, 11, R.id.txt_wipers, R.id.txt_power_off,
                    R.id.txt_wheel_heat, R.id.txt_windshield_heat);
        } else if (layout == R.layout.widget_lock) {
            size(views, scale, 64, R.id.img_lock);
        }
    }

    private static void size(RemoteViews views, float scale, int dp, int... ids) {
        if (scale == 1f || Build.VERSION.SDK_INT < 31) return;
        for (int id : ids) {
            views.setViewLayoutWidth(id, dp * scale, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(id, dp * scale, TypedValue.COMPLEX_UNIT_DIP);
        }
    }

    private static void textSize(RemoteViews views, float scale, int sp, int... ids) {
        if (scale == 1f) return;
        for (int id : ids) views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * scale);
    }

    private static void roundSize(RemoteViews views, float scale, float iconScale, int dp, int sp, int... ids) {
        textSize(views, scale * iconScale, sp, ids);
        if (scale == 1f || Build.VERSION.SDK_INT < 31) return;
        for (int id : ids) {
            views.setViewLayoutWidth(id, dp * scale, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(id, dp * scale, TypedValue.COMPLEX_UNIT_DIP);
        }
    }

    /**
     * AppWidget hosts choose the outer grid allocation. Center a tile of the same proportions
     * inside it, fitting it uniformly if the allocated space is smaller than requested.
     */
    record Slot(float scale, int left, int top, int right, int bottom) {
        RemoteViews wrap(Context context, RemoteViews content) {
            if (left == 0 && top == 0 && right == 0 && bottom == 0) return content;
            RemoteViews frame = new RemoteViews(context.getPackageName(), R.layout.widget_grid_frame);
            frame.setViewPadding(R.id.widget_grid_frame, left, top, right, bottom);
            frame.removeAllViews(R.id.widget_grid_frame);
            frame.addView(R.id.widget_grid_frame, new RemoteViews(content));
            return frame;
        }
    }

    static Slot appWidgetSlot(Context context, AppWidgetManager manager, int id) {
        AppWidgetProviderInfo provider = manager.getAppWidgetInfo(id);
        return appWidgetSlot(context, manager, id, provider == null ? 0 : provider.initialLayout);
    }

    static Slot appWidgetSlot(Context context, AppWidgetManager manager, int id, int layout) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        Bundle options = manager.getAppWidgetOptions(id);
        float width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        float height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if ((width <= 0 || height <= 0) && Build.VERSION.SDK_INT >= 31) {
            List<SizeF> sizes = BundleCompat.getParcelableArrayList(options,
                    AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF.class);
            if (sizes != null && !sizes.isEmpty()) {
                SizeF size = sizes.get(0);
                for (SizeF candidate : sizes) {
                    if (landscape ? candidate.getWidth() > size.getWidth()
                            : candidate.getWidth() < size.getWidth()) size = candidate;
                }
                width = size.getWidth();
                height = size.getHeight();
            }
        }
        if (width <= 0 || height <= 0) return new Slot(1f, 0, 0, 0, 0);

        Size base = size(context, layout);
        float fit = Math.min(width / (float) base.getWidth(),
                height / (float) base.getHeight());
        float scale = Math.max(0.5f, Math.min(2.5f, fit));
        float density = context.getResources().getDisplayMetrics().density;
        int horizontalInset = Math.max(0, Math.round((width - base.getWidth() * scale) * density));
        int verticalInset = Math.max(0, Math.round((height - base.getHeight() * scale) * density));
        int left = horizontalInset / 2;
        int top = verticalInset / 2;
        return new Slot(scale, left, top, horizontalInset - left, verticalInset - top);
    }
}
