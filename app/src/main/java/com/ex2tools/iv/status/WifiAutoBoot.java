package com.ex2tools.iv.status;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.util.concurrent.atomic.AtomicBoolean;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

public final class WifiAutoBoot {

    private static final int ATTEMPTS = 10;
    private static final long RETRY_MS = 2500L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private WifiAutoBoot() {
    }

    public static void applyOnBoot(Context context) {
        if (!Prefs.statusWifi(context) || !Prefs.wifiAutoOnBoot(context)) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                enableWithRetry(app);
            } finally {
                RUNNING.set(false);
            }
        }, "wifi-auto-boot").start();
    }

    private static void enableWithRetry(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            LogStore.add(context, "wifi auto: WifiManager unavailable");
            return;
        }
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (!Prefs.statusWifi(context) || !Prefs.wifiAutoOnBoot(context)) {
                LogStore.add(context, "wifi auto: cancelled by settings");
                return;
            }
            try {
                if (wm.isWifiEnabled()) {
                    if (attempt > 1) LogStore.add(context, "wifi auto: enabled");
                    return;
                }
                boolean requested = wm.setWifiEnabled(true);
                LogStore.add(context, "wifi auto: attempt " + attempt + " requested=" + requested);
            } catch (Throwable t) {
                LogStore.add(context, "wifi auto failed: " + t);
            }
            if (attempt < ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        try {
            if (!wm.isWifiEnabled()) {
                LogStore.add(context, "wifi auto: still disabled after retries");
            }
        } catch (Throwable ignored) {
        }
    }
}
