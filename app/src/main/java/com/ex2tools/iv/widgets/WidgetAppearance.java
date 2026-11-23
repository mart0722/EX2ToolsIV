package com.ex2tools.iv.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/** Surface styles shared by home-screen widgets and floating widgets. */
final class WidgetAppearance {
    private static final int[] STEEL = {0xFF24384C, 0xFF60788F, 0xFF324A60, 0xFF30495F};
    private static final int[] TITANIUM = {0xFFA6BED5, 0xFFF7FAFF, 0xFF8AA4C0, 0xFFCAD8E9};
    private static final float[] STEEL_STOPS = {0f, .27f, .47f, 1f};
    private static final float[] TITANIUM_STOPS = {0f, .27f, .52f, 1f};

    private WidgetAppearance() {}

    static boolean usesGlass(Context c) {
        return Prefs.climateIconStyle(c) != Prefs.ClimateIconStyle.STANDARD;
    }

    static LinearGradient chromeGradient(boolean night, float top, float bottom) {
        return new LinearGradient(0, top, 0, bottom, night ? TITANIUM : STEEL,
                night ? TITANIUM_STOPS : STEEL_STOPS, Shader.TileMode.CLAMP);
    }

    static int cardBackground(Context c, int resource) {
        return Prefs.widgetCardBorder(c) ? resource : borderless(resource);
    }

    static int buttonBackground(Context c, int resource) {
        return Prefs.widgetButtonBorder(c) ? resource : borderless(resource);
    }

    private static final SparseIntArray BORDERLESS = new SparseIntArray();

    static {
        BORDERLESS.put(R.drawable.bg_wg_card_020, R.drawable.bg_wg_card_020_borderless);
        BORDERLESS.put(R.drawable.bg_wg_card_040, R.drawable.bg_wg_card_040_borderless);
        BORDERLESS.put(R.drawable.bg_wg_card_060, R.drawable.bg_wg_card_060_borderless);
        BORDERLESS.put(R.drawable.bg_wg_card_080, R.drawable.bg_wg_card_080_borderless);
        BORDERLESS.put(R.drawable.bg_wg_card_100, R.drawable.bg_wg_card_100_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on, R.drawable.bg_wg_chip_on_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_green, R.drawable.bg_wg_chip_on_green_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_orange, R.drawable.bg_wg_chip_on_orange_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_red, R.drawable.bg_wg_chip_on_red_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_pending, R.drawable.bg_wg_chip_pending_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_boost, R.drawable.bg_wg_chip_on_boost_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_green_boost, R.drawable.bg_wg_chip_on_green_boost_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_orange_boost, R.drawable.bg_wg_chip_on_orange_boost_borderless);
        BORDERLESS.put(R.drawable.bg_wg_chip_on_red_boost, R.drawable.bg_wg_chip_on_red_boost_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_air, R.drawable.bg_wg_climate_air_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_air_high, R.drawable.bg_wg_climate_air_high_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_air_medium, R.drawable.bg_wg_climate_air_medium_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_glass, R.drawable.bg_wg_climate_glass_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_heat, R.drawable.bg_wg_climate_heat_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_pending, R.drawable.bg_wg_climate_pending_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_warm_air, R.drawable.bg_wg_climate_warm_air_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_warm_air_high, R.drawable.bg_wg_climate_warm_air_high_borderless);
        BORDERLESS.put(R.drawable.bg_wg_climate_warm_air_medium, R.drawable.bg_wg_climate_warm_air_medium_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_air, R.drawable.bg_wg_glass_accent_air_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_air_high, R.drawable.bg_wg_glass_accent_air_high_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_air_medium, R.drawable.bg_wg_glass_accent_air_medium_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_green, R.drawable.bg_wg_glass_accent_green_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_heat, R.drawable.bg_wg_glass_accent_heat_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_red, R.drawable.bg_wg_glass_accent_red_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_warm_air, R.drawable.bg_wg_glass_accent_warm_air_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_warm_air_high, R.drawable.bg_wg_glass_accent_warm_air_high_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_accent_warm_air_medium, R.drawable.bg_wg_glass_accent_warm_air_medium_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_card_020, R.drawable.bg_wg_glass_card_020_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_card_040, R.drawable.bg_wg_glass_card_040_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_card_060, R.drawable.bg_wg_glass_card_060_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_card_080, R.drawable.bg_wg_glass_card_080_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_card_100, R.drawable.bg_wg_glass_card_100_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_green, R.drawable.bg_wg_glass_green_borderless);
        BORDERLESS.put(R.drawable.bg_wg_glass_red, R.drawable.bg_wg_glass_red_borderless);
    }

    private static int borderless(int resource) {
        return BORDERLESS.get(resource, resource);
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
        if (Prefs.widgetCardBorder(c)) card.setStroke(strokeWidth, edge);
        return card;
    }

    private static int scaledAlpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * alpha / 255f));
    }

    static int controlBackground(Context c, boolean transparent, int standard) {
        int background = !usesGlass(c) ? standard
                : transparent ? android.R.color.transparent : R.drawable.bg_wg_climate_glass;
        return buttonBackground(c, background);
    }

    /** Accent indices match the drive and regeneration controls: blue, green, red. */
    static int chipBackground(Context c, boolean transparent, boolean active, int accent) {
        return buttonBackground(c, chipSurface(c, transparent, active, accent));
    }

    private static int chipSurface(Context c, boolean transparent, boolean active, int accent) {
        if (!usesGlass(c)) {
            if (!active) return R.drawable.bg_wg_chip;
            boolean boost = Prefs.widgetAccentBoost(c);
            return switch (accent) {
                case 1 -> boost ? R.drawable.bg_wg_chip_on_green_boost
                        : R.drawable.bg_wg_chip_on_green;
                case 2 -> boost ? R.drawable.bg_wg_chip_on_red_boost
                        : R.drawable.bg_wg_chip_on_red;
                default -> boost ? R.drawable.bg_wg_chip_on_boost
                        : R.drawable.bg_wg_chip_on;
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
