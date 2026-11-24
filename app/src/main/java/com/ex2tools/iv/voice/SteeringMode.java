package com.ex2tools.iv.voice;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A short lease lets the patched keyserver fail back to native actions after app failure. */
final class SteeringMode {
    private final Context context;
    private final Handler handler;
    private volatile long expiry;
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private Toast feedback;
    private volatile long generation;
    private long lastCommandAt;
    private long lastTap;
    private boolean waiting;
    private final Runnable single;
    private final Runnable beat = new Runnable() {
        @Override public void run() {
            if (!enabled() || expiry <= SystemClock.elapsedRealtime()) {
                if (expiry != 0) leave(true);
                return;
            }
            try {
                Settings.Global.putLong(context.getContentResolver(), "ex2_climate_heartbeat", SystemClock.elapsedRealtime());
            } catch (Throwable t) { leave(true); return; }
            handler.postDelayed(this, 1000);
        }
    };
    SteeringMode(Context c, Handler h, Runnable voice) {
        context = c; handler = h;
        single = () -> { waiting = false; voice.run(); };
        // Clear any lease left by a previous process before accepting new taps.
        leave(false);
    }
    private boolean enabled() {
        return Prefs.steeringClimate(context);
    }
    void tap(Runnable voice) {
        if (!enabled()) { voice.run(); return; }
        long now = SystemClock.elapsedRealtime();
        if (waiting && now - lastTap <= 400) {
            waiting = false;
            handler.removeCallbacks(single);
            if (expiry > now) leave(true);
            else {
                try {
                    expiry = now + 15000;
                    Settings.Global.putLong(context.getContentResolver(), "ex2_climate_expiry", expiry);
                    handler.removeCallbacks(beat);
                    beat.run();
                    if (expiry != 0) show(context.getString(R.string.steering_mode_active));
                } catch (Throwable t) {
                    expiry = 0;
                    LogStore.add(context, "steering mode: " + t);
                    show(context.getString(R.string.steering_mode_start_failed));
                }
            }
        } else {
            waiting = true; lastTap = now;
            handler.removeCallbacks(single);
            handler.postDelayed(single, 400);
        }
    }
    void key(int key) {
        SteeringButton button = SteeringButton.fromCode(key);
        long now = SystemClock.elapsedRealtime();
        if (!enabled() || expiry <= now || button == null) return;
        SteeringButton.Action action = button.action(context);
        expiry = now + 15000;
        try {
            Settings.Global.putLong(context.getContentResolver(), "ex2_climate_expiry", expiry);
            long session = generation;
            commands.execute(() -> {
                // Serialize closely spaced button events without rejecting fast taps.
                long delay = 130L - (SystemClock.elapsedRealtime() - lastCommandAt);
                if (delay > 0) {
                    try { Thread.sleep(delay); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                if (generation != session || expiry <= SystemClock.elapsedRealtime()) return;
                lastCommandAt = SystemClock.elapsedRealtime();
                try {
                    CommandDispatcher.Result result = action.execute(context);
                    boolean ok = result != null && result.ok();
                    if (!ok) LogStore.add(context, "steering " + action + ": "
                            + (result == null ? "no result" : result.message));
                    String feedback = ok ? action.feedback(context)
                            : context.getString(R.string.steering_action_failed, context.getString(action.label));
                    handler.post(() -> {
                        if (expiry == 0 || generation != session) return;
                        if (!ok) leave(false);
                        show(feedback);
                    });
                } catch (Throwable error) {
                    handler.post(() -> {
                        if (expiry == 0 || generation != session) return;
                        LogStore.add(context, "steering " + action + ": " + error);
                        leave(false);
                        show(context.getString(R.string.steering_action_failed, context.getString(action.label)));
                    });
                }
            });
        } catch (Throwable t) {
            LogStore.add(context, "steering key: " + t);
            leave(true);
        }
    }
    void close() {
        waiting = false;
        handler.removeCallbacks(single);
        leave(false);
        commands.shutdownNow();
        if (feedback != null) feedback.cancel();
    }
    private void leave(boolean notify) {
        generation++;
        expiry = 0;
        handler.removeCallbacks(beat);
        try {
            Settings.Global.putLong(context.getContentResolver(), "ex2_climate_expiry", 0);
            Settings.Global.putLong(context.getContentResolver(), "ex2_climate_heartbeat", 0);
        } catch (Throwable t) { LogStore.add(context, "steering mode reset: " + t); }
        if (notify) show(context.getString(R.string.steering_mode_inactive));
    }
    private void show(String text) {
        if (feedback != null) feedback.cancel();
        feedback = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        feedback.show();
    }
}
