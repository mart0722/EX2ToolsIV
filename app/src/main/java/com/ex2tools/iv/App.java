package com.ex2tools.iv;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public final class App extends Application {

    public static final String CH_DRIVE = "ex2_drive";
    public static final String CH_SERVICE = "ex2_service";
    public static final String CH_STATUS = "ex2_status";
    public static final String CH_WIFI = "central_ex_status_wifi";
    public static final String CH_BATTERY = "central_ex_status_temp";
    public static final String CH_TRIGGER = "ex2_trigger";
    public static final String CH_BOTTOM = "ex2_bottom";
    public static final String CH_SCREENSHOT = "ex2_screenshot";
    public static final String CH_TASKS = "ex2_tasks";
    public static final String CH_WIDGETS = "ex2_widgets";
    public static final String CH_VOICE = "ex2_voice";
    public static final String CH_CRASH = "ex2_crash";

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashLogger();
        try {
            int mode = Prefs.themeMode(this);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    mode == 1 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                            : mode == 2 ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                            : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } catch (Throwable t) {
            LogStore.add(this, "theme apply failed: " + t);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            nm.createNotificationChannel(channel(CH_DRIVE, getString(R.string.notification_channel_drive)));
            nm.createNotificationChannel(channel(CH_SERVICE, getString(R.string.notification_channel_service)));
            nm.createNotificationChannel(channel(CH_STATUS, getString(R.string.notification_channel_status)));
            nm.createNotificationChannel(channel(CH_WIFI, getString(R.string.status_wifi_desc)));
            nm.createNotificationChannel(channel(CH_BATTERY, getString(R.string.status_battery_desc)));
            nm.createNotificationChannel(channel(CH_TRIGGER, getString(R.string.notification_channel_trigger)));
            nm.createNotificationChannel(channel(CH_BOTTOM, getString(R.string.notification_channel_bottom)));
            nm.createNotificationChannel(channel(CH_SCREENSHOT, getString(R.string.notification_channel_screenshot)));
            nm.createNotificationChannel(channel(CH_TASKS, getString(R.string.task_channel)));
            nm.createNotificationChannel(channel(CH_WIDGETS, getString(R.string.widget_channel)));
            nm.createNotificationChannel(channel(CH_VOICE, getString(R.string.voice_channel)));
            NotificationChannel crash = new NotificationChannel(CH_CRASH, "Ошибки EX2ToolsIV",
                    NotificationManager.IMPORTANCE_HIGH);
            crash.setShowBadge(true);
            nm.createNotificationChannel(crash);
        }
    }

    private NotificationChannel channel(String id, String name) {
        NotificationChannel ch = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        return ch;
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            String trace = android.util.Log.getStackTraceString(error);
            try {
                LogStore.add(this, "CRASH " + thread.getName() + ": " + trace);
            } catch (Throwable ignored) {
            }
            try {
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(getFilesDir(), "last_crash.txt"), false);
                fw.write(trace);
                fw.close();
            } catch (Throwable ignored) {
            }
            try {
                java.io.File dir = getExternalFilesDir(null);
                if (dir != null) {
                    if (!dir.exists()) dir.mkdirs();
                    java.io.FileWriter fw = new java.io.FileWriter(
                            new java.io.File(dir, "last_crash.txt"), false);
                    fw.write(trace);
                    fw.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    android.app.Notification n = new android.app.Notification.Builder(this, CH_CRASH)
                            .setSmallIcon(R.drawable.ic_info)
                            .setContentTitle("EX2ToolsIV: ошибка")
                            .setContentText(String.valueOf(error))
                            .setStyle(new android.app.Notification.BigTextStyle().bigText(trace))
                            .setAutoCancel(true)
                            .build();
                    nm.notify(9999, n);
                }
            } catch (Throwable ignored) {
            }
            if (previous != null && previous != Thread.getDefaultUncaughtExceptionHandler()) {
                previous.uncaughtException(thread, error);
            }
        });
    }
}



