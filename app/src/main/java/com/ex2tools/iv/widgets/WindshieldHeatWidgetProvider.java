package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class WindshieldHeatWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context context) {
        return WidgetsRenderer.windshieldHeat(context, WidgetsCommon.readWindshieldHeat());
    }
}
