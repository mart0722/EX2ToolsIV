package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import com.ex2tools.iv.Prefs;
import android.widget.RemoteViews;

abstract class BaseWidgetProvider extends AppWidgetProvider {

    protected abstract RemoteViews build(Context context);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, build(WidgetIconSizes.appWidgetContext(context, id)));
        }
        WidgetUpdateService.start(context);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) Prefs.clearWidgetIconSettings(context, WidgetIconSizes.appWidgetKey(id));
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
