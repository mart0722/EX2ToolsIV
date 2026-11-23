package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/**
 * Native port of the HTML speedometer. The same painter is used by the
 * floating overlay view and by the AppWidget bitmap renderer.
 */
final class SpeedGaugePainter {

    static final float MAX_SPEED = 160f;
    private static final int CELLS = 32;
    private static final int LABEL_EVERY = 4;
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private SpeedGaugePainter() {
    }

    static Bitmap render(Context c, int width, int height, float kmh,
                         boolean digital, int backgroundAlpha,
                         float tripKm, boolean showTrip) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        draw(c, new Canvas(bitmap), w, h, kmh, digital, backgroundAlpha, tripKm, showTrip);
        return bitmap;
    }

    static void draw(Context c, Canvas canvas, int width, int height, float kmh,
                     boolean digital, int backgroundAlpha,
                     float tripKm, boolean showTrip) {
        boolean chrome = Prefs.climateIconStyle(c).isChrome() && Prefs.speedColor(c) == 0;
        Palette p = chrome ? chromePalette(c) : palette(c);
        float size = Math.min(width, height);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float speed = Float.isNaN(kmh) ? 0f : Math.max(0f, Math.min(MAX_SPEED, kmh));

        if (backgroundAlpha > 0 && chrome) {
            float cellScale = size / (2f * WidgetGrid.UNIT_DP);
            GradientDrawable card = WidgetAppearance.glassCard(c, backgroundAlpha,
                    22f * cellScale, Math.max(1, Math.round(cellScale)));
            card.setBounds(0, 0, width, height);
            card.draw(canvas);
        } else if (backgroundAlpha > 0) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(p.surface);
            int alpha = backgroundAlpha;
            float radius = size * 0.075f;
            RectF panel = new RectF(0, 0, width, height);
            if (WidgetAppearance.usesGlass(c)) {
                // The gauge has its own foreground color setting, so use its
                // matching surface color for the tint and preserve contrast.
                int highlight = ColorUtils.blendARGB(p.surface, Color.WHITE, 0.22f);
                int top = ColorUtils.setAlphaComponent(highlight, alpha);
                int bottom = ColorUtils.setAlphaComponent(p.surface, Math.round(alpha * .82f));
                bg.setShader(new LinearGradient(0, 0, 0, height, top, bottom, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(panel, radius, radius, bg);
                if (Prefs.widgetCardBorder(c)) {
                    bg.setShader(null);
                    bg.setColor(ColorUtils.setAlphaComponent(Color.WHITE, Math.round(alpha * .42f)));
                    bg.setStyle(Paint.Style.STROKE);
                    float strokeWidth = Math.max(1f, size / 160f);
                    bg.setStrokeWidth(strokeWidth);
                    panel.inset(strokeWidth / 2f, strokeWidth / 2f);
                    canvas.drawRoundRect(panel, radius, radius, bg);
                }
            } else {
                bg.setAlpha(alpha);
                canvas.drawRoundRect(panel, radius, radius, bg);
            }
        }

        canvas.save();
        canvas.translate(cx, cy);
        if (digital) {
            drawDigital(canvas, size, speed, p, false);
        } else {
            float dialSize = size;
            if (chrome) {
                float inset = c.getResources().getDimension(R.dimen.wg_glass_inset)
                        / c.getResources().getDisplayMetrics().density;
                dialSize = size * (1f - inset / WidgetGrid.UNIT_DP);
            }
            drawAnalog(canvas, dialSize, speed, p, tripKm, showTrip);
        }
        canvas.restore();
    }

    private static void drawAnalog(Canvas canvas, float size, float speed,
                                   Palette p,
                                   float tripKm, boolean showTrip) {
        // The dial used to occupy only 72% of the overlay. At large sizes this
        // left a lot of empty space around the clock, so use more of the view.
        float radius = size * 0.43f;
        float cellAngle = SWEEP_ANGLE / CELLS;
        float gap = cellAngle * 0.18f;
        int active = (int) Math.ceil((speed / MAX_SPEED) * CELLS - 0.0001f);

        Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
        cell.setStyle(Paint.Style.STROKE);
        cell.setStrokeCap(Paint.Cap.BUTT);
        cell.setStrokeWidth(Math.max(5f, size * 0.028f));
        RectF arc = new RectF(-radius, -radius, radius, radius);

        cell.setColor(p.track);
        for (int i = 0; i < CELLS; i++) {
            float start = START_ANGLE + i * cellAngle + gap * 0.5f;
            canvas.drawArc(arc, start, cellAngle - gap, false, cell);
        }

        if (active > 0) {
            cell.setColor(p.accent);
            if (p.chrome) cell.setShader(p.metal(-radius, radius));
            else cell.setShadowLayer(size * 0.035f, 0f, 0f, p.accent);
            for (int i = 0; i < active; i++) {
                float start = START_ANGLE + i * cellAngle + gap * 0.5f;
                canvas.drawArc(arc, start, cellAngle - gap, false, cell);
            }
            cell.clearShadowLayer();
        }

        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setColor(p.label);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(Math.max(2f, size * 0.006f));
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setShader(p.metal(-radius, radius));

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(p.label);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setTextSize(size * 0.095f);

        float tickInner = radius - size * 0.046f;
        float tickOuter = radius - size * 0.022f;
        float labelRadius = radius - size * 0.082f;

        for (int k = 0; k <= CELLS; k += LABEL_EVERY) {
            double angle = Math.toRadians(START_ANGLE + (k / (float) CELLS) * SWEEP_ANGLE);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            canvas.drawLine(cos * tickInner, sin * tickInner,
                    cos * tickOuter, sin * tickOuter, tick);
            drawCentered(canvas, String.valueOf(Math.round((k / (float) CELLS) * MAX_SPEED)),
                    cos * labelRadius, sin * labelRadius, label);
        }

        float needleAngle = START_ANGLE + (speed / MAX_SPEED) * SWEEP_ANGLE;
        Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
        needle.setColor(p.accent);
        needle.setStyle(Paint.Style.STROKE);
        needle.setStrokeCap(Paint.Cap.ROUND);
        needle.setStrokeWidth(Math.max(4f, size * 0.011f));
        if (p.chrome) {
            float halfWidth = needle.getStrokeWidth() / 2f;
            needle.setShader(p.metal(-halfWidth, halfWidth));
        } else {
            needle.setShadowLayer(size * 0.025f, 0f, 0f, p.accent);
        }
        canvas.save();
        canvas.rotate(needleAngle);
        canvas.drawLine(-size * 0.035f, 0f, radius * 0.84f, 0f, needle);
        canvas.restore();
        needle.clearShadowLayer();

        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setColor(p.accent);
        float hubRadius = size * 0.028f;
        hub.setShader(p.metal(-hubRadius, hubRadius));
        canvas.drawCircle(0f, 0f, hubRadius, hub);
        hub.setShader(null);
        hub.setColor(p.surface);
        canvas.drawCircle(0f, 0f, size * 0.011f, hub);

        if (showTrip) {
            drawTrip(canvas, size, tripKm, p);
        } else {
            drawDigital(canvas, size, speed, p, true);
        }
    }

    private static void drawTrip(Canvas canvas, float size, float tripKm,
                                 Palette p) {
        String text = Float.isNaN(tripKm) ? "—"
                : (tripKm < 100f ? String.format(java.util.Locale.US, "%.1f", tripKm)
                : String.valueOf(Math.round(tripKm)));
        float y = size * 0.225f;

        Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        number.setColor(p.text);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        number.setTextSize(size * 0.132f);

        drawNumber(canvas, text, y, number, p);

        Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        unit.setColor(p.muted);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        unit.setTextSize(size * 0.045f);
        drawCentered(canvas, "км", 0f, size * 0.345f, unit);
    }

    private static void drawDigital(Canvas canvas, float size, float speed,
                                    Palette p, boolean analog) {
        float numberY = analog ? size * 0.225f : size * 0.03f;
        float unitY = analog ? size * 0.345f : size * 0.25f;

        Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        number.setColor(p.text);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        number.setTextSize(size * (analog ? 0.158f : 0.26f));

        String text = speed <= 0f ? "0" : String.valueOf(Math.round(speed));
        drawNumber(canvas, text, numberY, number, p);

        Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        unit.setColor(p.muted);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        unit.setTextSize(size * (analog ? 0.045f : 0.060f));
        drawCentered(canvas, "км/ч", 0f, unitY, unit);
    }

    private static void drawNumber(Canvas canvas, String value, float y, Paint paint, Palette palette) {
        float halfHeight = paint.getTextSize() * 0.4f;
        paint.setShader(palette.metal(y - halfHeight, y + halfHeight));
        drawCentered(canvas, value, 0f, y, paint);
    }

    private static void drawCentered(Canvas canvas, String text, float x, float y, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, x, y - (fm.ascent + fm.descent) * 0.5f, paint);
    }

    private static Palette chromePalette(Context c) {
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int ink = c.getColor(R.color.wg_text);
        return new Palette(ink, ink, ink, c.getColor(R.color.wg_text2),
                ColorUtils.setAlphaComponent(ink, 54), c.getColor(R.color.wg_card), true, night);
    }

    private static Palette palette(Context c) {
        switch (Prefs.speedColor(c)) {
            case 1:
                return new Palette(
                        Color.BLACK,
                        Color.BLACK,
                        Color.BLACK,
                        Color.rgb(95, 107, 122),
                        Color.argb(46, 18, 22, 28),
                        Color.rgb(242, 247, 252));
            case 2:
                return new Palette(
                        Color.rgb(19, 104, 251),
                        Color.WHITE,
                        Color.rgb(19, 104, 251),
                        Color.rgb(143, 160, 188),
                        Color.argb(56, 143, 160, 188),
                        Color.rgb(15, 23, 34));
            default:
                return new Palette(
                        Color.rgb(242, 247, 252),
                        Color.rgb(242, 247, 252),
                        Color.rgb(242, 247, 252),
                        Color.rgb(143, 160, 188),
                        Color.argb(46, 242, 247, 252),
                        Color.rgb(15, 23, 34));
        }
    }

    private static final class Palette {
        final int accent;
        final int label;
        final int text;
        final int muted;
        final int track;
        final int surface;
        final boolean chrome;
        final boolean night;

        Palette(int accent, int label, int text, int muted, int track, int surface) {
            this(accent, label, text, muted, track, surface, false, false);
        }

        Palette(int accent, int label, int text, int muted, int track, int surface,
                boolean chrome, boolean night) {
            this.accent = accent;
            this.label = label;
            this.text = text;
            this.muted = muted;
            this.track = track;
            this.surface = surface;
            this.chrome = chrome;
            this.night = night;
        }

        Shader metal(float top, float bottom) {
            return chrome ? WidgetAppearance.chromeGradient(night, top, bottom) : null;
        }
    }
}
