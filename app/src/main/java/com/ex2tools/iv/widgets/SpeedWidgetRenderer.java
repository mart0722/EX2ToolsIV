package com.ex2tools.iv.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

final class SpeedWidgetRenderer {

    // RemoteViews is sent through Binder. A 512x512 ARGB bitmap is already
    // 1 MiB before Parcel overhead and exceeds the Binder transaction limit on
    // the EX2, so the launcher shows "Problem loading widget". 320x320 keeps
    // the widget sharp at its 2x2 size while staying safely below the limit.
    private static final int BITMAP_SIZE = 320;

    private SpeedWidgetRenderer() {
    }

    static RemoteViews build(Context c, int appWidgetId, float kmh) {
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_speed);
        views.setViewVisibility(R.id.speed_digital, View.GONE);
        views.setViewVisibility(R.id.speed_gauge, View.VISIBLE);

        boolean digital = Prefs.speedMode(c) == 0;
        boolean background = Prefs.speedBackground(c);
        float trip = WidgetsCommon.readTripKm(c);
        boolean showTrip = Prefs.speedShowTrip(c);
        Bitmap bitmap = SpeedGaugePainter.render(c, BITMAP_SIZE, BITMAP_SIZE,
                kmh, digital, background, trip, showTrip && !digital);
        views.setImageViewBitmap(R.id.speed_gauge, bitmap);

        // Тап по пробегу внизу — то же подтверждение сброса, что и в статус-баре.
        boolean tripTappable = showTrip && !digital;
        views.setViewVisibility(R.id.speed_trip_tap, tripTappable ? View.VISIBLE : View.GONE);
        if (tripTappable) {
            android.content.Intent tap = new android.content.Intent(c, WidgetUpdateService.class)
                    .setAction(WidgetsCommon.TRIP_RESET_DIALOG);
            android.app.PendingIntent pending = android.app.PendingIntent.getService(
                    c, 0x74726970, tap,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.speed_trip_tap, pending);
        }
        return views;
    }
}
