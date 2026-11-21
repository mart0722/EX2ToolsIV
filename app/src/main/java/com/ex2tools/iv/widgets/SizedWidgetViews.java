package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.RemoteViews;

final class SizedWidgetViews extends ContextWrapper {
    private static final int NORMAL = 8;
    private final int size;

    private SizedWidgetViews(Context context, int size) {
        super(context);
        this.size = size;
    }

    static Context context(Context context, int size) {
        return size == NORMAL ? context : new SizedWidgetViews(context, size);
    }

    static int size(Context context, AppWidgetManager manager, int id, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (width <= 0 || height <= 0) return NORMAL;

        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null || info.initialLayout == 0) return NORMAL;
        Resources resources = context.getResources();
        String name = resources.getResourceEntryName(info.initialLayout);
        int dimensions = resources.getIdentifier(name + "_base_size", "array", context.getPackageName());
        if (dimensions == 0) return NORMAL;
        int[] base = resources.getIntArray(dimensions);
        float scale = Math.min(width / (float) base[0], height / (float) base[1]);
        return Math.max(4, Math.min(32, (int) (scale * NORMAL)));
    }

    static float scale(Context context) {
        return context instanceof SizedWidgetViews sized ? sized.size / (float) NORMAL : 1f;
    }

    static RemoteViews create(Context context, int layout) {
        if (context instanceof SizedWidgetViews sized) {
            Resources resources = context.getResources();
            String name = resources.getResourceEntryName(layout) + "_scale_" + sized.size;
            int scaled = resources.getIdentifier(name, "layout", context.getPackageName());
            if (scaled != 0) layout = scaled;
        }
        return new RemoteViews(context.getPackageName(), layout);
    }
}
