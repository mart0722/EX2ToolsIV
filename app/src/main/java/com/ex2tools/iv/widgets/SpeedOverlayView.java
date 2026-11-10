package com.ex2tools.iv.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import com.ex2tools.iv.Prefs;

/**
 * Native overlay implementation of the speedometer. It animates the needle
 * and the digital value independently from the service poll rate.
 */
final class SpeedOverlayView extends View {

    private float targetSpeed;
    private float shownSpeed;
    private long lastFrame;
    private boolean initialized;
    private boolean digital;
    private boolean showBackground;

    SpeedOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    View view() {
        return this;
    }

    void update(Context c, float kmh) {
        float value = Float.isNaN(kmh) ? 0f : kmh;
        targetSpeed = Math.max(0f, Math.min(SpeedGaugePainter.MAX_SPEED, value));
        digital = Prefs.speedMode(c) == 0;
        showBackground = Prefs.speedBackground(c);
        if (!initialized) {
            shownSpeed = targetSpeed;
            initialized = true;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float dt = Math.min(0.12f, (now - lastFrame) / 1000f);
        lastFrame = now;

        if (Math.abs(targetSpeed - shownSpeed) > 0.05f) {
            float factor = Math.min(1f, dt * 9f);
            shownSpeed += (targetSpeed - shownSpeed) * factor;
            postInvalidateOnAnimation();
        } else {
            shownSpeed = targetSpeed;
        }

        SpeedGaugePainter.draw(getContext(), canvas, getWidth(), getHeight(),
                shownSpeed, digital, showBackground);
    }
}
