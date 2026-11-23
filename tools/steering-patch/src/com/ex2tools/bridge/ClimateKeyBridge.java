package com.ex2tools.bridge;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.HashSet;
import java.util.Set;

/** Injected into this specific keyserver build; default is always native handling. */
public final class ClimateKeyBridge {
    private static final Set<Integer> held = new HashSet<>();
    public static synchronized boolean intercept(Context c, int property, int state) {
        int key;
        switch (property) {
            case 557884992: case 1090650368: key = 1; break;
            case 557884993: case 1090650880: key = 2; break;
            case 557884995: case 1090650624: key = 3; break;
            case 557884994: case 1090651136: key = 4; break;
            default: return false;
        }
        try {
            long now = SystemClock.elapsedRealtime();
            long heartbeat = Settings.Global.getLong(c.getContentResolver(), "ex2_climate_heartbeat", 0);
            long expiry = Settings.Global.getLong(c.getContentResolver(), "ex2_climate_expiry", 0);
            boolean active = heartbeat > 0 && heartbeat <= now && now - heartbeat < 3000
                    && expiry > now && expiry - now <= 15000;
            // Preserve a consumed press through release, even if the lease expires mid-press.
            if (state == 1) {
                if (!active) return false;
                if (held.add(property)) {
                    Intent i = new Intent("com.ex2tools.iv.CLIMATE_STEERING_KEY");
                    i.setPackage("com.ex2tools.iv");
                    i.putExtra("key", key);
                    c.sendBroadcast(i, "android.permission.WRITE_SECURE_SETTINGS");
                }
                return true;
            }
            boolean consumed = held.contains(property);
            // Adapted event uses 2 for release; raw MCU uses 3. Swallow repeats too.
            if (state == 0 || state == (property >= 1000000000 ? 2 : 3)) held.remove(property);
            return consumed;
        } catch (Throwable ignored) {
            held.remove(property);
            return false;
        }
    }
}
