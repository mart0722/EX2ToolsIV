package com.ex2tools.iv.widgets;

import com.ex2tools.iv.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Один каталог описаний виджетов: ключ, подпись, layout и размер.
 *
 * Задача — убрать размазанные по коду switch'и. Когда появится новый дизайн,
 * здесь меняются только layout'ы и размеры, логика остаётся прежней.
 */
public final class WidgetCatalog {

    public static final class Entry {
        public final String key;
        public final int labelRes;
        public final int layoutRes;
        public final int widthDp;
        public final int heightDp;
        public final int defaultX;
        public final int defaultY;

        Entry(String key, int labelRes, int layoutRes,
              int widthDp, int heightDp, int defaultX, int defaultY) {
            this.key = key;
            this.labelRes = labelRes;
            this.layoutRes = layoutRes;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        add(WidgetsCommon.SIZE_CLOCK, R.string.widgets_size_clock,
                R.layout.widget_clock_overlay, 240, 120, 40, 40);
        add(WidgetsCommon.SIZE_SPEED, R.string.widgets_size_speed,
                R.layout.widget_speed, 240, 240, 40, 160);
        add(WidgetsCommon.SIZE_DRIVE_REGEN, R.string.widgets_size_drive_regen,
                R.layout.widget_drive_regen, 420, 220, 40, 40);
        add(WidgetsCommon.SIZE_DRIVE_MODE, R.string.widgets_size_drive_mode,
                R.layout.widget_drive_mode_vertical, 180, 240, 40, 420);
        add(WidgetsCommon.SIZE_REGEN, R.string.widgets_size_regen,
                R.layout.widget_regen_vertical, 180, 240, 240, 420);
        add(WidgetsCommon.SIZE_FAN, R.string.widgets_size_fan,
                R.layout.widget_fan_speed, 240, 150, 40, 40);
        add(WidgetsCommon.SIZE_TEMP, R.string.widgets_size_temp,
                R.layout.widget_temperature, 260, 150, 300, 40);
        add(WidgetsCommon.SIZE_VENT, R.string.widgets_size_vent,
                R.layout.widget_hvac_direction, 420, 180, 40, 280);
        add(WidgetsCommon.SIZE_VENT_FAN, R.string.widgets_size_vent_fan,
                R.layout.widget_hvac_direction_fan, 420, 260, 40, 520);
        add(WidgetsCommon.SIZE_HEATING, R.string.widgets_size_heating,
                R.layout.widget_heating_test, 420, 260, 480, 520);
        add(WidgetsCommon.SIZE_WINDSHIELD_HEAT, R.string.widget_name_windshield_heat,
                R.layout.widget_windshield_heat, 160, 100, 480, 420);
        add(WidgetsCommon.SIZE_BATTERY_RANGE, R.string.widgets_size_battery_range,
                R.layout.widget_battery_range, 300, 96, 40, 700);
        add(WidgetsCommon.SIZE_WIPERS, R.string.widget_name_wipers,
                R.layout.widget_wipers, 127, 91, 650, 420);
        add(WidgetsCommon.SIZE_POWER_OFF, R.string.widget_name_power_off,
                R.layout.widget_power_off, 127, 91, 820, 420);
        add(WidgetsCommon.SIZE_LOCK, R.string.widgets_size_lock,
                R.layout.widget_lock, 160, 160, 360, 700);
        add(WidgetsCommon.SIZE_APPS, R.string.widgets_size_apps,
                R.layout.widget_apps, 84, 84, 560, 700);
        add(WidgetsCommon.SIZE_CLIMATE, R.string.climate_overlay,
                R.layout.widget_climate_control, 300, 160, 800, 40);
    }

    private static void add(String key, int labelRes, int layoutRes,
                            int widthDp, int heightDp, int x, int y) {
        ENTRIES.put(key, new Entry(key, labelRes, layoutRes, widthDp, heightDp, x, y));
    }

    public static Entry get(String key) {
        return ENTRIES.get(key);
    }

    public static List<String> keys() {
        return new ArrayList<>(ENTRIES.keySet());
    }

    public static int layout(String key) {
        Entry entry = ENTRIES.get(key);
        return entry == null ? R.layout.widget_drive_regen : entry.layoutRes;
    }

    private WidgetCatalog() {
    }
}
