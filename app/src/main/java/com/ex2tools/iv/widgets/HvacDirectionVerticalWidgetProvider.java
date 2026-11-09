package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class HvacDirectionVerticalWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.ventVertical(c, null, null);
    }
}
