package com.ex2tools.iv.widgets;

import android.content.Context;
import android.widget.RemoteViews;

public final class DriveRegenWidgetProvider extends BaseWidgetProvider {
    @Override
    protected RemoteViews build(Context c) {
        return WidgetsRenderer.driveRegen(c, null, null);
    }
}
