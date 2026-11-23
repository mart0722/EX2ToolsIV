package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.ex2tools.iv.Prefs;

abstract class BaseWidgetProvider extends AppWidgetProvider {

    protected abstract RemoteViews build(Context context);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
        WidgetUpdateService.start(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int id, Bundle options) {
        updateWidget(context, manager, id);
    }

    private void updateWidget(Context context, AppWidgetManager manager, int id) {
        WidgetGrid.Slot slot = WidgetGrid.appWidgetSlot(context, manager, id);
        RemoteViews views = build(WidgetIconSizes.appWidgetContext(context, id, slot.scale()));
        WidgetGrid.scaleContent(context, views, slot.scale());
        manager.updateAppWidget(id, slot.wrap(context, views));
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
