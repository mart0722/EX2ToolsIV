package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

public final class WidgetModes {

    public static final String STOCK_LAUNCHER = "com.flyme.auto.launcher";

    private WidgetModes() {
    }

    public static ResolveInfo homeActivity(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            ResolveInfo info = c.getPackageManager().resolveActivity(
                    home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null
                    && installed(c, info.activityInfo.packageName)
                    && !String.valueOf(info.activityInfo.name).contains("ResolverActivity")) {
                return info;
            }
        } catch (Throwable ignored) {
        }
        try {
            java.util.List<ResolveInfo> all = c.getPackageManager()
                    .queryIntentActivities(home, 0);
            ResolveInfo first = null;
            if (all != null) {
                for (ResolveInfo info : all) {
                    if (info == null || info.activityInfo == null) continue;
                    if (first == null) first = info;
                    if (STOCK_LAUNCHER.equals(info.activityInfo.packageName)) return info;
                }
            }
            return first;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String launcherPackage(Context c) {
        ResolveInfo info = homeActivity(c);
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        }
        return "";
    }

    public static boolean isStockLauncher(Context c) {
        return STOCK_LAUNCHER.equals(launcherPackage(c));
    }

    public static String launcherName(Context c) {
        try {
            ResolveInfo info = homeActivity(c);
            if (info != null && info.activityInfo != null) {
                CharSequence label = info.loadLabel(c.getPackageManager());
                if (label != null && label.length() > 0) return label.toString();
                return info.activityInfo.packageName;
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    public static int detectedMode(Context c) {
        return isStockLauncher(c)
                ? Prefs.WIDGET_MODE_STOCK : Prefs.WIDGET_MODE_THIRD_PARTY;
    }

    public static int current(Context c) {
        int mode = Prefs.widgetMode(c);
        if (mode == Prefs.WIDGET_MODE_UNSET) {
            mode = detectedMode(c);
            Prefs.setWidgetMode(c, mode);
            Prefs.setWidgetModeLauncher(c, launcherPackage(c));
            LogStore.add(c, "widget mode initial: " + mode
                    + " launcher=" + launcherPackage(c));
        }
        return mode;
    }

    /**
     * Follows the current HOME activity. If HOME changes (for example a
     * third-party launcher was uninstalled), the mode is switched even if the
     * user had chosen a mode manually for the previous launcher.
     */
    public static boolean refreshAuto(Context c) {
        String pkg = launcherPackage(c);
        String last = Prefs.widgetModeLauncher(c);
        if (!pkg.equals(last)) {
            int detected = detectedMode(c);
            int old = Prefs.widgetMode(c);
            Prefs.setWidgetMode(c, detected);
            Prefs.setWidgetModeManual(c, false);
            Prefs.setWidgetModeLauncher(c, pkg);
            LogStore.add(c, "widget mode launcher changed: " + last + " -> " + pkg
                    + " mode " + old + " -> " + detected);
            return true;
        }
        if (Prefs.widgetModeManual(c)) return false;
        int detected = detectedMode(c);
        int current = Prefs.widgetMode(c);
        if (current == detected) return false;
        Prefs.setWidgetMode(c, detected);
        LogStore.add(c, "widget mode auto: " + current + " -> " + detected
                + " launcher=" + pkg);
        return true;
    }

    public static void set(Context c, int mode) {
        Prefs.setWidgetMode(c, mode);
        Prefs.setWidgetModeManual(c, true);
        Prefs.setWidgetModeLauncher(c, launcherPackage(c));
        LogStore.add(c, "widget mode manual: " + mode
                + " launcher=" + launcherPackage(c));
        OverlayWidgetService.sync(c);
        WidgetUpdateService.sync(c);
    }
}
