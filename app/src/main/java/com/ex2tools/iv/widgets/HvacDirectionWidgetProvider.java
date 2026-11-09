package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class HvacDirectionWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.vent(c, null, null);
    }
}
