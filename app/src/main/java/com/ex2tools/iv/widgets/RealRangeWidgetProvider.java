package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import com.ex2tools.iv.car.RangeModel;

public final class RealRangeWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews build(Context context) {
        return WidgetsRenderer.realRange(context, RangeModel.estimateKm(context));
    }
}
