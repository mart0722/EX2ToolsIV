package com.ex2tools.iv.tasks;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Debug;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.servicemode.ServiceModeManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Tasks {

    private Tasks() {
    }

    public static List<TaskEntry> load(Context c) {
        List<TaskEntry> out = new ArrayList<>();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return out;

        PackageManager pm = c.getPackageManager();
        String launcher = launcherPackage(pm);
        Set<String> locked = Prefs.lockedApps(c);
        Map<String, Integer> recent = recentTaskIds(c, am);
        Map<String, Integer> running = runningProcesses(c, am, pm);
        Map<Integer, Long> memory = memoryByPid(am, running.values());

        Map<String, TaskEntry> byPackage = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> item : running.entrySet()) {
            int pid = item.getValue();
            addPackage(c, pm, byPackage, item.getKey(),
                    recent.getOrDefault(item.getKey(), -1), pid,
                    memory.getOrDefault(pid, 0L), locked, launcher);
        }
        for (Map.Entry<String, Integer> item : recent.entrySet()) {
            String pkg = item.getKey();
            if (byPackage.containsKey(pkg)) continue;
            addPackage(c, pm, byPackage, pkg, item.getValue(), -1, 0L, locked, launcher);
        }

        out.addAll(byPackage.values());
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /**
     * Closes a running application. Protected applications are intentionally not
     * blocked here: protection only keeps them out of the default bulk selection,
     * while an explicit manual close must still work.
     */
    public static boolean close(Context c, TaskEntry entry) {
        if (entry == null || entry.packageName == null || entry.packageName.isEmpty()) {
            return false;
        }
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        boolean ok = false;
        boolean forceStopped = false;

        if (entry.taskId >= 0) {
            try {
                ActivityManager.class.getMethod("removeTask", int.class)
                        .invoke(am, entry.taskId);
                ok = true;
            } catch (Throwable t) {
                LogStore.add(c, "tasks: removeTask failed " + entry.packageName + ": " + t);
            }
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
            forceStopped = true;
        } catch (Throwable ignored) {
        }

        if (!forceStopped && isSafePackageName(entry.packageName)) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", "am force-stop " + entry.packageName
                });
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroy();
                } else {
                    ok = ok || p.exitValue() == 0;
                }
            } catch (Throwable t) {
                LogStore.add(c, "tasks: am force-stop failed " + entry.packageName + ": " + t);
            }
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

    private static Map<String, Integer> recentTaskIds(Context c, ActivityManager am) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<?> recents;
        try {
            Object raw = ActivityManager.class
                    .getMethod("getRecentTasks", int.class, int.class)
                    .invoke(am, 40, 0);
            recents = raw instanceof List ? (List<?>) raw : null;
        } catch (Throwable t) {
            LogStore.add(c, "tasks: getRecentTasks failed: " + t);
            return out;
        }
        if (recents == null) return out;

        for (Object raw : recents) {
            if (raw == null) continue;
            Intent base = intentField(raw, "baseIntent");
            if (base == null) continue;
            ComponentName component = base.getComponent();
            String pkg = component == null ? null : component.getPackageName();
            if (pkg == null || pkg.isEmpty() || out.containsKey(pkg)) continue;

            int taskId = intField(raw, "taskId");
            if (taskId < 0) taskId = intField(raw, "id");
            if (taskId >= 0) out.put(pkg, taskId);
        }
        return out;
    }

    private static Map<String, Integer> runningProcesses(Context c, ActivityManager am,
                                                         PackageManager pm) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ActivityManager.RunningAppProcessInfo> processes;
        try {
            processes = am.getRunningAppProcesses();
        } catch (Throwable t) {
            LogStore.add(c, "tasks: getRunningAppProcesses failed: " + t);
            return out;
        }
        if (processes == null) return out;

        for (ActivityManager.RunningAppProcessInfo proc : processes) {
            if (proc == null || proc.processName == null) continue;
            String pkg = packageForProcess(pm, proc);
            if (pkg == null || pkg.isEmpty()) continue;
            if (!out.containsKey(pkg)) out.put(pkg, proc.pid);
        }
        return out;
    }

    private static String packageForProcess(PackageManager pm,
                                             ActivityManager.RunningAppProcessInfo proc) {
        String process = proc.processName;
        try {
            pm.getApplicationInfo(process, 0);
            return process;
        } catch (Throwable ignored) {
        }

        try {
            String[] packages = pm.getPackagesForUid(proc.uid);
            if (packages == null || packages.length == 0) return null;
            for (String pkg : packages) {
                if (process.equals(pkg) || process.startsWith(pkg + ":")) return pkg;
            }
            return packages[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<Integer, Long> memoryByPid(ActivityManager am,
                                                   Iterable<Integer> pids) {
        Map<Integer, Long> out = new HashMap<>();
        Set<Integer> unique = new HashSet<>();
        for (Integer pid : pids) {
            if (pid != null && pid > 0) unique.add(pid);
        }
        if (unique.isEmpty()) return out;

        int[] values = new int[unique.size()];
        int i = 0;
        for (Integer pid : unique) values[i++] = pid;
        try {
            Debug.MemoryInfo[] infos = am.getProcessMemoryInfo(values);
            if (infos == null) return out;
            for (int n = 0; n < values.length && n < infos.length; n++) {
                if (infos[n] != null) out.put(values[n], (long) infos[n].getTotalPss());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void addPackage(Context c, PackageManager pm,
                                   Map<String, TaskEntry> out, String pkg,
                                   int taskId, int pid, long memKb,
                                   Set<String> locked, String launcher) {
        if (pkg == null || pkg.isEmpty()) return;
        if (pkg.equals(c.getPackageName())) return;
        if (pkg.equals(launcher)) return;
        if (pkg.startsWith("com.flyme.")) return;
        if (ServiceModeManager.isEssential(c, pkg)) return;

        ApplicationInfo info;
        try {
            info = pm.getApplicationInfo(pkg, 0);
        } catch (Throwable ignored) {
            return;
        }
        if ((info.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            return;
        }

        String label = pkg;
        Drawable icon = null;
        try {
            label = String.valueOf(info.loadLabel(pm));
            icon = info.loadIcon(pm);
        } catch (Throwable ignored) {
        }
        out.put(pkg, new TaskEntry(taskId, pkg, label, icon,
                locked.contains(pkg), pid, memKb));
    }

    private static boolean isSafePackageName(String pkg) {
        for (int i = 0; i < pkg.length(); i++) {
            char ch = pkg.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_') continue;
            return false;
        }
        return !pkg.isEmpty();
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
