package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import com.ex2tools.iv.car.Telemetry;

public final class BatteryRangeWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews build(Context context) {
        int soc = Telemetry.soc(context);
        float range = Telemetry.rangeKm(context);
        return WidgetsRenderer.batteryRange(context,
                soc < 0 ? null : soc,
                Float.isNaN(range) ? null : range);
    }
}
