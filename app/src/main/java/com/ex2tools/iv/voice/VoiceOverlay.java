package com.ex2tools.iv.voice;

import android.content.Context;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.ex2tools.iv.R;

public final class VoiceOverlay {

    private final Context context;
    private final WindowManager windowManager;
    private View view;
    private TextView title;
    private TextView text;
    private VoiceWaveView wave;

    public VoiceOverlay(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean show() {
        if (view == null) {
            if (windowManager == null || !Settings.canDrawOverlays(context)) return false;
            view = LayoutInflater.from(context).inflate(R.layout.voice_overlay, null);
            title = view.findViewById(R.id.voiceOverlayTitle);
            text = view.findViewById(R.id.voiceOverlayText);
            wave = view.findViewById(R.id.voiceOverlayWave);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = dp(110);
            try {
                windowManager.addView(view, lp);
            } catch (Throwable t) {
                view = null;
                title = null;
                text = null;
                wave = null;
                return false;
            }
        }
        view.setVisibility(View.VISIBLE);
        if (wave != null) wave.start();
        return true;
    }

    public void hide() {
        if (view != null) view.setVisibility(View.GONE);
        if (wave != null) wave.stop();
    }

    public void dismiss() {
        hide();
    }

    public void destroy() {
        View v = view;
        view = null;
        title = null;
        text = null;
        if (wave != null) {
            wave.stop();
            wave = null;
        }
        if (v == null || windowManager == null) return;
        try {
            windowManager.removeView(v);
        } catch (Throwable ignored) {
        }
    }

    public void setTitle(CharSequence value) {
        if (title != null) title.setText(value);
    }

    public void setText(CharSequence value) {
        if (text != null) text.setText(value);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
