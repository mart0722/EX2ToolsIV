package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import com.ex2tools.iv.R;

public final class FanSpeedWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.fan(c, null, R.layout.widget_fan_speed);
    }
}
