package com.ex2tools.iv.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/** Surface styles shared by home-screen widgets and floating widgets. */
final class WidgetAppearance {
    private WidgetAppearance() {}

    static boolean usesGlass(Context c) {
        return Prefs.climateIconStyle(c) != Prefs.ClimateIconStyle.STANDARD;
    }

    static int glassCardResource(int opacity) {
        return switch (opacity) {
            case 0 -> R.drawable.bg_wg_card_000;
            case 20 -> R.drawable.bg_wg_glass_card_020;
            case 40 -> R.drawable.bg_wg_glass_card_040;
            case 60 -> R.drawable.bg_wg_glass_card_060;
            case 80 -> R.drawable.bg_wg_glass_card_080;
            default -> R.drawable.bg_wg_glass_card_100;
        };
    }

    static GradientDrawable glassCard(Context c, int alpha, float radius, int strokeWidth) {
        int top = scaledAlpha(c.getColor(R.color.wg_glass_top_100), alpha);
        int bottom = scaledAlpha(c.getColor(R.color.wg_glass_bottom_100), alpha);
        int edge = scaledAlpha(c.getColor(R.color.wg_glass_edge_100), alpha);
        GradientDrawable card = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        card.setCornerRadius(radius);
        card.setStroke(strokeWidth, edge);
        return card;
    }

    private static int scaledAlpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * alpha / 255f));
    }

    static int controlBackground(Context c, boolean transparent, int standard) {
        if (!usesGlass(c)) return standard;
        return transparent ? android.R.color.transparent : R.drawable.bg_wg_climate_glass;
    }

    /** Accent indices match the drive and regeneration controls: blue, green, red. */
    static int chipBackground(Context c, boolean transparent, boolean active, int accent) {
        if (!usesGlass(c)) {
            if (!active) return R.drawable.bg_wg_chip;
            return switch (accent) {
                case 1 -> R.drawable.bg_wg_chip_on_green;
                case 2 -> R.drawable.bg_wg_chip_on_red;
                default -> R.drawable.bg_wg_chip_on;
            };
        }
        if (transparent) return android.R.color.transparent;
        if (!active) return R.drawable.bg_wg_climate_glass;
        if (Prefs.widgetAccentBoost(c)) {
            return switch (accent) {
                case 1 -> R.drawable.bg_wg_glass_accent_green;
                case 2 -> R.drawable.bg_wg_glass_accent_red;
                default -> R.drawable.bg_wg_glass_accent_air;
            };
        }
        return switch (accent) {
            case 1 -> R.drawable.bg_wg_glass_green;
            case 2 -> R.drawable.bg_wg_glass_red;
            default -> R.drawable.bg_wg_climate_air;
        };
    }

    static int chipTextColor(Context c, boolean active, int accent) {
        if (!active || !usesGlass(c)) return c.getColor(R.color.wg_text);
        return c.getColor(switch (accent) {
            case 1 -> R.color.wg_glass_green_ink;
            case 2 -> R.color.wg_glass_red_ink;
            default -> R.color.wg_climate_blue;
        });
    }
}
