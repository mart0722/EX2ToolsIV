package com.ex2tools.iv.voice;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.climate.ClimateController;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A short lease lets the patched keyserver fail back to native actions after app failure. */
final class SteeringClimate {
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
    SteeringClimate(Context c, Handler h, Runnable voice) {
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
                    if (expiry != 0) show("Климат: +/− температура, треки — вентилятор");
                } catch (Throwable t) {
                    expiry = 0;
                    LogStore.add(context, "steering climate: " + t);
                    show("Не удалось включить управление климатом");
                }
            }
        } else {
            waiting = true; lastTap = now;
            handler.removeCallbacks(single);
            handler.postDelayed(single, 400);
        }
    }
    void key(int key) {
        long now = SystemClock.elapsedRealtime();
        if (!enabled() || expiry <= now || key < 1 || key > 4) return;
        expiry = now + 15000;
        try {
            Settings.Global.putLong(context.getContentResolver(), "ex2_climate_expiry", expiry);
            long session = generation;
            commands.execute(() -> {
                // Respect the dispatcher's 120 ms per-property limit without rejecting fast taps.
                long delay = 130L - (SystemClock.elapsedRealtime() - lastCommandAt);
                if (delay > 0) {
                    try { Thread.sleep(delay); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                if (generation != session || expiry <= SystemClock.elapsedRealtime()) return;
                lastCommandAt = SystemClock.elapsedRealtime();
                try {
                    CommandDispatcher.Result result = key <= 2
                            ? ClimateController.get().adjustTempInteractive(key == 1 ? 1f : -1f)
                            : ClimateController.get().adjustFanInteractive(key == 3 ? 1 : -1);
                    handler.post(() -> {
                        if (expiry == 0 || generation != session) return;
                        if (result == null || !result.ok()) {
                            show("Команда климата не выполнена");
                            leave(true);
                        } else show(key <= 2 ? (key == 1 ? "Температура +1 °C" : "Температура −1 °C")
                                : (key == 3 ? "Вентилятор +1" : "Вентилятор −1"));
                    });
                } catch (Throwable error) {
                    handler.post(() -> {
                        LogStore.add(context, "steering climate command: " + error);
                        leave(true);
                    });
                }
            });
        } catch (Throwable t) {
            LogStore.add(context, "steering climate key: " + t);
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
        } catch (Throwable t) { LogStore.add(context, "steering climate reset: " + t); }
        if (notify) show("Обычное управление кнопками");
    }
    private void show(String text) {
        if (feedback != null) feedback.cancel();
        feedback = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        feedback.show();
    }
}
