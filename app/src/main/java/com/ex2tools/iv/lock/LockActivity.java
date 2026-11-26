package com.ex2tools.iv.lock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen dark screen with clock and vehicle summary. */
public final class LockActivity extends Activity {

    private static final long SPEED_REFRESH_MS = 120L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static volatile boolean showing;
    private static boolean speedVisible;

    public static boolean isSpeedVisible() {
        return speedVisible;
    }

    /** Открыт ли сейчас экран блокировки (чтобы автоблокировка не срабатывала повторно). */
    public static boolean isShowing() {
        return showing;
    }

    private long openedAt;
    private volatile boolean running;

    private TextView date;
    private TextView battery;
    private TextView range;
    private TextView temp;
    private View speedPanel;
    private TextView speed;
    private boolean speedReadPending;

    private final Runnable speedTick = new Runnable() {
        @Override
        public void run() {
            if (!running || speedPanel.getVisibility() != View.VISIBLE || speedReadPending) return;
            speedReadPending = true;
            io.execute(() -> {
                float kmh = WidgetsCommon.readSpeed(LockActivity.this);
                handler.post(() -> {
                    speedReadPending = false;
                    if (!running || speedPanel.getVisibility() != View.VISIBLE) return;
                    speed.setText(Float.isFinite(kmh)
                            ? Integer.toString(Math.round(kmh))
                            : getString(R.string.lock_speed_missing));
                    handler.postDelayed(this, SPEED_REFRESH_MS);
                });
            });
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            handler.postDelayed(this, 1000L);
            updateDate();
            io.execute(() -> {
                int soc = Telemetry.soc(LockActivity.this);
                float km = Telemetry.rangeKm(LockActivity.this);
                float outside = Telemetry.outsideTemp(LockActivity.this);
                runOnUiThread(() -> apply(soc, km, outside));
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 0.02f;
        getWindow().setAttributes(params);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        showing = true;
        setContentView(R.layout.activity_lock);

        date = findViewById(R.id.lockDate);
        battery = findViewById(R.id.lockBattery);
        range = findViewById(R.id.lockRange);
        temp = findViewById(R.id.lockTemp);
        speedPanel = findViewById(R.id.lockSpeedPanel);
        speed = findViewById(R.id.lockSpeed);
        openedAt = SystemClock.elapsedRealtime();

        View root = findViewById(R.id.lockRoot);
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (SystemClock.elapsedRealtime() - openedAt > 500L) {
                            try {
                                startActivity(new Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_HOME)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Throwable ignored) {
                            }
                            finish();
                        }
                        return true;
                    }
                });
        root.setOnTouchListener((v, event) -> detector.onTouchEvent(event));

        TextView hint = findViewById(R.id.lockHint);
        hint.animate().alpha(0f).setStartDelay(3200L).setDuration(800L).start();
        updateDate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openedAt = SystemClock.elapsedRealtime();
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
        speedVisible = Prefs.speedOnLockScreen(this);
        speedPanel.setVisibility(speedVisible ? View.VISIBLE : View.GONE);
        if (speedVisible) handler.post(speedTick);
    }

    @Override
    protected void onPause() {
        running = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(speedTick);
        speedVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        showing = false;
        running = false;
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private void updateDate() {
        try {
            date.setText(new SimpleDateFormat(
                    getString(R.string.lock_date_format), new Locale("ru"))
                    .format(new Date()));
        } catch (Throwable ignored) {
        }
    }

    private void apply(int soc, float km, float outside) {
        battery.setText(soc < 0 ? "—" : soc + "%");
        range.setText(Float.isNaN(km) ? "—" : Math.round(km) + " км");
        temp.setText(Float.isNaN(outside)
                ? "—"
                : String.format(Locale.US, "%.1f °C", outside));
    }
}
