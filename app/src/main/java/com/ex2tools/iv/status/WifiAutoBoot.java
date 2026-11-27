package com.ex2tools.iv.status;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.Telemetry;

public final class WifiAutoBoot {

    private static final String TAG = "WifiAutoBoot";
    private static final int ENABLE_ATTEMPTS = 24;
    private static final long ENABLE_RETRY_MS = 2500L;
    private static final long FIRST_SCAN_DELAY_MS = 2000L;
    private static final long FAST_SCAN_MS = 5000L;
    private static final long SLOW_SCAN_MS = 15000L;
    private static final long FAST_SCAN_WINDOW_MS = 60000L;
    private static final long MAX_SCAN_WINDOW_MS = 180000L;
    private static boolean running;
    private static Trigger pendingWake;

    private enum Trigger {
        BOOT, SCREEN, IGNITION
    }

    private WifiAutoBoot() {
    }

    public static void applyOnBoot(Context context) {
        start(context, Trigger.BOOT);
    }

    public static void applyOnWake(Context context) {
        start(context, Trigger.SCREEN);
    }

    static void applyOnIgnition(Context context) {
        start(context, Trigger.IGNITION);
    }

    private static synchronized void start(Context context, Trigger trigger) {
        Context app = context.getApplicationContext();
        if (!mayRun(app, trigger)) return;
        if (running) {
            if (trigger == Trigger.IGNITION
                    || (trigger == Trigger.SCREEN && pendingWake == null)) pendingWake = trigger;
            return;
        }
        running = true;
        new Thread(() -> {
            try {
                if (trigger != Trigger.BOOT) Log.i(TAG, "Checking Wi-Fi after wake: " + trigger);
                enableAndScan(app, trigger);
            } finally {
                finish(app);
            }
        }, "wifi-auto").start();
    }

    private static synchronized void finish(Context context) {
        running = false;
        if (pendingWake != null) {
            Trigger trigger = pendingWake;
            pendingWake = null;
            start(context, trigger);
        }
    }

    private static boolean mayRun(Context context, Trigger trigger) {
        if (!Prefs.statusWifi(context) || !Prefs.wifiAutoOnBoot(context)) return false;
        if (trigger == Trigger.BOOT) return true;
        if (trigger == Trigger.IGNITION) return Boolean.TRUE.equals(Telemetry.ignitionOn());
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    private static void enableAndScan(Context context, Trigger trigger) {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            LogStore.add(context, "wifi auto: WifiManager unavailable");
            return;
        }

        if (!enable(wm, context, trigger)) return;
        if (!sleep(FIRST_SCAN_DELAY_MS)) return;

        long started = SystemClock.elapsedRealtime();
        int scans = 0;
        while (mayRun(context, trigger)) {
            if (!wm.isWifiEnabled()) {
                LogStore.add(context, "wifi auto: radio turned off, scan stopped");
                return;
            }
            int state = connectionState(context, wm);
            if (state == 2) {
                LogStore.add(context, "wifi auto: connected after " + scans + " scan(s)");
                return;
            }

            if (state == 1) {
                LogStore.add(context, "wifi auto: connecting, waiting");
            } else {
                boolean scanRequested = false;
                boolean reconnectRequested = false;
                try {
                    scanRequested = wm.startScan();
                } catch (Throwable t) {
                    LogStore.add(context, "wifi auto: startScan failed: " + t);
                }
                try {
                    reconnectRequested = wm.reconnect();
                } catch (Throwable t) {
                    LogStore.add(context, "wifi auto: reconnect failed: " + t);
                }
                scans++;
                LogStore.add(context, "wifi auto: scan " + scans
                        + " requested=" + scanRequested + " reconnect=" + reconnectRequested);
            }

            long elapsed = SystemClock.elapsedRealtime() - started;
            if (elapsed >= MAX_SCAN_WINDOW_MS) {
                LogStore.add(context, "wifi auto: scan window finished (" + scans + " scans)");
                return;
            }
            if (!sleep(elapsed < FAST_SCAN_WINDOW_MS ? FAST_SCAN_MS : SLOW_SCAN_MS)) return;
        }
        LogStore.add(context, "wifi auto: cancelled");
    }

    private static boolean enable(WifiManager wm, Context context, Trigger trigger) {
        for (int attempt = 1; attempt <= ENABLE_ATTEMPTS; attempt++) {
            if (!mayRun(context, trigger)) {
                LogStore.add(context, "wifi auto: cancelled");
                return false;
            }
            try {
                if (wm.isWifiEnabled()) {
                    if (attempt > 1) LogStore.add(context, "wifi auto: enabled");
                    return true;
                }
                boolean requested = wm.setWifiEnabled(true);
                LogStore.add(context, "wifi auto: attempt " + attempt + " requested=" + requested);
            } catch (Throwable t) {
                LogStore.add(context, "wifi auto failed: " + t);
            }
            if (attempt < ENABLE_ATTEMPTS && !sleep(ENABLE_RETRY_MS)) return false;
        }
        try {
            if (wm.isWifiEnabled()) return true;
        } catch (Throwable ignored) {
        }
        LogStore.add(context, "wifi auto: still disabled after retries");
        return false;
    }

    /** 0 = disconnected, 1 = connecting, 2 = connected. */
    private static int connectionState(Context context, WifiManager wm) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (info != null) {
                    if (info.isConnected()) return 2;
                    if (info.getState() == NetworkInfo.State.CONNECTING) return 1;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null) {
                SupplicantState state = info.getSupplicantState();
                if (state == SupplicantState.COMPLETED && info.getNetworkId() != -1) return 2;
                switch (state) {
                    case AUTHENTICATING:
                    case ASSOCIATING:
                    case ASSOCIATED:
                    case FOUR_WAY_HANDSHAKE:
                    case GROUP_HANDSHAKE:
                        return 1;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
