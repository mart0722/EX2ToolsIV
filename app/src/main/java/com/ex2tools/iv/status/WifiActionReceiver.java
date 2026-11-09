package com.ex2tools.iv.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import com.ex2tools.iv.LogStore;

public final class WifiActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) {
                wm.setWifiEnabled(true);
            }
        } catch (Throwable t) {
            LogStore.add(context, "wifi enable failed: " + t);
        }
        try {
            Intent settings = new Intent(Settings.ACTION_WIFI_SETTINGS);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
        } catch (Throwable t) {
            LogStore.add(context, "wifi settings failed: " + t);
        }
    }
}
