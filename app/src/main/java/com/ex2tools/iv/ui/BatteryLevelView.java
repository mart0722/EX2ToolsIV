package com.ex2tools.iv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.R;

/**
 * Glass-style traction battery with a liquid fill, semantic colors and an optional
 * pulsing charging bolt.
 */
public final class BatteryLevelView extends View {

    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF body = new RectF();
    private final RectF terminal = new RectF();
    private final Path bodyPath = new Path();
    private final Path fillPath = new Path();
    private final Path boltPath = new Path();

    private float shownLevel;
    private float targetLevel;
    private float wavePhase;
    private float pulse = 1f;
    private boolean charging;

    private ValueAnimator levelAnimator;
    private ValueAnimator waveAnimator;
    private ValueAnimator pulseAnimator;

    public BatteryLevelView(Context context) {
        this(context, null);
    }

    public BatteryLevelView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryLevelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        glassPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2f));
        fillPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.FILL);
        boltPaint.setStyle(Paint.Style.FILL);
        boltGlowPaint.setStyle(Paint.Style.FILL);
        setContentDescription(context.getString(R.string.overview_battery));
    }

    public void setLevel(int level) {
        int next = Math.max(0, Math.min(100, level));
        if (next == (int) targetLevel) return;
        targetLevel = next;
        if (levelAnimator != null) levelAnimator.cancel();
        levelAnimator = ValueAnimator.ofFloat(shownLevel, targetLevel);
        levelAnimator.setDuration(600L);
        levelAnimator.setInterpolator(new DecelerateInterpolator());
        levelAnimator.addUpdateListener(a -> {
            shownLevel = (float) a.getAnimatedValue();
            invalidate();
        });
        levelAnimator.start();
    }

    public void setCharging(boolean value) {
        if (charging == value) return;
        charging = value;
        if (charging) {
            if (isShown()) startAnimators();
        } else {
            stopAnimators();
            wavePhase = 0f;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (int) dp(120f);
        int desiredHeight = (int) dp(200f);
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = dp(8f);
        float terminalHeight = dp(9f);
        float terminalWidth = Math.max(dp(24f), w * 0.32f);
        float top = pad + terminalHeight + dp(5f);

        body.set(pad, top, w - pad, h - pad);
        bodyPath.reset();
        bodyPath.addRoundRect(body, dp(18f), dp(18f), Path.Direction.CW);

        float cx = body.centerX();
        terminal.set(cx - terminalWidth / 2f, pad, cx + terminalWidth / 2f,
                pad + terminalHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (body.isEmpty()) return;

        int stroke = ContextCompat.getColor(getContext(), R.color.flyme_label_level4);
        strokePaint.setColor(stroke);
        strokePaint.setAlpha(220);

        // Terminal
        glassPaint.setColor(0x33FFFFFF);
        canvas.drawRoundRect(terminal, dp(3f), dp(3f), glassPaint);
        canvas.drawRoundRect(terminal, dp(3f), dp(3f), strokePaint);

        // Glass body
        glassPaint.setColor(0x2EFFFFFF);
        canvas.drawRoundRect(body, dp(18f), dp(18f), glassPaint);

        // Liquid
        float levelHeight = body.height() * (shownLevel / 100f);
        if (levelHeight > 0f) {
            float top = body.bottom - levelHeight;
            fillPath.reset();
            fillPath.moveTo(body.left, top);
            int steps = 24;
            float amp = Math.min(dp(2.5f), levelHeight * 0.08f);
            for (int i = 0; i <= steps; i++) {
                float x = body.left + body.width() * i / steps;
                float y = top + (float) Math.sin(wavePhase + i * 0.55f) * amp;
                fillPath.lineTo(x, y);
            }
            fillPath.lineTo(body.right, body.bottom);
            fillPath.lineTo(body.left, body.bottom);
            fillPath.close();

            int[] colors = fillColors();
            fillPaint.setShader(new LinearGradient(body.left, top, body.right, body.bottom,
                    colors[0], colors[1], Shader.TileMode.CLAMP));

            canvas.save();
            canvas.clipPath(bodyPath);
            canvas.drawPath(fillPath, fillPaint);

            highlightPaint.setColor(0x33FFFFFF);
            RectF highlight = new RectF(
                    body.left + body.width() * 0.12f, top,
                    body.left + body.width() * 0.30f, body.bottom);
            canvas.drawRoundRect(highlight, dp(8f), dp(8f), highlightPaint);
            canvas.restore();
        }

        // Body outline on top of the liquid
        canvas.drawRoundRect(body, dp(18f), dp(18f), strokePaint);

        if (charging) drawBolt(canvas);
    }

    private int[] fillColors() {
        Context c = getContext();
        if (shownLevel < 20f) {
            return new int[]{0xFFFF8A8A, ContextCompat.getColor(c, R.color.danger)};
        }
        if (shownLevel >= 80f) {
            return new int[]{0xFF6FE0A5, ContextCompat.getColor(c, R.color.emerald)};
        }
        return new int[]{0xFF7CC4FF, ContextCompat.getColor(c, R.color.flyme_blue)};
    }

    private void drawBolt(Canvas canvas) {
        float cx = body.centerX();
        float cy = body.centerY();
        float h = body.height() * 0.42f;
        float w = body.width() * 0.42f;

        boltPath.reset();
        boltPath.moveTo(cx + w * 0.10f, cy - h * 0.50f);
        boltPath.lineTo(cx - w * 0.45f, cy + h * 0.05f);
        boltPath.lineTo(cx - w * 0.05f, cy + h * 0.05f);
        boltPath.lineTo(cx - w * 0.15f, cy + h * 0.50f);
        boltPath.lineTo(cx + w * 0.45f, cy - h * 0.10f);
        boltPath.lineTo(cx + w * 0.05f, cy - h * 0.10f);
        boltPath.close();

        canvas.save();
        canvas.scale(1.12f, 1.12f, cx, cy);
        boltGlowPaint.setColor(0x661368FB);
        boltGlowPaint.setAlpha((int) (0x66 * pulse));
        canvas.drawPath(boltPath, boltGlowPaint);
        canvas.restore();

        boltPaint.setColor(Color.WHITE);
        boltPaint.setAlpha((int) (255 * pulse));
        canvas.drawPath(boltPath, boltPaint);
    }

    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ValueAnimator.ofFloat(0.55f, 1f);
        pulseAnimator.setDuration(900L);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        pulse = 1f;
        invalidate();
    }

    private void startWave() {
        if (waveAnimator != null && waveAnimator.isRunning()) return;
        waveAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        waveAnimator.setDuration(2800L);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.addUpdateListener(a -> {
            wavePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        waveAnimator.start();
    }

    private void stopWave() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
    }

    private void startAnimators() {
        if (!isAttachedToWindow() || !isShown()) return;
        if (charging) {
            startWave();
            startPulse();
        } else {
            stopWave();
            wavePhase = 0f;
            invalidate();
        }
    }

    private void stopAnimators() {
        stopWave();
        stopPulse();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimators();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimators();
        if (levelAnimator != null) {
            levelAnimator.cancel();
            levelAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isShown()) startAnimators();
        else stopAnimators();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (isShown()) startAnimators();
        else stopAnimators();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
