package com.ex2tools.iv.widgets;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.state.VehicleState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * РџР»Р°РІР°СЋС‰РёРµ РІРёРґР¶РµС‚С‹ РїРѕРІРµСЂС… СЂР°Р±РѕС‡РµРіРѕ СЃС‚РѕР»Р°. Р—РЅР°С‡РµРЅРёСЏ С‡РёС‚Р°СЋС‚СЃСЏ РёР·
 * VehicleState, РѕР±РЅРѕРІР»РµРЅРёРµ вЂ” РїРѕ СЃРѕР±С‹С‚РёСЏРј, Р° РЅРµ РѕРїСЂРѕСЃРѕРј VHAL.
 */
public final class OverlayWidgetService extends Service {

    private static final long POLL_MS = 400L;
    private static final int GRID_STEP_DP = 16;
    private static final int WIDGET_GAP_DP = 8;
    private static final int NEIGHBOR_SNAP_DISTANCE_DP = 24;
    private static final int GLASS_NEIGHBOR_SNAP_DISTANCE_DP = 36;
    private static final String ACTION_RESTART = "com.ex2tools.iv.overlay.RESTART";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OverlaySpec> specs = new ArrayList<>();
    public static volatile boolean running;
    private static volatile OverlayWidgetService instance;
    private WindowManager windowManager;
    private volatile boolean destroyed;
    private int tilePitch;
    private int lastThemeSignature = Integer.MIN_VALUE;
    private volatile boolean stateDirty = true;
    private long lastRangeSample;
    private long lastClockMinute = -1L;

    private final VehicleState.Listener stateListener = this::onVehicleStateChanged;

