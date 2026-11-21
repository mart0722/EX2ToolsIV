package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

abstract class BaseWidgetProvider extends AppWidgetProvider {

    protected abstract RemoteViews build(Context context);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateSize(context, appWidgetManager, id, appWidgetManager.getAppWidgetOptions(id));
        }
        WidgetUpdateService.start(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle options) {
        updateSize(context, manager, appWidgetId, options);
        WidgetUpdateService.start(context);
    }

    private void updateSize(Context context, AppWidgetManager manager, int id, Bundle options) {
        int size = SizedWidgetViews.size(context, manager, id, options);
        manager.updateAppWidget(id, build(SizedWidgetViews.context(context, size)));
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdateService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetUpdateService.sync(context);
    }
}
