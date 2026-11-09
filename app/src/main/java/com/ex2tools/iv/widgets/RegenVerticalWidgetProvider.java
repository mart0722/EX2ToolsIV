package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class RegenVerticalWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.regenVertical(c, null);
    }
}
