package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ClockWidgetProvider extends AppWidgetProvider {

    static final float GLASS_DATE_TEXT_SP = 13f;
    static final float LARGE_TIME_TEXT_SP = 72f;
    static final float LARGE_DATE_TEXT_SP = 20f;

    protected boolean isLarge() {
        return false;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids, false, isLarge());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle options) {
        update(context, manager, new int[]{appWidgetId}, false, isLarge());
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ClockWidgetProvider.class));
        if (ids != null && ids.length > 0) update(context, manager, ids, false);
        int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, LargeClockWidgetProvider.class));
        if (largeIds.length > 0) update(context, manager, largeIds, false, true);
        ClockDateWidgetProvider.refreshAll(context);
    }

    static void update(Context context, AppWidgetManager manager, int[] ids, boolean forceDate) {
        update(context, manager, ids, forceDate, false);
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids,
                               boolean forceDate, boolean large) {
        if (ids == null) return;
        int font = Prefs.clockFont(context);
        int color = color(context);
        boolean date = forceDate || Prefs.clockDate(context);
        int layout;
        if (date) {
            layout = font == 1
                    ? R.layout.widget_clock_condensed_date
                    : R.layout.widget_clock_classic_date;
        } else {
            layout = font == 1 ? R.layout.widget_clock_condensed : R.layout.widget_clock_classic;
        }
        String dateText = date ? dateText() : "";
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            int background = WidgetsCommon.bgDrawableResForPct(context);
            views.setInt(R.id.widget_bg_layer, "setBackgroundResource", background);
            views.setTextColor(R.id.clockTime, color);
            if (date) {
                views.setTextColor(R.id.clockDate, color);
                views.setTextViewText(R.id.clockDate, dateText);
            }
            WidgetGrid.Slot slot = WidgetGrid.appWidgetSlot(context, manager, id,
                    large ? R.layout.widget_clock_large : layout);
            if (WidgetAppearance.usesGlass(context) || large) {
                views.setTextViewTextSize(R.id.clockTime, TypedValue.COMPLEX_UNIT_SP,
                        (large ? LARGE_TIME_TEXT_SP : 42f) * slot.scale());
                if (date) {
                    views.setTextViewTextSize(R.id.clockDate, TypedValue.COMPLEX_UNIT_SP,
                            (large ? LARGE_DATE_TEXT_SP : GLASS_DATE_TEXT_SP) * slot.scale());
                }
            }
            manager.updateAppWidget(id, slot.wrap(context, views));
        }
    }

    static String dateText() {
        try {
            String value = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"))
                    .format(new Date());
            if (value.isEmpty()) return value;
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        } catch (Throwable t) {
            return "";
        }
    }

    private static int color(Context c) {
        int mode = Prefs.clockColor(c);
        if (mode == 1) return Color.WHITE;
        if (mode == 2) return Color.BLACK;
        return ContextCompat.getColor(c, R.color.clock_text);
    }
}
