package com.ex2tools.iv.servicemode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ex2tools.iv.LogStore;

/** Starts user services a few seconds after service mode has been restored. */
public final class RestoreServicesReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ServiceModeManager.isActive(context)) {
            LogStore.add(context, "restore services skipped: service mode active");
            return;
        }
        LogStore.add(context, "restore services receiver");
        ServiceModeManager.restoreUserServices(context);
    }
}
