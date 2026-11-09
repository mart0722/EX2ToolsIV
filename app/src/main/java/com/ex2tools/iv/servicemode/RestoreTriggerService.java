package com.ex2tools.iv.servicemode;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import java.util.ArrayList;
import java.util.List;

public final class RestoreTriggerService extends Service {

    private static final String EXTRA_TEST = "test";
    public static final String ACTION_SHOW_CORNERS = "com.ex2tools.iv.action.SHOW_CORNERS";
    private static final int SIZE_DP = 44;
    private static final long HIGHLIGHT_MS = 5000L;
    private static volatile boolean sRunning;

    public static boolean isRunning() {
        return sRunning;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private final List<View> overlays = new ArrayList<>();
    private boolean testMode;
    private long firstTapMs;
    private int taps;
    private Runnable highlightEnd;

    public static void start(Context context, boolean test) {
        Intent i = new Intent(context, RestoreTriggerService.class);
        i.putExtra(EXTRA_TEST, test);
        try {
            ContextCompat.startForegroundService(context, i);
        } catch (Throwable t) {
            LogStore.add(context, "start trigger failed: " + t);
        }
    }

    public static void showCorners(Context context) {
        try {
            Intent i = new Intent(context, RestoreTriggerService.class);
            i.setAction(ACTION_SHOW_CORNERS);
            ContextCompat.startForegroundService(context, i);
        } catch (Throwable t) {
            LogStore.add(context, "show corners failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(NotificationHelper.ID_TRIGGER, NotificationHelper.trigger(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_CORNERS.equals(intent.getAction())) {
            showCornersInternal();
            return ServiceModeManager.isActive(this) ? START_STICKY : START_NOT_STICKY;
        }
        if (intent == null && !ServiceModeManager.isActive(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        testMode = intent != null && intent.getBooleanExtra(EXTRA_TEST, false);
        if (!ServiceModeManager.canUseOverlay(this)) {
            LogStore.add(this, "trigger: overlay permission missing");
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        removeOverlays();
        if (Prefs.triggerLeft(this)) addOverlay(true, false);
        if (Prefs.triggerRight(this)) addOverlay(false, false);
        if (overlays.isEmpty()) {
            LogStore.add(this, "trigger: both corners disabled");
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void showCornersInternal() {
        if (!ServiceModeManager.canUseOverlay(this)) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
            if (!ServiceModeManager.isActive(this)) stopSelf();
            return;
        }
        removeOverlays();
        addOverlay(true, true);
        addOverlay(false, true);
        LogStore.add(this, "show corners");
        if (highlightEnd != null) handler.removeCallbacks(highlightEnd);
        highlightEnd = () -> {
            removeOverlays();
            if (ServiceModeManager.isActive(this)) {
                if (Prefs.triggerLeft(this)) addOverlay(true, false);
                if (Prefs.triggerRight(this)) addOverlay(false, false);
            }
            if (overlays.isEmpty()) stopSelf();
        };
        handler.postDelayed(highlightEnd, HIGHLIGHT_MS);
    }

    private void addOverlay(boolean left, boolean highlight) {
        try {
            View view = new View(this);
            if (highlight) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0x99FF3B30);
                bg.setCornerRadius(dp(10));
                view.setBackground(bg);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            if (highlight) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(SIZE_DP), dp(SIZE_DP),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    flags,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | (left ? Gravity.START : Gravity.END);
            lp.x = 0;
            lp.y = 0;
            if (!highlight) {
                view.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        onCornerTap();
                    }
                    return true;
                });
            }
            windowManager.addView(view, lp);
            overlays.add(view);
        } catch (Throwable t) {
            LogStore.add(this, "trigger overlay failed: " + t);
        }
    }

    private void onCornerTap() {
        long now = System.currentTimeMillis();
        if (now - firstTapMs > Prefs.triggerWindowMs(this)) {
            firstTapMs = now;
            taps = 0;
        }
        taps++;
        LogStore.add(this, "trigger tap " + taps);
        if (taps >= Prefs.triggerTaps(this)) {
            taps = 0;
            firstTapMs = 0;
            if (testMode) {
                Toast.makeText(this, R.string.test_trigger, Toast.LENGTH_SHORT).show();
                stopSelf();
            } else {
                int restored = ServiceModeManager.restoreAll(this);
                Toast.makeText(this, getString(R.string.restore_success) + " (" + restored + ")",
                        Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void removeOverlays() {
        if (windowManager == null) return;
        for (View v : overlays) {
            try {
                windowManager.removeView(v);
            } catch (Throwable ignored) {
            }
        }
        overlays.clear();
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (highlightEnd != null) {
            handler.removeCallbacks(highlightEnd);
            highlightEnd = null;
        }
        removeOverlays();
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
