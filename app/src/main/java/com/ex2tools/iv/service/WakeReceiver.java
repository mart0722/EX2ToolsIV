package com.ex2tools.iv.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.screenshot.ScreenshotButtonService;
import com.ex2tools.iv.status.StatusIconService;
import com.ex2tools.iv.tasks.TaskManagerButtonService;
import com.ex2tools.iv.widgets.WidgetUpdateService;

public final class WakeReceiver extends BroadcastReceiver {

    private static long lastWake;

    @Override
    public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        if (now - lastWake < 1500L) return;
        lastWake = now;

        DriveRegenWatchService.sync(context);
        if (Prefs.statusAny(context)) StatusIconService.refresh(context);
        WidgetUpdateService.sync(context);
        ScreenshotButtonService.sync(context);
        TaskManagerButtonService.sync(context);
    }
}
