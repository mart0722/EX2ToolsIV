package com.ex2tools.iv.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

final class SpeedGaugeRenderer {

    private static final float MAX_KMH = 160f;
    private static final float START_ANGLE = 135f;
    private static final float SWEEP = 270f;

    private SpeedGaugeRenderer() {
    }

    static Bitmap create(int size, float kmh, int color) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, size), Math.max(1, size),
                Bitmap.Config.ARGB_8888);
        draw(new Canvas(bitmap), size, size, kmh, color);
        return bitmap;
    }

    private static void draw(Canvas canvas, int width, int height, float kmh, int color) {
        float cx = width * 0.5f;
        float cy = height * 0.52f;
        float radius = Math.min(width, height) * 0.4f;
        int muted = (color & 0x00FFFFFF) | 0x66000000;

        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(Math.max(2f, radius * 0.035f));
        arc.setColor(muted);
        canvas.drawCircle(cx, cy, radius, arc);

        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(color);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setTextSize(radius * 0.16f);

        for (int value = 0; value <= (int) MAX_KMH; value += 5) {
            float angle = speedToAngle(value);
            double rad = Math.toRadians(angle);
            boolean major = value % 20 == 0;
            float len = radius * (major ? 0.14f : 0.07f);
            tick.setColor(major ? color : muted);
            tick.setStrokeWidth(Math.max(1.5f, radius * (major ? 0.028f : 0.014f)));

            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float x1 = cx + (radius - len) * cos;
            float y1 = cy + (radius - len) * sin;
            float x2 = cx + radius * cos;
            float y2 = cy + radius * sin;
            canvas.drawLine(x1, y1, x2, y2, tick);

            if (major) {
                float tx = cx + (radius - len - radius * 0.16f) * cos;
                float ty = cy + (radius - len - radius * 0.16f) * sin;
                String text = String.valueOf(value);
                Rect bounds = new Rect();
                label.getTextBounds(text, 0, text.length(), bounds);
                canvas.drawText(text, tx, ty + bounds.height() * 0.35f, label);
            }
        }

        float angle = speedToAngle(Float.isNaN(kmh) ? 0f : kmh);
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setColor(muted);
        canvas.drawCircle(cx, cy, radius * 0.07f, hub);

        Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
        needle.setColor(color);
        needle.setStrokeWidth(Math.max(3f, radius * 0.032f));
        needle.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx, cy, cx + radius * 0.76f * cos, cy + radius * 0.76f * sin, needle);

        hub.setColor(color);
        canvas.drawCircle(cx, cy, radius * 0.032f, hub);

        Paint speed = new Paint(Paint.ANTI_ALIAS_FLAG);
        speed.setColor(color);
        speed.setTextAlign(Paint.Align.CENTER);
        speed.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        speed.setTextSize(radius * 0.36f);
        String value = Float.isNaN(kmh) ? "--" : String.valueOf(Math.round(kmh));
        canvas.drawText(value, cx, cy + radius * 0.52f, speed);

        Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        unit.setColor(muted);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTextSize(radius * 0.13f);
        canvas.drawText("км/ч", cx, cy + radius * 0.68f, unit);
    }

    private static float speedToAngle(float kmh) {
        float clamped = Math.max(0f, Math.min(MAX_KMH, kmh));
        return START_ANGLE + (clamped / MAX_KMH) * SWEEP;
    }
}
