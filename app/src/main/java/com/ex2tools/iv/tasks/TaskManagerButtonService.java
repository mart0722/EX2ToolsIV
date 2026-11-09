package com.ex2tools.iv.tasks;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

public final class TaskManagerButtonService extends Service {

    private WindowManager windowManager;
    private ImageView button;
    private WindowManager.LayoutParams lp;
    private TaskPanel panel;
    private int startX;
    private int startY;
    private float downRawX;
    private float downRawY;
    private boolean dragging;
    private int touchSlop;

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, TaskManagerButtonService.class));
        } catch (Throwable t) {
            LogStore.add(c, "start task button failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, TaskManagerButtonService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context c) {
        if (Prefs.taskButton(c)) start(c);
        else stop(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_TASK_BUTTON,
                NotificationHelper.taskButton(this));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        addButton();
    }

    private void addButton() {
        if (windowManager == null) return;
        if (!android.provider.Settings.canDrawOverlays(this)) {
            LogStore.add(this, "task button: overlay permission missing");
            stopSelf();
            return;
        }
        try {
            button = new ImageView(this);
            int size = dp(58);
            button.setPadding(dp(14), dp(14), dp(14), dp(14));
            button.setBackgroundResource(R.drawable.bg_screenshot_button);
            button.setImageResource(R.drawable.ic_tasks);
            button.setColorFilter(ContextCompat.getColor(this, R.color.cyan));
            button.setContentDescription(getString(R.string.task_button_desc));

            lp = new WindowManager.LayoutParams(
                    size, size,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            int defaultX = getResources().getDisplayMetrics().widthPixels - size - dp(18);
            int savedX = Prefs.taskX(this);
            int savedY = Prefs.taskY(this);
            lp.x = savedX >= 0 ? savedX : defaultX;
            lp.y = savedY >= 0 ? savedY : dp(200);

            button.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            try {
                                windowManager.updateViewLayout(button, lp);
                            } catch (Throwable ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            Prefs.setTaskPos(TaskManagerButtonService.this, lp.x, lp.y);
                        } else {
                            togglePanel();
                        }
                        dragging = false;
                        return true;
                    default:
                        return false;
                }
            });

            windowManager.addView(button, lp);
        } catch (Throwable t) {
            LogStore.add(this, "task button add failed: " + t);
            stopSelf();
        }
    }

    private void togglePanel() {
        if (panel != null && panel.isShowing()) {
            panel.dismiss();
            panel = null;
            return;
        }
        panel = new TaskPanel(this);
        try {
            panel.show();
        } catch (Throwable t) {
            LogStore.add(this, "task panel failed: " + t);
            panel = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.taskButton(this)) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (panel != null) {
            panel.dismiss();
            panel = null;
        }
        if (button != null && windowManager != null) {
            try {
                windowManager.removeView(button);
            } catch (Throwable ignored) {
            }
            button = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
