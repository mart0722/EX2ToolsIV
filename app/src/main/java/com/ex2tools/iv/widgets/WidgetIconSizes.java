package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.ContextWrapper;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

public final class WidgetIconSizes {
    private WidgetIconSizes() {}

    public static boolean supportsLayout(int layout) {
        return layout == R.layout.widget_hvac_direction
                || layout == R.layout.widget_hvac_direction_vertical
                || layout == R.layout.widget_hvac_direction_fan
                || layout == R.layout.widget_heating_test
                || layout == R.layout.widget_windshield_heat
                || layout == R.layout.widget_wipers
                || layout == R.layout.widget_power_off;
    }

    public static boolean supportsOverlay(String key) {
        return supportsLayout(WidgetCatalog.layout(key));
    }

    public static String appWidgetKey(int id) {
        return "app_widget_" + id;
    }

    static Context appWidgetContext(Context context, int id) {
        return new IconSizeContext(context, appWidgetKey(id));
    }

    static int appWidgetSize(Context context) {
        return Prefs.climateIconSizePct(context, appWidgetKey(context));
    }

    static boolean appWidgetLabels(Context context) {
        return Prefs.climateIconLabels(context, appWidgetKey(context));
    }

    private static String appWidgetKey(Context context) {
        return context instanceof IconSizeContext sized ? sized.key : "app_widget_default";
    }

    private static final class IconSizeContext extends ContextWrapper {
        final String key;

        IconSizeContext(Context context, String key) {
            super(context);
            this.key = key;
        }
    }
}
