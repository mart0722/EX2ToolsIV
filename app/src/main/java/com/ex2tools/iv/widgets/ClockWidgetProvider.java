package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ClockWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ClockWidgetProvider.class));
        if (ids != null && ids.length > 0) update(context, manager, ids);
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null) return;
        int font = Prefs.clockFont(context);
        int color = color(context);
        boolean date = Prefs.clockDate(context);
        int layout;
        if (date) {
            layout = font == 1
                    ? R.layout.widget_clock_condensed_date
                    : R.layout.widget_clock_classic_date;
        } else {
            layout = font == 1 ? R.layout.widget_clock_condensed : R.layout.widget_clock_classic;
        }
        String dateText = dateText(context);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setTextColor(R.id.clockTime, color);
            if (date) {
                views.setTextColor(R.id.clockDate, color);
                views.setTextViewText(R.id.clockDate, dateText);
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static String dateText(Context c) {
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
