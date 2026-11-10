package com.ex2tools.iv.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.ex2tools.iv.Prefs;

/**
 * Native port of the HTML speedometer. The same painter is used by the
 * floating overlay view and by the AppWidget bitmap renderer.
 */
final class SpeedGaugePainter {

    static final float MAX_SPEED = 150f;
    private static final int CELLS = 30;
    private static final int LABEL_EVERY = 6;
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private SpeedGaugePainter() {
    }

    static Bitmap render(Context c, int width, int height, float kmh,
                         boolean digital, boolean showBackground) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        draw(c, new Canvas(bitmap), w, h, kmh, digital, showBackground);
        return bitmap;
    }

    static void draw(Context c, Canvas canvas, int width, int height, float kmh,
                     boolean digital, boolean showBackground) {
        Palette p = palette(c);
        float size = Math.min(width, height);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float speed = Float.isNaN(kmh) ? 0f : Math.max(0f, Math.min(MAX_SPEED, kmh));

        if (showBackground) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(p.surface);
            bg.setAlpha(backgroundAlpha(c));
            float radius = size * 0.075f;
            canvas.drawRoundRect(new RectF(0, 0, width, height), radius, radius, bg);
        }

        canvas.save();
        canvas.translate(cx, cy);
        if (digital) {
            drawDigital(canvas, size, speed, p, showBackground, false);
        } else {
            drawAnalog(canvas, size, speed, p, showBackground);
        }
        canvas.restore();
    }

    private static void drawAnalog(Canvas canvas, float size, float speed,
                                   Palette p, boolean maskText) {
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
            cell.setShadowLayer(size * 0.035f, 0f, 0f, p.accent);
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

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(p.label);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setTextSize(size * 0.042f);

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
        needle.setShadowLayer(size * 0.025f, 0f, 0f, p.accent);
        canvas.save();
        canvas.rotate(needleAngle);
        canvas.drawLine(-size * 0.035f, 0f, radius * 0.84f, 0f, needle);
        canvas.restore();
        needle.clearShadowLayer();

        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setColor(p.accent);
        canvas.drawCircle(0f, 0f, size * 0.028f, hub);
        hub.setColor(p.surface);
        canvas.drawCircle(0f, 0f, size * 0.011f, hub);

        drawDigital(canvas, size, speed, p, maskText, true);
    }

    private static void drawDigital(Canvas canvas, float size, float speed,
                                    Palette p, boolean maskText, boolean analog) {
        float numberY = analog ? size * 0.225f : size * 0.03f;
        float unitY = analog ? size * 0.345f : size * 0.25f;

        Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        number.setColor(p.text);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        number.setTextSize(size * (analog ? 0.158f : 0.26f));

        Paint stroke = null;
        if (maskText) {
            stroke = new Paint(number);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(size * (analog ? 0.020f : 0.026f));
            stroke.setColor(p.surface);
            stroke.setAlpha(238);
        }

        String text = speed <= 0f ? "0" : String.valueOf(Math.round(speed));
        if (stroke != null) drawCentered(canvas, text, 0f, numberY, stroke);
        drawCentered(canvas, text, 0f, numberY, number);

        Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        unit.setColor(p.muted);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        unit.setTextSize(size * (analog ? 0.045f : 0.060f));
        if (maskText) {
            unit.setStyle(Paint.Style.STROKE);
            unit.setStrokeWidth(size * 0.012f);
            unit.setColor(p.surface);
            unit.setAlpha(238);
            drawCentered(canvas, "км/ч", 0f, unitY, unit);
            unit.setStyle(Paint.Style.FILL);
            unit.setColor(p.muted);
        }
        drawCentered(canvas, "км/ч", 0f, unitY, unit);
    }

    private static void drawCentered(Canvas canvas, String text, float x, float y, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, x, y - (fm.ascent + fm.descent) * 0.5f, paint);
    }

    private static int backgroundAlpha(Context c) {
        float per = Prefs.overlayAlphaPct(c, WidgetsCommon.SIZE_SPEED) / 100f;
        float global = Prefs.widgetBgOpacity(c) / 100f;
        return Math.round(255f * Math.min(0.85f, per * global));
    }

    static Palette palette(Context c) {
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
                        Color.WHITE,
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

    static final class Palette {
        final int accent;
        final int label;
        final int text;
        final int muted;
        final int track;
        final int surface;

        Palette(int accent, int label, int text, int muted, int track, int surface) {
            this.accent = accent;
            this.label = label;
            this.text = text;
            this.muted = muted;
            this.track = track;
            this.surface = surface;
        }
    }
}
