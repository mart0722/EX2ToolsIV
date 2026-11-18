package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class BatteryRangeWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews build(Context context) {
        Integer soc = WidgetsCommon.readSoc();
        float factory = WidgetsCommon.readRangeKm();
        float learned = com.ex2tools.iv.car.RangeModel.estimateKm(context);
        float display = Float.isNaN(learned) ? factory : learned;
        Float energy = soc == null ? null
                : soc * com.ex2tools.iv.car.CarProps.CAPACITY_KWH / 100f;
        return WidgetsRenderer.batteryRange(context,
                soc == null || soc < 0 ? null : soc,
                Float.isNaN(display) ? null : display,
                energy);
    }
}
