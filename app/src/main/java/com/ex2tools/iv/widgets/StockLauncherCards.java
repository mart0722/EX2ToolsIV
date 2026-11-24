package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.StringRes;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

public final class StockLauncherCards {

    private static final long STARTUP_RETRY_MS = 4000L;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Object STARTUP_RETRY = new Object();

    private StockLauncherCards() {
    }

    @StringRes
    public static int setHidden(Context context, boolean hidden) {
        Prefs.setStockLauncherCardsHidden(context, hidden);
        if (!hidden) HANDLER.removeCallbacksAndMessages(STARTUP_RETRY);
        return sendCommand(context, hidden);
    }

    public static void restoreOnStartup(Context context) {
        if (!Prefs.stockLauncherCardsHidden(context)) return;
        Context app = context.getApplicationContext();
        sendCommand(app, true);

        // Flyme can still be initializing its cards when the first command arrives.
        HANDLER.removeCallbacksAndMessages(STARTUP_RETRY);
        HANDLER.postDelayed(() -> {
            if (Prefs.stockLauncherCardsHidden(app)) sendCommand(app, true);
        }, STARTUP_RETRY, STARTUP_RETRY_MS);
    }

    @StringRes
    private static int sendCommand(Context context, boolean hidden) {
        Intent intent = new Intent("com.flyme.auto.launcher.action.TOGGLE_AICY_WIDGET")
                .setPackage(WidgetModes.STOCK_LAUNCHER)
                .putExtra("is_hide", hidden);
        try {
            context.getPackageManager().getPackageInfo(WidgetModes.STOCK_LAUNCHER, 0);
            context.sendOrderedBroadcast(intent, null);
            return R.string.widgets_stock_cards_sent;
        } catch (PackageManager.NameNotFoundException e) {
            return R.string.widgets_stock_cards_launcher_missing;
        } catch (SecurityException e) {
            LogStore.add(context, "stock launcher cards command failed: " + e);
            return R.string.widgets_stock_cards_failed;
        }
    }
}
