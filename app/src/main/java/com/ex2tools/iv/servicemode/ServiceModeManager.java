package com.ex2tools.iv.servicemode;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.screenshot.ScreenshotButtonService;
import com.ex2tools.iv.service.DriveRegenService;
import com.ex2tools.iv.service.DriveRegenWatchService;
import com.ex2tools.iv.status.StatusIconService;
import com.ex2tools.iv.status.TripResetService;
import com.ex2tools.iv.tasks.TaskManagerButtonService;
import com.ex2tools.iv.voice.VoiceService;
import com.ex2tools.iv.widgets.WidgetUpdateService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ServiceModeManager {



    private static final Set<String> ESSENTIAL = new HashSet<>(Arrays.asList(
            "com.ex2tools.iv",
            "com.android.systemui",
            "com.android.settings",
            "com.android.bluetooth",
            "com.android.car",
            "com.android.launcher3",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.shell",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.providers.settings"
    ));

    private static final String[] ESSENTIAL_PREFIXES = {
            "com.android.",
            "com.google.android.",
            "com.geely.",
            "com.ecarx.",
            "com.njda."
    };

    private ServiceModeManager() {
    }

    public static boolean isEssential(Context c, String pkg) {
        if (pkg == null) return true;
        if (pkg.equals(c.getPackageName())) return true;
        if (ESSENTIAL.contains(pkg)) return true;
        for (String prefix : ESSENTIAL_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
    }

    public static boolean canUseOverlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static List<AppEntry> listThirdParty(Context c) {
        List<AppEntry> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        Set<String> excluded = Prefs.excluded(c);
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            if (!ai.enabled) continue;
            if (isEssential(c, ai.packageName)) continue;
            String label;
            Drawable icon;
            try {
                label = String.valueOf(ai.loadLabel(pm));
                icon = ai.loadIcon(pm);
            } catch (Throwable t) {
                label = ai.packageName;
                icon = null;
            }
            out.add(new AppEntry(label, ai.packageName, icon, !excluded.contains(ai.packageName)));
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static int hideSelected(Context c, List<String> packages) {
        boolean hideSelf = Prefs.hideSelf(c);
        if ((packages == null || packages.isEmpty()) && !hideSelf) return 0;
        PackageManager pm = c.getPackageManager();
        Set<String> hidden = new LinkedHashSet<>(Prefs.hidden(c));
        int count = 0;
        if (packages != null) {
            for (String pkg : packages) {
                if (isEssential(c, pkg)) continue;
                try {
                    pm.setApplicationEnabledSetting(pkg,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
                    hidden.add(pkg);
                    count++;
                } catch (Throwable t) {
                    LogStore.add(c, "hide failed " + pkg + ": " + t);
                }
            }
        }
        if (count > 0 || hideSelf) {
            Prefs.setServiceActive(c, true);
            Prefs.setHidden(c, hidden);
            setSelfLauncherEnabled(c, !hideSelf);
            Prefs.flush(c);
            stopUserServices(c);
            startTrigger(c);
            scheduleTriggerWatchdog(c);
            if (!canUseOverlay(c)) notifyService(c);
            LogStore.add(c, "service mode: hidden " + count + " apps, self=" + hideSelf);
        }
        return count;
    }

    public static int hideAll(Context c) {
        List<String> packages = new ArrayList<>();
        for (AppEntry e : listThirdParty(c)) {
            if (e.selected) packages.add(e.packageName);
        }
        return hideSelected(c, packages);
    }

    public static int restoreAll(Context c) {
        return restoreAll(c, true);
    }

    public static int restoreAll(Context c, boolean stopTrigger) {
        Set<String> hidden = new LinkedHashSet<>(Prefs.hidden(c));
        int count = 0;
        for (String pkg : hidden) {
            if (enablePackage(c, pkg)) count++;
        }
        Prefs.setServiceActive(c, false);
        Prefs.setHidden(c, new LinkedHashSet<>());
        setSelfLauncherEnabled(c, true);
        Prefs.flush(c);
        cancelTriggerWatchdog(c);
        if (stopTrigger) stopTrigger(c);
        cancelNotification(c);
        LogStore.add(c, "service mode: restored " + count + " apps");
        scheduleServiceRestore(c);
        return count;
    }

    private static boolean enablePackage(Context c, String pkg) {
        LogStore.add(c, "restore pkg " + pkg);
        try {
            Process process = new ProcessBuilder("sh", "-c", "pm enable --user 0 " + pkg)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LogStore.add(c, "restore pkg timeout " + pkg);
                return false;
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Drain output so the child process cannot block.
                }
            }
            int code = process.exitValue();
            LogStore.add(c, "restore pkg " + pkg + " -> " + code);
            if (code == 0) return true;
        } catch (Throwable t) {
            LogStore.add(c, "pm enable failed " + pkg + ": " + t);
        }
        try {
            c.getPackageManager().setApplicationEnabledSetting(pkg,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
            LogStore.add(c, "restore pkg fallback " + pkg);
            return true;
        } catch (Throwable t) {
            LogStore.add(c, "restore failed " + pkg + ": " + t);
            return false;
        }
    }

    private static void scheduleServiceRestore(Context c) {
        try {
            Intent intent = new Intent(c, RestoreServicesReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(c, 2003, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 5000L, pending);
                LogStore.add(c, "restore services scheduled");
            }
        } catch (Throwable t) {
            LogStore.add(c, "schedule restore services failed: " + t);
            restoreUserServices(c);
        }
    }

    private static void setSelfLauncherEnabled(Context c, boolean enabled) {
        try {
            ComponentName cn = new ComponentName(c, MainActivity.class);
            c.getPackageManager().setComponentEnabledSetting(cn,
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
            LogStore.add(c, "self launcher toggle failed: " + t);
        }
    }

    public static boolean isActive(Context c) {
        return Prefs.serviceActive(c) || !Prefs.hidden(c).isEmpty();
    }

    private static void stopUserServices(Context c) {
        try {
            StatusIconService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            StatusIconService.cancelNotifications(c);
        } catch (Throwable ignored) {
        }
        try {
            WidgetUpdateService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            ScreenshotButtonService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            TaskManagerButtonService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            VoiceService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            DriveRegenWatchService.stop(c);
        } catch (Throwable ignored) {
        }
        try {
            c.stopService(new Intent(c, DriveRegenService.class));
        } catch (Throwable ignored) {
        }
        try {
            c.stopService(new Intent(c, TripResetService.class));
        } catch (Throwable ignored) {
        }
        cancelNotifications(c);
    }

    public static void restoreUserServices(Context c) {
        try {
            StatusIconService.sync(c);
        } catch (Throwable t) {
            LogStore.add(c, "restore status icons failed: " + t);
        }
        try {
            WidgetUpdateService.sync(c);
        } catch (Throwable t) {
            LogStore.add(c, "restore widgets failed: " + t);
        }
        try {
            ScreenshotButtonService.sync(c);
        } catch (Throwable t) {
            LogStore.add(c, "restore screenshot button failed: " + t);
        }
        try {
            TaskManagerButtonService.sync(c);
        } catch (Throwable t) {
            LogStore.add(c, "restore task button failed: " + t);
        }
        try {
            DriveRegenWatchService.sync(c);
        } catch (Throwable t) {
            LogStore.add(c, "restore drive watch failed: " + t);
        }
        if (Prefs.voiceEnabled(c)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    VoiceService.start(c);
                } catch (Throwable t) {
                    LogStore.add(c, "restore voice failed: " + t);
                }
            }, 3000L);
        }
    }

    private static void cancelNotifications(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            for (int id : new int[]{
                    NotificationHelper.ID_DRIVE,
                    NotificationHelper.ID_STATUS,
                    NotificationHelper.ID_BOTTOM,
                    NotificationHelper.ID_SCREENSHOT_BUTTON,
                    NotificationHelper.ID_SCREENSHOT_CAPTURE,
                    NotificationHelper.ID_TASK_BUTTON,
                    NotificationHelper.ID_WIDGETS,
                    NotificationHelper.ID_DRIVE_WATCH,
                    NotificationHelper.ID_TRIP_RESET,
                    NotificationHelper.ID_VOICE}) {
                nm.cancel(id);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int hiddenCount(Context c) {
        return Prefs.hidden(c).size();
    }

    public static void onBoot(Context c) {
        if (!isActive(c)) return;
        if (Prefs.triggerLeft(c) || Prefs.triggerRight(c)) {
            startTrigger(c);
            scheduleTriggerWatchdog(c);
        }
        if (!canUseOverlay(c)) notifyService(c);
    }

    public static void startTrigger(Context c) {
        RestoreTriggerService.start(c, false);
    }

    public static void stopTrigger(Context c) {
        try {
            c.stopService(new Intent(c, RestoreTriggerService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void restartTriggerIfActive(Context c) {
        if (!isActive(c)) return;
        stopTrigger(c);
        startTrigger(c);
    }

    public static void testTrigger(Context c) {
        RestoreTriggerService.start(c, true);
    }

    public static void showCorners(Context c) {
        RestoreTriggerService.showCorners(c);
    }

    public static void scheduleTriggerWatchdog(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 120000L,
                    120000L,
                    watchdogIntent(c));
        } catch (Throwable t) {
            LogStore.add(c, "schedule trigger watchdog failed: " + t);
        }
    }

    public static void cancelTriggerWatchdog(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(watchdogIntent(c));
        } catch (Throwable ignored) {
        }
    }

    private static PendingIntent watchdogIntent(Context c) {
        Intent i = new Intent(c, RestoreAlarmReceiver.class);
        i.setAction(Actions.TRIGGER_WATCHDOG);
        return PendingIntent.getBroadcast(c, 2005, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void notifyService(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NotificationHelper.ID_SERVICE, NotificationHelper.service(c));
        } catch (Throwable ignored) {
        }
    }

    public static void cancelNotification(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_SERVICE);
        } catch (Throwable ignored) {
        }
    }
}


