package com.ex2tools.iv.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.servicemode.ServiceModeManager;
import com.ex2tools.iv.screenshot.ScreenshotButtonService;
import com.ex2tools.iv.status.StatusIconService;

public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        LogStore.add(context, "boot receiver: " + action);

        ServiceModeManager.onBoot(context);
        if (Prefs.ttl65(context)) {
            new Thread(() -> com.ex2tools.iv.car.Ttl65.applyOnBoot(context),
                    "ttl65-boot").start();
        }
        if (ServiceModeManager.isActive(context)) {
            LogStore.add(context, "boot: service mode active, user services stay hidden");
            return;
        }

        DriveRegenWatchService.sync(context);
        StatusIconService.sync(context);
        ScreenshotButtonService.sync(context);
        com.ex2tools.iv.tasks.TaskManagerButtonService.sync(context);
        if (isUserUnlocked(context)) {
            com.ex2tools.iv.widgets.WidgetUpdateService.sync(context);
            com.ex2tools.iv.widgets.OverlayWidgetService.sync(context);
            if (Prefs.rvcBlackBackground(context)) {
                new Thread(() -> com.ex2tools.iv.car.RvcBlackBackground.applyIfNeeded(context),
                        "rvc-black-boot").start();
            }
        } else {
            LogStore.add(context, "boot: user locked, widget sync deferred");
        }
        if (Prefs.voiceEnabled(context)) {
            com.ex2tools.iv.voice.VoiceService.start(context);
        }
    }

    private static boolean isUserUnlocked(Context context) {
        try {
            UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            return um == null || um.isUserUnlocked();
        } catch (Throwable t) {
            return true;
        }
    }
}


