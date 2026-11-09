package com.ex2tools.iv.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class VoiceWaveView extends View {

    private static final int BARS = 5;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float phase;

    public VoiceWaveView(Context context) {
        this(context, null);
    }

    public VoiceWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
    }

    public void start() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(1600L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        phase = 0f;
        invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float barW = w / (BARS * 2.2f);
        float gap = (w - BARS * barW) / (BARS - 1);
        float base = h * 0.24f;
        float amp = h * 0.36f;
        float cy = h / 2f;
        for (int i = 0; i < BARS; i++) {
            float v = (float) ((Math.sin(phase + i * 0.9) + 1f) * 0.5f);
            float barH = base + amp * v;
            float left = i * (barW + gap);
            rect.set(left, cy - barH / 2f, left + barW, cy + barH / 2f);
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
        }
    }
}
