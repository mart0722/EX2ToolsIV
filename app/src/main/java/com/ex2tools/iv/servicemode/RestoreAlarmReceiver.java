package com.ex2tools.iv.servicemode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

public final class RestoreAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Actions.TRIGGER_WATCHDOG.equals(action)) {
            if (ServiceModeManager.isActive(context)
                    && (Prefs.triggerLeft(context) || Prefs.triggerRight(context))
                    && !RestoreTriggerService.isRunning()) {
                LogStore.add(context, "trigger watchdog: restarting");
                ServiceModeManager.startTrigger(context);
            }
            return;
        }
        if (!Actions.RESTORE.equals(action)) return;
        int restored = ServiceModeManager.restoreAll(context);
        LogStore.add(context, "restore receiver: " + action + " -> " + restored);
    }
}
