package com.ex2tools.iv;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.servicemode.RestoreAlarmReceiver;

public final class NotificationHelper {

    public static final int ID_DRIVE = 2101;
    public static final int ID_SERVICE = 2102;
    public static final int ID_TRIGGER = 2103;
    public static final int ID_STATUS = 2104;
    public static final int ID_BOTTOM = 2105;
    public static final int ID_SCREENSHOT_BUTTON = 2106;
    public static final int ID_SCREENSHOT_CAPTURE = 2107;
    public static final int ID_TASK_BUTTON = 2108;
    public static final int ID_WIDGETS = 2109;
    public static final int ID_DRIVE_WATCH = 2110;
    public static final int ID_TRIP_RESET = 2111;
    public static final int ID_VOICE = 2112;
    public static final int ID_OVERLAY = 2113;

    private NotificationHelper() {
    }

    public static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 2000, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent restoreAction(Context c) {
        Intent i = new Intent(c, RestoreAlarmReceiver.class);
        i.setAction(Actions.RESTORE);
        return PendingIntent.getBroadcast(c, 2001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static Notification voice(Context c) {
        return base(c, App.CH_VOICE, R.drawable.ic_voice,
                c.getString(R.string.voice_notification_title),
                c.getString(R.string.voice_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification drive(Context c) {
        return base(c, App.CH_DRIVE, R.drawable.ic_car,
                c.getString(R.string.drive_notification_title),
                c.getString(R.string.drive_notification_text))
                .setOngoing(true)
                .build();
    }

    public static Notification service(Context c) {
        return base(c, App.CH_SERVICE, R.drawable.ic_service,
                c.getString(R.string.service_notification_title),
                c.getString(R.string.service_notification_text))
                .setOngoing(true)
                .setContentIntent(restoreAction(c))
                .addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_restore,
                        c.getString(R.string.restore_action),
                        restoreAction(c)).build())
                .build();
    }

    public static Notification trigger(Context c) {
        return base(c, App.CH_TRIGGER, R.drawable.ic_restore,
                c.getString(R.string.trigger_notification_title),
                c.getString(R.string.trigger_notification_text))
                .setOngoing(true)
                .setContentIntent(restoreAction(c))
                .addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_restore,
                        c.getString(R.string.restore_action),
                        restoreAction(c)).build())
                .build();
    }

    public static Notification status(Context c) {
        return base(c, App.CH_STATUS, R.drawable.ic_blank,
                c.getString(R.string.app_name),
                c.getString(R.string.status_channel_text))
                .setOngoing(true)
                .build();
    }

    public static Notification screenshotButton(Context c) {
        return base(c, App.CH_SCREENSHOT, R.drawable.ic_camera,
                c.getString(R.string.screenshot_button_notification_title),
                c.getString(R.string.screenshot_button_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification screenshotCapture(Context c) {
        return base(c, App.CH_SCREENSHOT, R.drawable.ic_camera,
                c.getString(R.string.screenshot_capture_title),
                c.getString(R.string.screenshot_capture_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification taskButton(Context c) {
        return base(c, App.CH_TASKS, R.drawable.ic_tasks,
                c.getString(R.string.task_notification_title),
                c.getString(R.string.task_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification tripReset(Context c) {
        return base(c, App.CH_TRIGGER, R.drawable.ic_road,
                c.getString(R.string.trip_reset_notification_title),
                c.getString(R.string.trip_reset_notification_text))
                .setOngoing(true)
                .build();
    }

    public static Notification widgets(Context c) {
        return base(c, App.CH_WIDGETS, R.drawable.ic_tasks,
                c.getString(R.string.widget_notification_title),
                c.getString(R.string.widget_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification overlay(Context c) {
        return base(c, App.CH_WIDGETS, R.drawable.ic_widgets,
                c.getString(R.string.overlay_notification_title),
                c.getString(R.string.overlay_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification driveWatch(Context c) {
        return base(c, App.CH_DRIVE, R.drawable.ic_car,
                c.getString(R.string.drive_watch_title),
                c.getString(R.string.drive_watch_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    public static Notification bottom(Context c) {
        return base(c, App.CH_BOTTOM, R.drawable.ic_battery,
                c.getString(R.string.bottom_notification_title),
                c.getString(R.string.bottom_notification_text))
                .setOngoing(true)
                .setContentIntent(openApp(c))
                .build();
    }

    private static NotificationCompat.Builder base(Context c, String channel,
                                                   int icon, String title, String text) {
        return new NotificationCompat.Builder(c, channel)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(ContextCompat.getColor(c, R.color.accent))
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(openApp(c));
    }
}



