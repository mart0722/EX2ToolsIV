package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import com.ex2tools.iv.Prefs;

public final class SpeedWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids);
        WidgetUpdateService.start(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        manager.updateAppWidget(appWidgetId,
                SpeedWidgetRenderer.build(context, appWidgetId, Prefs.speedCache(context)));
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdateService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetUpdateService.sync(context);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, SpeedWidgetProvider.class));
        update(context, manager, ids);
    }

    public static void updateCached(Context context, float speed) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, SpeedWidgetProvider.class));
        update(context, manager, ids, speed);
    }

    static void update(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids, Prefs.speedCache(context));
    }

    static void update(Context context, AppWidgetManager manager, int[] ids, float speed) {
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            manager.updateAppWidget(id, SpeedWidgetRenderer.build(context, id, speed));
        }
    }
}
