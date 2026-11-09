package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class HeatingTestWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.heating(c, null, null, null, null, null, null);
    }
}
