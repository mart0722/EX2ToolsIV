package com.ex2tools.iv.ui;

import android.view.MotionEvent;
import android.view.View;

/** Shared press feedback and disabled state for Flyme-style dialog buttons. */
public final class FlymePress {

    private FlymePress() {
    }

    public static void bind(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.8f).setDuration(120L).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180L).start();
            }
            return false;
        });
    }

    public static void setEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }
}
