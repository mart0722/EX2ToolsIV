package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import com.ex2tools.iv.R;
import com.ex2tools.iv.car.Telemetry;

public final class OutsideTemperatureWideWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context context) {
        return WidgetsRenderer.outsideTemperature(context, Telemetry.outsideTemp(context),
                R.layout.widget_outside_temp_wide);
    }
}
