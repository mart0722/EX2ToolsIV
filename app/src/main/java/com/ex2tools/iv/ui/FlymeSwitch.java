package com.ex2tools.iv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.R;

public final class FlymeSwitch extends CompoundButton {

    private final float density;
    private final float trackW;
    private final float trackH;
    private final float thumbW;
    private final float thumbH;
    private final float padding;
    private final float corner;
    private final float thumbCorner;
    private final float travel;
    private final int trackChecked;
    private final int trackUnchecked;
    private final int thumbColor;
    private final int thumbStroke;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();
    private final Path thumbPath = new Path();
    private ValueAnimator thumbAnimator;
    private ValueAnimator colorAnimator;
    private float thumbOffset;
    private int currentTrack;

    public FlymeSwitch(Context context) {
        this(context, null);
    }

    public FlymeSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlymeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        trackW = 72f * density;
        trackH = 38f * density;
        thumbW = 30f * density;
        thumbH = 30f * density;
        padding = 4f * density;
        corner = 6f * density;
        thumbCorner = 4f * density;
        travel = trackW - padding * 2f - thumbW;
        trackChecked = ContextCompat.getColor(context, R.color.flyme_blue);
        trackUnchecked = ContextCompat.getColor(context, R.color.flyme_fill_b2);
        thumbColor = ContextCompat.getColor(context, R.color.flyme_white);
        thumbStroke = ContextCompat.getColor(context, R.color.flyme_label_level5);
        trackPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(thumbColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(density);
        strokePaint.setColor(thumbStroke);
        currentTrack = trackUnchecked;
        thumbOffset = isChecked() ? travel : 0f;
        setButtonDrawable(null);
        setClickable(true);
        setFocusable(true);
        setMinimumWidth((int) trackW);
        setMinimumHeight((int) trackH);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize((int) trackW, widthMeasureSpec),
                resolveSize((int) trackH, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = (getWidth() - trackW) / 2f;
        float top = (getHeight() - trackH) / 2f;
        trackRect.set(left, top, left + trackW, top + trackH);
        trackPaint.setColor(currentTrack);
        canvas.drawRoundRect(trackRect, corner, corner, trackPaint);

        float thumbLeft = left + padding + thumbOffset;
        float thumbTop = top + padding;
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbW, thumbTop + thumbH);
        thumbPath.reset();
        thumbPath.addRoundRect(thumbRect, thumbCorner, thumbCorner, Path.Direction.CW);
        canvas.drawPath(thumbPath, thumbPaint);
        canvas.drawPath(thumbPath, strokePaint);
    }

    @Override
    public void setChecked(boolean checked) {
        boolean was = isChecked();
        super.setChecked(checked);
        if (was == checked) {
            syncVisual(checked);
            return;
        }
        if (!isAttachedToWindow() || !isLaidOut()) {
            syncVisual(checked);
            return;
        }
        animateThumb(checked);
        animateTrack(checked);
        animate().scaleX(0.97f).scaleY(0.97f).alpha(0.85f).setDuration(90L)
                .withEndAction(() -> animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(120L).start()).start();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (thumbAnimator != null) thumbAnimator.cancel();
        if (colorAnimator != null) colorAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private void syncVisual(boolean checked) {
        thumbOffset = checked ? travel : 0f;
        currentTrack = checked ? trackChecked : trackUnchecked;
        invalidate();
    }

    private void animateThumb(boolean checked) {
        if (thumbAnimator != null) thumbAnimator.cancel();
        thumbAnimator = ValueAnimator.ofFloat(thumbOffset, checked ? travel : 0f);
        thumbAnimator.setDuration(260L);
        thumbAnimator.setInterpolator(new DecelerateInterpolator(1.3f));
        thumbAnimator.addUpdateListener(a -> {
            thumbOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        thumbAnimator.start();
    }

    private void animateTrack(boolean checked) {
        if (colorAnimator != null) colorAnimator.cancel();
        final int from = currentTrack;
        final int to = checked ? trackChecked : trackUnchecked;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(180L);
        colorAnimator.addUpdateListener(a -> {
            currentTrack = blend(from, to, a.getAnimatedFraction());
            invalidate();
        });
        colorAnimator.start();
    }

    private static int blend(int a, int b, float f) {
        float inv = 1f - f;
        return android.graphics.Color.argb(
                (int) (android.graphics.Color.alpha(a) * inv + android.graphics.Color.alpha(b) * f),
                (int) (android.graphics.Color.red(a) * inv + android.graphics.Color.red(b) * f),
                (int) (android.graphics.Color.green(a) * inv + android.graphics.Color.green(b) * f),
                (int) (android.graphics.Color.blue(a) * inv + android.graphics.Color.blue(b) * f));
    }
}
