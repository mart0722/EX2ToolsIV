package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class PowerOffWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews build(Context context) {
        return WidgetsRenderer.powerOff(context);
    }
}
