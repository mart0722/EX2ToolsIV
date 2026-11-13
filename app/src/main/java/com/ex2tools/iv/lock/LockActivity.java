package com.ex2tools.iv.lock;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.ex2tools.iv.R;
import com.ex2tools.iv.car.Telemetry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen dark screen with clock and vehicle summary. */
public final class LockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long openedAt;
    private volatile boolean running;

    private TextView date;
    private TextView battery;
    private TextView range;
    private TextView temp;

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
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_lock);

        date = findViewById(R.id.lockDate);
        battery = findViewById(R.id.lockBattery);
        range = findViewById(R.id.lockRange);
        temp = findViewById(R.id.lockTemp);
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
    }

    @Override
    protected void onPause() {
        running = false;
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
