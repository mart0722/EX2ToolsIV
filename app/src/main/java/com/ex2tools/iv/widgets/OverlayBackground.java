package com.ex2tools.iv.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/**
 * Theme-aware translucent card used by floating widgets. Colors are resolved
 * through the same light/dark context as the widget contents.
 */
final class OverlayBackground {

    private OverlayBackground() {
    }

    static Drawable create(Context c, String key) {
        int alpha = alpha(c, key);
        if (WidgetAppearance.usesGlass(c)) {
            return WidgetAppearance.glassCard(c, alpha, dp(c, 22), Math.max(1, dp(c, 1)));
        }
        int base = c.getColor(R.color.wg_card);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        d.setCornerRadius(dp(c, 22));
        d.setStroke(Math.max(1, dp(c, 1)), androidx.core.graphics.ColorUtils.setAlphaComponent(
                c.getColor(R.color.wg_stroke_100), alpha));
        return d;
    }

    static boolean isTransparent(Context c, String key) {
        return alpha(c, key) == 0;
    }

    private static int alpha(Context c, String key) {
        return Math.round(255f * Prefs.overlayAlphaPct(c, key) / 100f);
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
