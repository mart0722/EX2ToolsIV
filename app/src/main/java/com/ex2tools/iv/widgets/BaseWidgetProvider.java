package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

abstract class BaseWidgetProvider extends AppWidgetProvider {

    protected abstract RemoteViews build(Context context);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = build(context);
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
        WidgetUpdateService.start(context);
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
