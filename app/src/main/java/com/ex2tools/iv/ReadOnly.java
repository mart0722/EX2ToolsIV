package com.ex2tools.iv;

import android.content.Context;

/**
 * Test build switch. While enabled, the app performs no writes to the car
 * and no system mounts. Reads and UI remain fully functional.
 */
public final class ReadOnly {

    public static final boolean ENABLED = true;

    public static boolean blocked(Context c, String action) {
        if (!ENABLED) return false;
        LogStore.add(c, "read-only blocked: " + action);
        return true;
    }

    private ReadOnly() {
    }
}
