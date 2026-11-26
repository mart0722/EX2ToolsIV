package com.ex2tools.iv.status;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

public final class WifiAutoBoot {

    private static final int ENABLE_ATTEMPTS = 24;
    private static final long ENABLE_RETRY_MS = 2500L;
    private static final long FIRST_SCAN_DELAY_MS = 2000L;
    private static final long FAST_SCAN_MS = 5000L;
    private static final long SLOW_SCAN_MS = 15000L;
    private static final long FAST_SCAN_WINDOW_MS = 60000L;
    private static final long MAX_SCAN_WINDOW_MS = 180000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private WifiAutoBoot() {
    }

    public static void applyOnBoot(Context context) {
        if (!Prefs.statusWifi(context) || !Prefs.wifiAutoOnBoot(context)) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                enableAndScan(app);
            } finally {
                RUNNING.set(false);
            }
        }, "wifi-auto-boot").start();
    }

    private static void enableAndScan(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            LogStore.add(context, "wifi auto: WifiManager unavailable");
            return;
        }

        if (!enable(wm, context)) return;
        if (!sleep(FIRST_SCAN_DELAY_MS)) return;

        long started = SystemClock.elapsedRealtime();
        int scans = 0;
        while (Prefs.statusWifi(context) && Prefs.wifiAutoOnBoot(context)) {
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
        LogStore.add(context, "wifi auto: cancelled by settings");
    }

    private static boolean enable(WifiManager wm, Context context) {
        for (int attempt = 1; attempt <= ENABLE_ATTEMPTS; attempt++) {
            if (!Prefs.statusWifi(context) || !Prefs.wifiAutoOnBoot(context)) {
                LogStore.add(context, "wifi auto: cancelled by settings");
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
