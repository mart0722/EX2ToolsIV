package com.ex2tools.iv.tasks;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.servicemode.ServiceModeManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Tasks {

    private Tasks() {
    }

    public static List<TaskEntry> load(Context c) {
        List<TaskEntry> out = new ArrayList<>();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return out;

        List<?> recents;
        try {
            Object raw = ActivityManager.class
                    .getMethod("getRecentTasks", int.class, int.class)
                    .invoke(am, 30, 0);
            recents = raw instanceof List ? (List<?>) raw : null;
        } catch (Throwable t) {
            LogStore.add(c, "tasks: getRecentTasks failed: " + t);
            return out;
        }
        if (recents == null) return out;

        PackageManager pm = c.getPackageManager();
        String launcher = launcherPackage(pm);
        Set<String> locked = Prefs.lockedApps(c);
        Set<String> seen = new HashSet<>();

        for (Object raw : recents) {
            if (raw == null) continue;
            Intent base = intentField(raw, "baseIntent");
            if (base == null) continue;
            ComponentName component = base.getComponent();
            String pkg = component == null ? null : component.getPackageName();
            if (pkg == null || pkg.isEmpty()) continue;
            if (pkg.equals(c.getPackageName())) continue;
            if (pkg.equals(launcher)) continue;
            if (ServiceModeManager.isEssential(c, pkg)) continue;
            if (pkg.startsWith("com.flyme.")) continue;
            if (!seen.add(pkg)) continue;

            int taskId = intField(raw, "taskId");
            if (taskId < 0) taskId = intField(raw, "id");
            if (taskId < 0) continue;

            String label = pkg;
            Drawable icon = null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = String.valueOf(ai.loadLabel(pm));
                icon = ai.loadIcon(pm);
            } catch (Throwable ignored) {
            }
            out.add(new TaskEntry(taskId, pkg, label, icon, locked.contains(pkg)));
        }

        for (String pkg : locked) {
            if (pkg == null || pkg.isEmpty()) continue;
            if (pkg.equals(c.getPackageName())) continue;
            if (pkg.equals(launcher)) continue;
            if (ServiceModeManager.isEssential(c, pkg)) continue;
            if (pkg.startsWith("com.flyme.")) continue;
            if (!seen.add(pkg)) continue;
            String label = pkg;
            Drawable icon = null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = String.valueOf(ai.loadLabel(pm));
                icon = ai.loadIcon(pm);
            } catch (Throwable ignored) {
            }
            out.add(new TaskEntry(-1, pkg, label, icon, true));
        }

        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static boolean close(Context c, TaskEntry entry) {
        if (entry == null || entry.locked) return false;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        boolean ok = false;
        try {
            ActivityManager.class.getMethod("removeTask", int.class)
                    .invoke(am, entry.taskId);
            ok = true;
        } catch (Throwable t) {
            LogStore.add(c, "tasks: removeTask failed " + entry.packageName + ": " + t);
        }
        try {
            am.killBackgroundProcesses(entry.packageName);
            ok = true;
        } catch (Throwable t) {
            LogStore.add(c, "tasks: killBackground failed " + entry.packageName + ": " + t);
        }
        try {
            ActivityManager.class.getMethod("forceStopPackage", String.class)
                    .invoke(am, entry.packageName);
            ok = true;
        } catch (Throwable ignored) {
        }
        LogStore.add(c, "tasks: close " + entry.packageName + " ok=" + ok);
        return ok;
    }

    public static boolean setLocked(Context c, TaskEntry entry, boolean locked) {
        if (entry == null) return false;
        Set<String> locks = new HashSet<>(Prefs.lockedApps(c));
        if (locked) locks.add(entry.packageName);
        else locks.remove(entry.packageName);
        Prefs.setLockedApps(c, locks);
        entry.locked = locked;
        return true;
    }

    private static String launcherPackage(PackageManager pm) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) return ri.activityInfo.packageName;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Intent intentField(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            Object value = f.get(o);
            return value instanceof Intent ? (Intent) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intField(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            return f.getInt(o);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
