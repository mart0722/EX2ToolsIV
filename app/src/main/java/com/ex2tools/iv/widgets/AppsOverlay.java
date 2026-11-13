package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry and app helpers for the "Приложения" floating widget.
 */
final class AppsOverlay {

    static final int CELL_DP = 60;
    static final int GAP_DP = 8;
    static final int PADDING_DP = 12;
    static final int ICON_DP = 56;
    static final int CORNER_DP = 14;

    private static final LruCache<String, Drawable> ICONS = new LruCache<>(32);
    private static long lastVisibleCheck;
    private static String lastVisibleSignature = "";
    private static boolean lastVisible;

    private AppsOverlay() {
    }

    static List<String> selected(Context c) {
        return Prefs.overlayApps(c);
    }

    /** Selected packages which still exist and still have a launcher entry. */
    static List<String> validSelected(Context c) {
        List<String> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String pkg : selected(c)) {
            if (pkg == null || pkg.isEmpty()) continue;
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) out.add(pkg);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    static boolean grid(Context c) {
        return Prefs.overlayAppsLayout(c) == 1;
    }

    /**
     * Cheap periodic check for the service poll: validity is re-evaluated at
     * most every five seconds, so uninstalling an app does not leave an empty
     * card on screen forever.
     */
    static boolean hasVisible(Context c) {
        String signature = selected(c).toString();
        long now = android.os.SystemClock.elapsedRealtime();
        if (signature.equals(lastVisibleSignature) && now - lastVisibleCheck < 5000L) {
            return lastVisible;
        }
        lastVisibleSignature = signature;
        lastVisibleCheck = now;
        lastVisible = !validSelected(c).isEmpty();
        return lastVisible;
    }

    static int columns(Context c, int count) {
        return grid(c) ? columns(count) : Math.max(1, count);
    }

    static int columns(int count) {
        if (count <= 1) return 1;
        if (count == 2) return 2;
        if (count == 3) return 3;
        if (count == 4) return 2;
        return 3;
    }

    /** Base size in dp; the overlay size slider is applied on top of it. */
    static int[] sizeDp(Context c) {
        int count = validSelected(c).size();
        int cols = columns(c, count);
        int rows = count <= 0 ? 1 : (count + cols - 1) / cols;
        int width = PADDING_DP * 2 + cols * CELL_DP + Math.max(0, cols - 1) * GAP_DP;
        int height = PADDING_DP * 2 + rows * CELL_DP + Math.max(0, rows - 1) * GAP_DP;
        return new int[]{width, height};
    }

    static Drawable icon(Context c, String pkg) {
        Drawable cached = ICONS.get(pkg);
        if (cached != null) return cached;
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            if (d != null) ICONS.put(pkg, d);
            return d;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    static void launch(Context c, String pkg) {
        try {
            Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) {
                LogStore.add(c, "apps overlay: no launch intent for " + pkg);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(intent);
        } catch (Throwable t) {
            LogStore.add(c, "apps overlay launch failed: " + pkg + " " + t);
        }
    }
}
