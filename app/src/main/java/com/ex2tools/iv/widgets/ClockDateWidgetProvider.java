package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;

/** Часы с датой для штатного лаунчера: дата включена всегда. */
public final class ClockDateWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        ClockWidgetProvider.update(context, manager, ids, true);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, ClockDateWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            ClockWidgetProvider.update(context, manager, ids, true);
        }
    }
}