    private void onVehicleStateChanged() {
        if (destroyed) return;
        stateDirty = true;
        if (!Prefs.fastWidgetResponse(this)) return;
        for (OverlaySpec spec : specs) {
            if (spec.view == null) continue;
            String key = spec.key;
            if (WidgetsCommon.SIZE_TEMP.equals(key)
                    || WidgetsCommon.SIZE_FAN.equals(key)
                    || WidgetsCommon.SIZE_VENT.equals(key)
                    || WidgetsCommon.SIZE_VENT_FAN.equals(key)
                    || WidgetsCommon.SIZE_HEATING.equals(key)
                    || WidgetsCommon.SIZE_WIPERS.equals(key)
                    || WidgetsCommon.SIZE_WINDSHIELD_HEAT.equals(key)
                    || WidgetsCommon.SIZE_WHEEL_HEAT.equals(key)
                    || WidgetsCommon.SIZE_RECIRC.equals(key)) {
                update(spec);
            }
        }
    }
    private final ExecutorService speedIo = Executors.newSingleThreadExecutor();
    private final AtomicBoolean speedBusy = new AtomicBoolean();
    private volatile float speedTrip;
    private volatile long speedTripAt;


    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            WidgetModes.refreshAuto(OverlayWidgetService.this);
            if (!Settings.canDrawOverlays(OverlayWidgetService.this)) {
                stopSelf();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastRangeSample > 10_000L) {
                lastRangeSample = now;
                com.ex2tools.iv.car.RangeModel.sample(OverlayWidgetService.this);
            }
            long clockMinute = now / 60_000L;
            boolean clockTick = clockMinute != lastClockMinute;
            lastClockMinute = clockMinute;
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
            boolean render = stateDirty;
            stateDirty = false;
            for (OverlaySpec spec : specs) {
                boolean enabled = Prefs.overlayEnabled(OverlayWidgetService.this, spec.key);
                boolean active = enabled
                        && !(WidgetsCommon.SIZE_CLIMATE.equals(spec.key)
                        && !Prefs.climateEnabled(OverlayWidgetService.this));
                anyEnabled |= active;
                boolean pinned = Prefs.overlayPinned(OverlayWidgetService.this, spec.key);
                boolean show = active && !state.appList && (pinned || state.launcher);
                if (show && WidgetsCommon.SIZE_APPS.equals(spec.key)
                        && !AppsOverlay.hasVisible(OverlayWidgetService.this)) {
                    show = false;
                }
                if (show) {
                    boolean created = ensure(spec);
                    if (WidgetsCommon.SIZE_OUTSIDE_TEMP.equals(spec.key)
                            || WidgetsCommon.SIZE_OUTSIDE_TEMP_WIDE.equals(spec.key)) {
                        // Keep polling even when the vehicle sends no temperature events.
                        long interval = SystemClock.elapsedRealtime() / 10000L;
                        if (created) {
                            spec.lastOutsideTempInterval = interval;
                        } else if (spec.lastOutsideTempInterval != interval) {
                            spec.lastOutsideTempInterval = interval;
                            update(spec);
                        }
                        continue;
                    }
                    // Retry climate reads even if the ECU emitted no event during boot.
                    if (render || created || WidgetsCommon.SIZE_TEMP.equals(spec.key)
                            || (clockTick && (WidgetsCommon.SIZE_CLOCK.equals(spec.key)
                            || WidgetsCommon.SIZE_CLOCK_LARGE.equals(spec.key)))) {
                        update(spec);
                    }
                } else {
                    remove(spec);
                }
            }
            if (Prefs.overlayPlacement(OverlayWidgetService.this) == Prefs.OverlayPlacement.CORNERS) {
                layoutDockedWidgets();
            }
            if (!anyEnabled) {
                stopSelf();
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable speedTick = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            OverlaySpec speed = null;
            for (OverlaySpec spec : specs) {
                if (WidgetsCommon.SIZE_SPEED.equals(spec.key)) {
                    speed = spec;
                    break;
                }
            }
            final OverlaySpec target = speed;
            if (target != null && target.view != null && target.speedView != null
                    && !speedBusy.get()) {
                speedBusy.set(true);
                speedIo.execute(() -> {
                    float kmh = WidgetsCommon.readSpeed(OverlayWidgetService.this);
                    long now = System.currentTimeMillis();
                    if (now - speedTripAt > 2000L) {
                        speedTrip = WidgetsCommon.readTripKm(OverlayWidgetService.this);
                        speedTripAt = now;
                    }
                    final float trip = speedTrip;
                    handler.post(() -> {
                        speedBusy.set(false);
                        if (target.view != null && target.speedView != null) {
                            target.speedView.update(OverlayWidgetService.this, kmh, trip);
                        }
                    });
                });
            }
            handler.postDelayed(this, 120L);
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
            if (!Prefs.overlayEnabled(c, key)) continue;
            if (WidgetsCommon.SIZE_CLIMATE.equals(key) && !Prefs.climateEnabled(c)) continue;
            return true;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        instance = this;
        startForeground(NotificationHelper.ID_OVERLAY, NotificationHelper.overlay(this));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LogStore.add(this, "overlay service created");
        for (String key : OverlaySpec.keys()) {
            specs.add(new OverlaySpec(key));
        }
        tilePitch = resolveTilePitch();
        WidgetsCommon.subscribeAll(this);
        VehicleState.get().addListener(stateListener);
        handler.post(poll);
        handler.post(speedTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int pitch = resolveTilePitch();
        boolean restart = intent != null && ACTION_RESTART.equals(intent.getAction());
        if (restart || tilePitch != pitch) {
            tilePitch = pitch;
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
        tilePitch = resolveTilePitch();
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
        instance = null;
        running = false;
        LogStore.add(this, "overlay service destroyed");
        VehicleState.get().removeListener(stateListener);
        handler.removeCallbacksAndMessages(null);
        for (OverlaySpec spec : specs) {
            remove(spec);
        }
        speedIo.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean ensure(OverlaySpec spec) {
        if (spec.view != null || windowManager == null) return false;
        try {
            FrameLayout container = new FrameLayout(this);
            boolean speed = WidgetsCommon.SIZE_SPEED.equals(spec.key);
            boolean apps = WidgetsCommon.SIZE_APPS.equals(spec.key);
            boolean glass = WidgetAppearance.usesGlass(this);
            Size base = glass ? tileSize(spec) : apps ? AppsOverlay.sizeDp(this) : legacySize(spec);
            float scale = glass ? WidgetGrid.scale(this) : (speed
                    ? Prefs.widgetSizePct(this, spec.key) : Prefs.overlaySizePct(this, spec.key)) / 100f;
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            Prefs.OverlayPlacement placement = Prefs.overlayPlacement(this);
            int maxWidth = Math.max(1, metrics.widthPixels - dp(16));
            int maxHeight = Math.max(1, metrics.heightPixels - dp(16));
            int width;
            int height;
            if (tilePitch > 0) {
                // Reserve one gutter per grid line, including between neighboring tiles.
                int gap = tileGap();
                width = base.getWidth() / WidgetGrid.UNIT_DP * tilePitch - gap;
                height = base.getHeight() / WidgetGrid.UNIT_DP * tilePitch - gap;
                scale = tilePitch / (WidgetGrid.UNIT_DP * density());
            } else {
                if (glass) {
                    scale = Math.min(scale, Math.min(maxWidth / (base.getWidth() * density()),
                            maxHeight / (base.getHeight() * density())));
                }
                width = Math.min(maxWidth, Math.max(1, Math.round(base.getWidth() * density() * scale)));
                height = Math.min(maxHeight, Math.max(1, Math.round(base.getHeight() * density() * scale)));
            }

            View content;
            if (speed) {
                spec.speedView = new SpeedOverlayView(this);
                content = spec.speedView.view();
                spec.speedView.update(this, WidgetsCommon.readSpeed(this), WidgetsCommon.readTripKm(this));
            } else {
                content = OverlayWidgetViews.create(this, spec.key, scale);
                OverlayWidgetViews.update(content, spec.key);
            }
            container.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    width, height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Prefs.overlayX(this, spec.key, spec.defaultX);
            lp.y = Prefs.overlayY(this, spec.key, spec.defaultY);
            if (glass) {
                lp.x = Math.max(0, Math.min(metrics.widthPixels - width, lp.x));
                lp.y = Math.max(0, Math.min(metrics.heightPixels - height, lp.y));
            }
            if (placement == Prefs.OverlayPlacement.GRID) {
                snapToGrid(lp);
            }
            container.setAlpha(1f);

            bindDrag(spec, container, content, lp);
            windowManager.addView(container, lp);
            spec.dragging = false;
            spec.view = container;
            spec.content = content;
            spec.params = lp;
            LogStore.add(this, "overlay added " + spec.key + " " + width + "x" + height
                    + " at " + lp.x + "," + lp.y);
            return true;
        } catch (Throwable t) {
            LogStore.add(this, "overlay add " + spec.key + " failed: " + t);
            return false;
        }
    }

    // Keep each corner as a vertical stack. Tall stacks continue inward in a new column.
    private void layoutDockedWidgets() {
        for (OverlaySpec spec : specs) {
            if (spec.view != null && spec.dragging) return;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int gap = dp(8);
        for (int corner = 0; corner < 4; corner++) {
            boolean right = (corner & 1) != 0;
            boolean bottom = (corner & 2) != 0;
            int used = gap;
            int inset = gap;
            int columnWidth = 0;
            for (OverlaySpec spec : specs) {
                if (spec.view == null || spec.params == null) continue;
                WindowManager.LayoutParams lp = spec.params;
                if (Prefs.overlayCorner(this, spec.key) != corner) continue;
                if (used > gap && used + lp.height > height - gap) {
                    inset += columnWidth + gap;
                    used = gap;
                    columnWidth = 0;
                }
                int x = right ? width - inset - lp.width : inset;
                int y = bottom ? height - used - lp.height : used;
                x = Math.max(0, Math.min(width - lp.width, x));
                y = Math.max(0, Math.min(height - lp.height, y));
                if (lp.x != x || lp.y != y) {
                    lp.x = x;
                    lp.y = y;
                    try {
                        windowManager.updateViewLayout(spec.view, lp);
                    } catch (Throwable t) {
                        LogStore.add(this, "overlay dock " + spec.key + " failed: " + t);
                    }
                }
                used += lp.height + gap;
                columnWidth = Math.max(columnWidth, lp.width);
            }
        }
    }

    private Size tileSize(OverlaySpec spec) {
        return WidgetsCommon.SIZE_APPS.equals(spec.key)
                ? AppsOverlay.sizeDp(this) : WidgetGrid.size(this, WidgetCatalog.layout(spec.key));
    }

    private int resolveTilePitch() {
        if (!WidgetAppearance.usesGlass(this)) return 0;
        Prefs.OverlayPlacement placement = Prefs.overlayPlacement(this);
        if (placement != Prefs.OverlayPlacement.GRID && placement != Prefs.OverlayPlacement.NEIGHBORS) {
            return 0;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int gap = dp(WIDGET_GAP_DP);
        int maxWidth = Math.max(1, metrics.widthPixels - dp(16));
        int maxHeight = Math.max(1, metrics.heightPixels - dp(16));
        int pitch = Math.round(WidgetGrid.UNIT_DP * density() * WidgetGrid.scale(this));
        for (OverlaySpec spec : specs) {
            if (!Prefs.overlayEnabled(this, spec.key)) continue;
            if (WidgetsCommon.SIZE_CLIMATE.equals(spec.key) && !Prefs.climateEnabled(this)) continue;
            Size size = tileSize(spec);
            pitch = Math.min(pitch, (maxWidth + gap) / (size.getWidth() / WidgetGrid.UNIT_DP));
            pitch = Math.min(pitch, (maxHeight + gap) / (size.getHeight() / WidgetGrid.UNIT_DP));
        }
        return Math.max(1, pitch);
    }

    private int tileGap() {
        int gap = dp(WIDGET_GAP_DP);
        return tilePitch > 0 ? Math.min(gap, tilePitch - 1) : gap;
    }

    private void snapToGrid(WindowManager.LayoutParams lp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int step = tilePitch > 0 ? tilePitch : Math.max(1, dp(GRID_STEP_DP));
        lp.x = gridCoordinate(lp.x, metrics.widthPixels - lp.width, step);
        lp.y = gridCoordinate(lp.y, metrics.heightPixels - lp.height, step);
    }

    private static int gridCoordinate(int position, int max, int step) {
        int nearest = Math.round(Math.max(0, position) / (float) step);
        int lastVisible = Math.max(0, max) / step;
        return Math.min(nearest, lastVisible) * step;
    }

    private void update(OverlaySpec spec) {
        if (spec.view == null || spec.content == null) return;
        try {
            if (WidgetsCommon.SIZE_SPEED.equals(spec.key) && spec.speedView != null) {
                spec.speedView.update(this, WidgetsCommon.readSpeed(this), WidgetsCommon.readTripKm(this));
            } else {
                OverlayWidgetViews.update(spec.content, spec.key);
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
        spec.dragging = false;
    }

    private Size legacySize(OverlaySpec spec) {
        int width = WidgetsCommon.SIZE_TEMP.equals(spec.key) && Prefs.overlayWideTemperature(this)
                ? WidgetCatalog.get(WidgetsCommon.SIZE_VENT).widthDp : spec.widthDp;
        int height = WidgetsCommon.SIZE_HEATING.equals(spec.key) && Prefs.heatingSingleRow(this)
                ? 180 : spec.heightDp;
        if (WidgetsCommon.SIZE_HEATING.equals(spec.key) && Prefs.heatingSingleRow(this)
                && Prefs.showHeatingMaxAir(this)) {
            width = width * 6 / 5;
        }
        return new Size(width, height);
    }

    private void bindDrag(OverlaySpec spec, FrameLayout container, View content,
                          WindowManager.LayoutParams lp) {
        final boolean locked = Prefs.overlayLock(this);
        final boolean lockWidget = WidgetsCommon.SIZE_LOCK.equals(spec.key);
        final View button = WidgetsCommon.SIZE_WINDSHIELD_HEAT.equals(spec.key)
                ? content.findViewById(R.id.chip_windshield_heat)
                : WidgetsCommon.isStandaloneVehicleButton(spec.key) ? content : null;
        View drag = content.findViewById(R.id.widget_bg_layer);
        if (lockWidget) {
            // Тело виджета блокировки перекрывает фон и перехватывает касания.
            View body = content.findViewById(R.id.lock_body);
            if (body != null) {
                body.setOnClickListener(null);
                body.setClickable(false);
            }
            drag = content;
        } else if (drag == null || drag.getVisibility() != View.VISIBLE) {
            drag = content;
        }
        View.OnTouchListener listener = new View.OnTouchListener() {
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
                        if (locked && button == null) return true;
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved && !locked) {
                            spec.dragging = true;
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
                        spec.dragging = false;
                        if (moved && !locked) {
                            placeAfterDrag(spec, container, lp);
                        } else if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            if (lockWidget) {
                                openLockScreen();
                            } else if (v == button) {
                                v.performClick();
                            } else if (WidgetsCommon.SIZE_SPEED.equals(spec.key)
                                    && v.getHeight() > 0
                                    && event.getY() >= v.getHeight() * 0.72f) {
                                // Тап по нижней части спидометра (там пробег) — сброс с подтверждением.
                                com.ex2tools.iv.status.TripResetService.start(OverlayWidgetService.this);
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
        drag.setOnTouchListener(listener);
        if (button != null && button != drag) button.setOnTouchListener(listener);
        // The app-icon widget has a lot of empty space between rounded tiles;
        // let that area drag the window too, while icons keep working as clicks.
        if (WidgetsCommon.SIZE_APPS.equals(spec.key)) {
            View iconArea = content.findViewById(R.id.apps_container);
            if (iconArea != null && iconArea != drag) iconArea.setOnTouchListener(listener);
        }
    }

    private void placeAfterDrag(OverlaySpec spec, View container, WindowManager.LayoutParams lp) {
        Prefs.OverlayPlacement placement = Prefs.overlayPlacement(this);
        if (placement == Prefs.OverlayPlacement.CORNERS) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int corner = (lp.x + lp.width / 2 >= metrics.widthPixels / 2 ? 1 : 0)
                    | (lp.y + lp.height / 2 >= metrics.heightPixels / 2 ? 2 : 0);
            Prefs.setOverlayCorner(this, spec.key, corner);
            layoutDockedWidgets();
            return;
        }
        if (placement == Prefs.OverlayPlacement.GRID || placement == Prefs.OverlayPlacement.NEIGHBORS) {
            if (placement == Prefs.OverlayPlacement.GRID) snapToGrid(lp);
            else snapToNeighbor(spec, lp);
            try {
                windowManager.updateViewLayout(container, lp);
            } catch (RuntimeException e) {
                LogStore.add(this, "overlay snap " + spec.key + " failed: " + e);
            }
        }
        Prefs.setOverlayPos(this, spec.key, lp.x, lp.y);
    }

    private void snapToNeighbor(OverlaySpec dragged, WindowManager.LayoutParams lp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        List<Rect> neighbors = new ArrayList<>();
        for (OverlaySpec spec : specs) {
            if (spec == dragged || spec.view == null || spec.params == null
                    || spec.view.getVisibility() != View.VISIBLE) continue;
            WindowManager.LayoutParams other = spec.params;
            neighbors.add(new Rect(other.x, other.y, other.x + other.width, other.y + other.height));
        }
        boolean glass = WidgetAppearance.usesGlass(this);
        int reach = glass ? GLASS_NEIGHBOR_SNAP_DISTANCE_DP : NEIGHBOR_SNAP_DISTANCE_DP;
        Rect target = OverlayNeighborSnap.find(
                new Rect(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height),
                neighbors, metrics.widthPixels, metrics.heightPixels,
                tileGap(), dp(reach), glass, tilePitch);
        lp.x = target.left;
        lp.y = target.top;
    }

    /** Попросить оверлеи перерисоваться сразу после действия виджета. */
    public static void requestUpdate(Context c) {
        OverlayWidgetService service = instance;
        if (service == null) return;
        service.stateDirty = true;
        service.handler.removeCallbacks(service.poll);
        service.handler.post(service.poll);
    }

    private void openLockScreen() {
        try {
            startActivity(new android.content.Intent(this,
                    com.ex2tools.iv.lock.LockActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Throwable t) {
            LogStore.add(this, "lock widget open failed: " + t);
        }
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
        long lastOutsideTempInterval = -1L;
        boolean dragging;
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
            WidgetCatalog.Entry entry = WidgetCatalog.get(key);
            if (entry == null) {
                widthDp = 220; heightDp = 220; defaultX = 40; defaultY = 40;
            } else {
                widthDp = entry.widthDp;
                heightDp = entry.heightDp;
                defaultX = entry.defaultX;
                defaultY = entry.defaultY;
            }
        }

        static String[] keys() {
            return new String[]{
                    WidgetsCommon.SIZE_CLOCK,
                    WidgetsCommon.SIZE_CLOCK_LARGE,
                    WidgetsCommon.SIZE_OUTSIDE_TEMP,
                    WidgetsCommon.SIZE_OUTSIDE_TEMP_WIDE,
                    WidgetsCommon.SIZE_SPEED,
                    WidgetsCommon.SIZE_DRIVE_REGEN,
                    WidgetsCommon.SIZE_DRIVE_MODE,
                    WidgetsCommon.SIZE_REGEN,
                    WidgetsCommon.SIZE_FAN,
                    WidgetsCommon.SIZE_TEMP,
                    WidgetsCommon.SIZE_VENT,
                    WidgetsCommon.SIZE_VENT_FAN,
                    WidgetsCommon.SIZE_RECIRC,
                    WidgetsCommon.SIZE_HEATING,
                    WidgetsCommon.SIZE_WINDSHIELD_HEAT,
                    WidgetsCommon.SIZE_WHEEL_HEAT,
                    WidgetsCommon.SIZE_BATTERY_RANGE,
                    WidgetsCommon.SIZE_WIPERS,
                    WidgetsCommon.SIZE_POWER_OFF,
                    WidgetsCommon.SIZE_LOCK,
                    WidgetsCommon.SIZE_APPS
            };
        }
    }
}
