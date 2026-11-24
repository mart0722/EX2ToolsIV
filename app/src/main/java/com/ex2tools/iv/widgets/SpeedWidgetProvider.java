package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import com.ex2tools.iv.Prefs;

public class SpeedWidgetProvider extends AppWidgetProvider {

    private enum Format {
        TWO_BY_TWO(2, SpeedWidgetProvider.class),
        THREE_BY_THREE(3, ThreeByThree.class),
        FOUR_BY_FOUR(4, FourByFour.class);

        final int cells;
        private final Class<? extends SpeedWidgetProvider> provider;

        Format(int cells, Class<? extends SpeedWidgetProvider> provider) {
            this.cells = cells;
            this.provider = provider;
        }

        int[] widgetIds(Context context, AppWidgetManager manager) {
            return manager.getAppWidgetIds(new ComponentName(context, provider));
        }
    }

    private final Format format;

    public SpeedWidgetProvider() {
        this(Format.TWO_BY_TWO);
    }

    private SpeedWidgetProvider(Format format) {
        this.format = format;
    }

    public static final class ThreeByThree extends SpeedWidgetProvider {
        public ThreeByThree() {
            super(Format.THREE_BY_THREE);
        }
    }

    public static final class FourByFour extends SpeedWidgetProvider {
        public FourByFour() {
            super(Format.FOUR_BY_FOUR);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids, Prefs.speedCache(context), format);
        WidgetUpdateService.start(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        manager.updateAppWidget(appWidgetId,
                SpeedWidgetRenderer.build(context, manager, appWidgetId,
                        Prefs.speedCache(context), format.cells));
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
        updateAll(context, AppWidgetManager.getInstance(context), Prefs.speedCache(context));
    }

    public static void updateCached(Context context, float speed) {
        updateAll(context, AppWidgetManager.getInstance(context), speed);
    }

    static boolean hasWidgets(Context context, AppWidgetManager manager) {
        for (Format format : Format.values()) {
            if (format.widgetIds(context, manager).length > 0) return true;
        }
        return false;
    }

    static boolean updateAll(Context context, AppWidgetManager manager, float speed) {
        boolean updated = false;
        for (Format format : Format.values()) {
            updated |= update(context, manager, format.widgetIds(context, manager), speed, format);
        }
        return updated;
    }

    private static boolean update(Context context, AppWidgetManager manager, int[] ids, float speed,
                                  Format format) {
        for (int id : ids) {
            manager.updateAppWidget(id, SpeedWidgetRenderer.build(context, manager, id, speed, format.cells));
        }
        return ids.length > 0;
    }
}
