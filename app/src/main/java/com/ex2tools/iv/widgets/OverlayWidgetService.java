package com.ex2tools.iv.widgets;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.Telemetry;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating widgets for launchers which do not support AppWidgetHost (or as a
 * non-invasive alternative for any launcher). The widgets are normal
 * RemoteViews inflated into windows of TYPE_APPLICATION_OVERLAY and are shown
 * while the current HOME launcher is in front (or while an individual widget
 * is pinned).
 */
public final class OverlayWidgetService extends Service {

    private static final long POLL_MS = 1000L;
    private static final String ACTION_RESTART = "com.ex2tools.iv.overlay.RESTART";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OverlaySpec> specs = new ArrayList<>();
    public static volatile boolean running;
    private WindowManager windowManager;
    private volatile boolean destroyed;
    private int lastThemeSignature = Integer.MIN_VALUE;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            WidgetModes.refreshAuto(OverlayWidgetService.this);
            if (!Settings.canDrawOverlays(OverlayWidgetService.this)) {
                stopSelf();
                return;
            }
            int themeSignature = themeSignature();
            if (lastThemeSignature != Integer.MIN_VALUE
                    && lastThemeSignature != themeSignature) {
                lastThemeSignature = themeSignature;
                for (OverlaySpec spec : specs) {
                    remove(spec);
                }
                handler.postDelayed(this, 100L);
                return;
            }
            lastThemeSignature = themeSignature;
            ForegroundState state = foregroundState();
            boolean anyEnabled = false;
            for (OverlaySpec spec : specs) {
                boolean enabled = Prefs.overlayEnabled(OverlayWidgetService.this, spec.key);
                anyEnabled |= enabled;
                boolean pinned = Prefs.overlayPinned(OverlayWidgetService.this, spec.key);
                boolean show = enabled && !state.appList && (pinned || state.launcher);
                if (show) {
                    ensure(spec);
                    update(spec);
                } else {
                    remove(spec);
                }
            }
            if (!anyEnabled) {
                stopSelf();
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, OverlayWidgetService.class));
        } catch (Throwable t) {
            LogStore.add(c, "overlay service start failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, OverlayWidgetService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void restart(Context c) {
        if (!Settings.canDrawOverlays(c) || !hasEnabled(c)) {
            stop(c);
            return;
        }
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, OverlayWidgetService.class).setAction(ACTION_RESTART));
        } catch (Throwable t) {
            LogStore.add(c, "overlay service restart failed: " + t);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static void sync(Context c) {
        WidgetModes.refreshAuto(c);
        boolean allowed = Settings.canDrawOverlays(c);
        boolean enabled = hasEnabled(c);
        LogStore.add(c, "overlay sync: allowed=" + allowed
                + " enabled=" + enabled + " running=" + running);
        if (!allowed || !enabled) {
            stop(c);
        } else {
            start(c);
        }
    }

    public static boolean hasEnabled(Context c) {
        for (String key : OverlaySpec.keys()) {
            if (Prefs.overlayEnabled(c, key)) return true;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(NotificationHelper.ID_OVERLAY, NotificationHelper.overlay(this));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LogStore.add(this, "overlay service created");
        for (String key : OverlaySpec.keys()) {
            specs.add(new OverlaySpec(key));
        }
        handler.post(poll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            for (OverlaySpec spec : specs) {
                remove(spec);
            }
            handler.removeCallbacks(poll);
            handler.post(poll);
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (destroyed) return;
        for (OverlaySpec spec : specs) {
            remove(spec);
        }
        lastThemeSignature = Integer.MIN_VALUE;
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        LogStore.add(this, "overlay service destroyed");
        handler.removeCallbacksAndMessages(null);
        for (OverlaySpec spec : specs) {
            remove(spec);
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensure(OverlaySpec spec) {
        if (spec.view != null || windowManager == null) return;
        try {
            FrameLayout container = new FrameLayout(this);
            View content;
            if (WidgetsCommon.SIZE_SPEED.equals(spec.key)) {
                spec.speedView = new SpeedOverlayView(this);
                content = spec.speedView.view();
                spec.speedView.update(this, WidgetsCommon.readSpeed(this));
            } else {
                content = OverlayWidgetViews.create(this, spec.key);
                OverlayWidgetViews.update(this, content, spec.key);
            }
            if (WidgetsCommon.SIZE_SPEED.equals(spec.key)) {
                container.addView(content, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            } else {
                container.addView(content);
            }

            int size = WidgetsCommon.SIZE_SPEED.equals(spec.key)
                    ? Prefs.widgetSizePct(this, spec.key)
                    : Prefs.overlaySizePct(this, spec.key);
            int width = Math.max(1, Math.round(spec.widthDp * density() * size / 100f));
            int height = Math.max(1, Math.round(spec.heightDp * density() * size / 100f));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    width, height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Prefs.overlayX(this, spec.key, spec.defaultX);
            lp.y = Prefs.overlayY(this, spec.key, spec.defaultY);
            container.setAlpha(1f);

            bindDrag(spec, container, content, lp);
            windowManager.addView(container, lp);
            spec.view = container;
            spec.content = content;
            spec.params = lp;
            LogStore.add(this, "overlay added " + spec.key + " " + width + "x" + height
                    + " at " + lp.x + "," + lp.y);
        } catch (Throwable t) {
            LogStore.add(this, "overlay add " + spec.key + " failed: " + t);
        }
    }

    private void update(OverlaySpec spec) {
        if (spec.view == null || spec.content == null) return;
        try {
            if (WidgetsCommon.SIZE_SPEED.equals(spec.key) && spec.speedView != null) {
                spec.speedView.update(this, WidgetsCommon.readSpeed(this));
            } else {
                OverlayWidgetViews.update(this, spec.content, spec.key);
            }
            spec.view.setAlpha(1f);
        } catch (Throwable t) {
            LogStore.add(this, "overlay update " + spec.key + " failed: " + t);
        }
    }

    private void remove(OverlaySpec spec) {
        if (spec.view == null) return;
        try {
            windowManager.removeView(spec.view);
        } catch (Throwable ignored) {
        }
        spec.view = null;
        spec.content = null;
        spec.params = null;
        spec.speedView = null;
    }

    private RemoteViews build(String key) {
        RemoteViews views;
        switch (key) {
            case WidgetsCommon.SIZE_SPEED:
                views = SpeedWidgetRenderer.build(this, -1, WidgetsCommon.readSpeed(this));
                break;
            case WidgetsCommon.SIZE_DRIVE_REGEN:
                views = WidgetsRenderer.driveRegen(this,
                        intOrNull(Telemetry.driveNow(this)),
                        intOrNull(Telemetry.regenNow(this)));
                break;
            case WidgetsCommon.SIZE_DRIVE_MODE:
                views = WidgetsRenderer.driveVertical(this, intOrNull(Telemetry.driveNow(this)));
                break;
            case WidgetsCommon.SIZE_REGEN:
                views = WidgetsRenderer.regenVertical(this, intOrNull(Telemetry.regenNow(this)));
                break;
            case WidgetsCommon.SIZE_FAN:
                views = WidgetsRenderer.fan(this, WidgetsCommon.readFan(),
                        R.layout.widget_fan_speed);
                break;
            case WidgetsCommon.SIZE_TEMP:
                views = WidgetsRenderer.temperature(this, WidgetsCommon.readTemp(this),
                        R.layout.widget_temperature);
                break;
            case WidgetsCommon.SIZE_VENT:
                views = WidgetsRenderer.vent(this, WidgetsCommon.readVent(),
                        WidgetsCommon.readRecirc());
                break;
            case WidgetsCommon.SIZE_VENT_FAN:
                views = WidgetsRenderer.ventFan(this, WidgetsCommon.readVent(),
                        WidgetsCommon.readRecirc(), WidgetsCommon.readFan(),
                        WidgetsCommon.readAc());
                break;
            case WidgetsCommon.SIZE_HEATING:
                views = WidgetsRenderer.heating(this,
                        WidgetsCommon.readWheelHeat(),
                        WidgetsCommon.readRearDefrost(),
                        WidgetsCommon.readFrontMaxDefrost(),
                        WidgetsCommon.readWindshieldHeat(),
                        WidgetsCommon.readSeatHeat(1),
                        WidgetsCommon.readSeatHeat(4));
                break;
            default:
                views = new RemoteViews(getPackageName(), R.layout.widget_speed);
                break;
        }
        // The overlay window itself controls the final size. Do not apply
        // the AppWidget scale a second time.
        return views;
    }

    private void bindDrag(OverlaySpec spec, FrameLayout container, View content,
                          WindowManager.LayoutParams lp) {
        View drag = content.findViewById(R.id.widget_bg_layer);
        if (drag == null || drag.getVisibility() != View.VISIBLE) drag = content;
        drag.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved) {
                            lp.x = startX + Math.round(dx);
                            lp.y = startY + Math.round(dy);
                            try {
                                windowManager.updateViewLayout(container, lp);
                            } catch (Throwable ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (moved) {
                            Prefs.setOverlayPos(OverlayWidgetService.this,
                                    spec.key, lp.x, lp.y);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private int themeSignature() {
        int mode = Prefs.themeMode(this);
        int night;
        if (mode == 1) {
            night = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            night = Configuration.UI_MODE_NIGHT_YES;
        } else {
            night = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
        }
        return mode * 10 + (night == Configuration.UI_MODE_NIGHT_YES ? 1 : 0);
    }

    private ForegroundState foregroundState() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return new ForegroundState(true, false);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0) == null) {
                return new ForegroundState(true, false);
            }
            ComponentName top = tasks.get(0).topActivity;
            if (top == null) return new ForegroundState(true, false);
            String cls = top.getClassName() == null ? "" : top.getClassName();
            String home = WidgetModes.launcherPackage(this);
            boolean stock = WidgetModes.STOCK_LAUNCHER.equals(top.getPackageName());
            boolean appList = stock && cls.contains("AppListActivity");
            boolean homeVisible = !home.isEmpty() && home.equals(top.getPackageName());
            return new ForegroundState(homeVisible && !appList, appList);
        } catch (Throwable t) {
            LogStore.add(this, "overlay foreground check failed: " + t);
            return new ForegroundState(true, false);
        }
    }

    private static Integer intOrNull(int value) {
        return value == Integer.MIN_VALUE ? null : value;
    }

    private int dp(int value) {
        return Math.round(value * density());
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static final class ForegroundState {
        final boolean launcher;
        final boolean appList;

        ForegroundState(boolean launcher, boolean appList) {
            this.launcher = launcher;
            this.appList = appList;
        }
    }

    private static final class OverlaySpec {
        final String key;
        final int widthDp;
        final int heightDp;
        final int defaultX;
        final int defaultY;
        View view;
        View content;
        SpeedOverlayView speedView;
        WindowManager.LayoutParams params;

        OverlaySpec(String key) {
            this.key = key;
            switch (key) {
                case WidgetsCommon.SIZE_SPEED:
                    widthDp = 240; heightDp = 240; defaultX = 40; defaultY = 160;
                    break;
                case WidgetsCommon.SIZE_DRIVE_REGEN:
                    widthDp = 420; heightDp = 220; defaultX = 40; defaultY = 40;
                    break;
                case WidgetsCommon.SIZE_DRIVE_MODE:
                    widthDp = 180; heightDp = 240; defaultX = 40; defaultY = 420;
                    break;
                case WidgetsCommon.SIZE_REGEN:
                    widthDp = 180; heightDp = 240; defaultX = 240; defaultY = 420;
                    break;
                case WidgetsCommon.SIZE_FAN:
                    widthDp = 240; heightDp = 150; defaultX = 40; defaultY = 40;
                    break;
                case WidgetsCommon.SIZE_TEMP:
                    widthDp = 260; heightDp = 150; defaultX = 300; defaultY = 40;
                    break;
                case WidgetsCommon.SIZE_VENT:
                    widthDp = 420; heightDp = 220; defaultX = 40; defaultY = 280;
                    break;
                case WidgetsCommon.SIZE_VENT_FAN:
                    widthDp = 420; heightDp = 260; defaultX = 40; defaultY = 520;
                    break;
                case WidgetsCommon.SIZE_HEATING:
                    widthDp = 420; heightDp = 260; defaultX = 480; defaultY = 520;
                    break;
                default:
                    widthDp = 220; heightDp = 220; defaultX = 40; defaultY = 40;
                    break;
            }
        }

        static String[] keys() {
            return new String[]{
                    WidgetsCommon.SIZE_SPEED,
                    WidgetsCommon.SIZE_DRIVE_REGEN,
                    WidgetsCommon.SIZE_DRIVE_MODE,
                    WidgetsCommon.SIZE_REGEN,
                    WidgetsCommon.SIZE_FAN,
                    WidgetsCommon.SIZE_TEMP,
                    WidgetsCommon.SIZE_VENT,
                    WidgetsCommon.SIZE_VENT_FAN,
                    WidgetsCommon.SIZE_HEATING
            };
        }
    }
}
