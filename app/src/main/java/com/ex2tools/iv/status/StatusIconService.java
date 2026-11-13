package com.ex2tools.iv.status;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.Telemetry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StatusIconService extends Service {

    private static final int WATCHDOG_ID = 15030;

    private static final int NOTIF_TEMP = 15021;
    private static final int NOTIF_BATTERY = 15020;
    private static final int NOTIF_TOTAL = 15028;
    private static final int NOTIF_TRIP = 15026;
    private static final int NOTIF_WIFI = 15024;
    private static final int NOTIF_BRIGHTNESS = 15022;

    private static final int ICON_TEMP = 15;
    private static final int ICON_BATTERY = 97;
    private static final int ICON_TOTAL = 96;
    private static final int ICON_TRIP = 94;
    private static final int ICON_WIFI = 98;
    private static final int ICON_BRIGHTNESS = 99;

    private static final int RANK_TEMP = 22;
    private static final int RANK_TRIP = 23;
    private static final int RANK_BRIGHTNESS = 30;
    private static final int RANK_WIFI = 31;
    private static final int RANK_BATTERY = 32;
    private static final int RANK_TOTAL = 33;

    private static final String CHANNEL_TEMP = "central_ex_status_temp";
    private static final String CHANNEL_WIFI = "central_ex_status_wifi";
    private static final String CHANNEL_BRIGHTNESS = "central_ex_status_brightness";

    private static final int WIFI_OFF = 0;
    private static final int WIFI_SEARCHING = 1;
    private static final int WIFI_LEVEL0 = 2;
    private static final int WIFI_LEVEL1 = 3;
    private static final int WIFI_LEVEL2 = 4;
    private static final int WIFI_LEVEL3 = 5;
    private static final int WIFI_LEVEL4 = 6;

    private static final int BAT_GREEN = -14696587;
    private static final int BAT_ORANGE = -30208;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Map<Integer, Integer> lastRank = new HashMap<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            requestUpdate();
            handler.postDelayed(this, Math.max(3, Prefs.intervalSec(StatusIconService.this)) * 1000L);
        }
    };

    public static void start(Context c) {
        start(c, false);
    }

    public static void refresh(Context c) {
        start(c, true);
    }

    private static void start(Context c, boolean repost) {
        try {
            Intent i = new Intent(c, StatusIconService.class);
            i.putExtra("repost", repost);
            ContextCompat.startForegroundService(c, i);
        } catch (Throwable t) {
            LogStore.add(c, "start status icons failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, StatusIconService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context c) {
        if (Prefs.statusAny(c)) start(c);
        else stop(c);
    }

    public static void cancelNotifications(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            for (int id : new int[]{NOTIF_TEMP, NOTIF_BATTERY, NOTIF_TOTAL, NOTIF_TRIP,
                    NOTIF_WIFI, NOTIF_BRIGHTNESS, 18020, 18024, 18026, 18028, 18021}) {
                nm.cancel(id);
            }
            for (int base = 16000; base <= 16990; base += 10) {
                nm.cancel(base + 20);
                nm.cancel(base + 24);
                nm.cancel(base + 26);
                nm.cancel(base + 28);
                nm.cancel(base + 21);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void test(Context c) {
        start(c, true);
        Toast.makeText(c, R.string.status_test_posted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(WATCHDOG_ID, NotificationHelper.status(this));
        createChannels();
        clearOldNotifications();
        cancelAll();
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean repost = intent != null && intent.getBooleanExtra("repost", false);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, repost ? 300L : 0L);
        return START_STICKY;
    }

    private void requestUpdate() {
        if (!busy.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                updateInner();
            } catch (Throwable t) {
                LogStore.add(this, "status update error: " + android.util.Log.getStackTraceString(t));
            } finally {
                busy.set(false);
            }
        });
    }

    private void updateInner() {
        if (!Prefs.statusAny(this)) {
            cancelAll();
            stopSelf();
            return;
        }
        if (Prefs.statusTemp(this)) {
            postTemp();
        } else {
            cancel(NOTIF_TEMP);
        }
        if (Prefs.statusBattery(this)) {
            postBattery();
        } else {
            cancel(NOTIF_BATTERY);
        }
        if (Prefs.statusTotalKm(this)) {
            postTotal();
        } else {
            cancel(NOTIF_TOTAL);
        }
        if (Prefs.statusTripKm(this)) {
            postTrip();
        } else {
            cancel(NOTIF_TRIP);
        }
        if (Prefs.statusWifi(this)) {
            postWifi();
        } else {
            cancel(NOTIF_WIFI);
        }
        if (Prefs.statusBrightness(this)) {
            postBrightness();
        } else {
            cancel(NOTIF_BRIGHTNESS);
        }
    }

    private void postTemp() {
        float value = Telemetry.outsideTemp(this);
        boolean valid = !Float.isNaN(value);
        String formatted = String.format(Locale.US, "%.1f", value);
        String main = valid ? formatted : "?";
        String unit = valid ? "°C" : "";
        int color = -1;
        Icon icon = textIcon(main, unit, color, 10, true, false);
        String text = valid
                ? getString(R.string.status_temp_text, formatted)
                : getString(R.string.status_unavailable);
        postStatusIcon(NOTIF_TEMP, ICON_TEMP, RANK_TEMP,
                "StatusIcon_OutsideTemperature", CHANNEL_TEMP,
                getString(R.string.status_temp_title), text, icon,
                NotificationHelper.openApp(this));
    }

    private void postBattery() {
        int soc = Telemetry.soc(this);
        boolean valid = soc >= 0;
        String main = valid ? String.valueOf(soc) : "?";
        String unit = valid ? "%" : "";
        int color = !valid ? -1 : soc >= 80 ? BAT_GREEN : soc < 20 ? BAT_ORANGE : -1;
        Icon icon = textIcon(main, unit, color, 10, true, false);
        String text = valid
                ? getString(R.string.battery_value, soc)
                : getString(R.string.status_unavailable);
        postStatusIcon(NOTIF_BATTERY, ICON_BATTERY, RANK_BATTERY,
                "StatusIcon_BatteryPercent", CHANNEL_TEMP,
                getString(R.string.status_battery_desc), text, icon,
                NotificationHelper.openApp(this));
    }

    private void postTotal() {
        float odo = Telemetry.odometerKm(this);
        boolean valid = !Float.isNaN(odo);
        String main = valid ? String.valueOf(Math.round(odo)) : "?";
        Icon icon = textIcon(main, "", -1, 2, false, true);
        String text = valid
                ? getString(R.string.status_total_text, Math.round(odo))
                : getString(R.string.status_unavailable);
        postStatusIcon(NOTIF_TOTAL, ICON_TOTAL, RANK_TOTAL,
                "StatusIcon_TotalMileage", CHANNEL_TEMP,
                getString(R.string.status_total_title), text, icon,
                NotificationHelper.openApp(this));
    }

    private void postTrip() {
        float odo = Telemetry.odometerKm(this);
        boolean valid = !Float.isNaN(odo);
        int trip = 0;
        if (valid) {
            float start = Prefs.tripStartKm(this);
            if (start <= 0f) {
                start = odo;
                Prefs.setTripStartKm(this, start);
            }
            trip = Math.round(Math.max(0f, odo - start));
        }
        String main = valid ? String.valueOf(trip) : "?";
        Icon icon = textIcon(main, "", -1, 2, false, true);
        String text = valid
                ? getString(R.string.status_trip_text, trip)
                : getString(R.string.status_unavailable);
        Intent reset = new Intent(this, TripResetService.class);
        PendingIntent pending = PendingIntent.getForegroundService(this, ICON_TRIP, reset,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        postStatusIcon(NOTIF_TRIP, ICON_TRIP, RANK_TRIP,
                "StatusIcon_TripMileage", CHANNEL_TEMP,
                getString(R.string.status_trip_title), text, icon, pending);
    }

    private void postWifi() {
        int level = computeWifiLevel();
        Icon icon = Icon.createWithBitmap(wifiBitmap(level));
        String text = level == WIFI_OFF
                ? getString(R.string.status_wifi_off)
                : level == WIFI_SEARCHING
                ? getString(R.string.status_wifi_searching)
                : getString(R.string.status_wifi_on);
        PendingIntent pending = PendingIntent.getBroadcast(this, ICON_WIFI,
                new Intent(this, WifiActionReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        postStatusIcon(NOTIF_WIFI, ICON_WIFI, RANK_WIFI,
                "StatusIcon_WifiApply", CHANNEL_WIFI,
                getString(R.string.status_wifi_title), text, icon, pending);
    }

    private void postBrightness() {
        Icon icon = Icon.createWithBitmap(brightnessBitmap());
        PendingIntent pending = PendingIntent.getBroadcast(this, ICON_BRIGHTNESS,
                new Intent(this, BrightnessActionReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        postStatusIcon(NOTIF_BRIGHTNESS, ICON_BRIGHTNESS, RANK_BRIGHTNESS,
                "StatusIcon_BrightnessApply", CHANNEL_BRIGHTNESS,
                getString(R.string.status_brightness_title),
                getString(R.string.status_brightness_text), icon, pending);
    }

    private void postStatusIcon(int notifId, int iconId, int rank, String describe,
                                String channel, String title, String text,
                                Icon icon, PendingIntent pending) {
        Integer previous = lastRank.get(notifId);
        if (previous != null && previous != rank) cancel(notifId);
        lastRank.put(notifId, rank);
        try {
            Notification notification = new Notification.Builder(this, channel)
                    .setSmallIcon(icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            Bundle bundle = notification.extras;
            bundle.putBoolean("flag_status_icon_notification", true);
            bundle.putInt("flag_status_icon_id", iconId);
            bundle.putString("flag_status_icon_describe", describe);
            bundle.putParcelable("flag_status_icon_icon", icon);
            bundle.putParcelable("flag_status_icon_pressed_icon", icon);
            bundle.putBoolean("flag_status_icon_hide", false);
            bundle.putInt("flag_status_icon_rank", rank);
            bundle.putInt("flag_status_icon_space_x", 1);
            bundle.putBoolean("flag_status_icon_is_pick_on", false);
            bundle.putInt("flag_status_icon_specific_width", 0);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            LogStore.add(this, "status " + describe + " notif=" + notifId
                    + " icon=" + iconId + " rank=" + rank + " text=" + text);
            nm.notify(notifId, notification);
        } catch (Throwable t) {
            LogStore.add(this, "status icon " + describe + " failed: " + t);
        }
    }

    private void createChannels() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            createChannel(nm, CHANNEL_TEMP, getString(R.string.status_channel_temp));
            createChannel(nm, CHANNEL_WIFI, getString(R.string.status_channel_wifi));
            createChannel(nm, CHANNEL_BRIGHTNESS, getString(R.string.status_channel_brightness));
        } catch (Throwable t) {
            LogStore.add(this, "status channels failed: " + t);
        }
    }

    private void createChannel(NotificationManager nm, String id, String name) {
        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private int computeWifiLevel() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return WIFI_OFF;
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                    .getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (info == null || !info.isConnected()) return WIFI_SEARCHING;
            WifiInfo wifiInfo = wm.getConnectionInfo();
            if (wifiInfo == null) return WIFI_LEVEL4;
            int rssi = wifiInfo.getRssi();
            if (rssi <= -100 || rssi >= 0) return WIFI_SEARCHING;
            int level = WifiManager.calculateSignalLevel(rssi, 5);
            if (level == 0) return WIFI_LEVEL0;
            if (level == 1) return WIFI_LEVEL1;
            if (level == 2) return WIFI_LEVEL2;
            if (level == 3) return WIFI_LEVEL3;
            return WIFI_LEVEL4;
        } catch (Throwable t) {
            return WIFI_OFF;
        }
    }

    private Bitmap wifiBitmap(int level) {
        float base = 36f * getResources().getDisplayMetrics().density;
        int size = Math.max((int) (base / 0.61f), 1);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float center = size / 2f;
        float dotY = center + 0.3f * base;
        int active = level == WIFI_LEVEL1 ? 1 : level == WIFI_LEVEL2 ? 2
                : level == WIFI_LEVEL3 ? 3 : level == WIFI_LEVEL4 ? 4 : 0;
        int dotColor = level == WIFI_OFF ? Color.argb(140, 255, 255, 255)
                : level == WIFI_SEARCHING ? -7829368 : -1;
        boolean connected = level >= WIFI_LEVEL1 && level <= WIFI_LEVEL4;

        Paint paint = new Paint(1);
        paint.setColor(dotColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(center, dotY, 0.07f * base, paint);

        paint = new Paint(1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.075f * base);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 1; i < 5; i++) {
            float radius = (i * 0.105f + 0.1f) * base;
            RectF rect = new RectF(center - radius, dotY - radius, center + radius, dotY + radius);
            paint.setColor(connected && i <= active
                    ? Color.WHITE
                    : Color.argb(70, 255, 255, 255));
            canvas.drawArc(rect, 215f, 110f, false, paint);
        }
        return bitmap;
    }

    private Bitmap brightnessBitmap() {
        float base = 36f * getResources().getDisplayMetrics().density;
        int size = Math.max((int) (base / 0.61f), 1);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(1);
        paint.setColor(-1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.06f * base);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float center = size / 2f;
        float r0 = base * 0.32f;
        float r1 = base * 0.46f;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(center + cos * r0, center + sin * r0,
                    center + cos * r1, center + sin * r1, paint);
        }
        Paint fill = new Paint(1);
        fill.setColor(-1);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(center, center, base * 0.22f, fill);
        return bitmap;
    }

    private Icon textIcon(String main, String unit, int color, int padHdp,
                          boolean useFloor, boolean kmFloor) {
        Paint paint = new Paint(129);
        paint.setColor(color);
        paint.setTypeface(Typeface.DEFAULT);
        float base = dp(56);
        float small = base * 0.82f;
        paint.setTextSize(base);
        float mainWidth = paint.measureText(main);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float unitWidth = 0f;
        float gap = 0f;
        if (unit != null && !unit.isEmpty()) {
            paint.setTextSize(small);
            unitWidth = paint.measureText(unit);
            gap = dp(3);
        }
        int height = Math.max((int) (textHeight / 0.61f), 1);
        int pad = dp(padHdp);
        int contentWidth = (int) (mainWidth + gap + unitWidth) + pad * 2;
        int reference = kmFloor ? referenceWidthKm() : useFloor ? referenceWidth() : 0;
        int width = Math.max(Math.max(contentWidth, reference), 1);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float baseline = ((height - textHeight) / 2f) - metrics.ascent;
        float left = Math.max((width - contentWidth) / 2f, 0f);
        paint.setTextSize(base);
        canvas.drawText(main, pad + left, baseline, paint);
        if (unit != null && !unit.isEmpty()) {
            paint.setTextSize(small);
            canvas.drawText(unit, pad + left + mainWidth + gap, baseline, paint);
        }
        return Icon.createWithBitmap(bitmap);
    }

    private int referenceWidthKm() {
        Paint paint = new Paint(129);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(dp(56));
        return ((int) paint.measureText("99999")) + dp(2) * 2;
    }

    private int referenceWidth() {
        Paint paint = new Paint(129);
        paint.setTypeface(Typeface.DEFAULT);
        float base = dp(56);
        float small = base * 0.82f;
        float gap = dp(3);
        int pad = dp(10);
        float cold = widthFor(paint, base, small, gap, "-40", "°C");
        float hot = widthFor(paint, base, small, gap, "100", "%");
        return (int) Math.max(cold, hot) + pad * 2;
    }

    private float widthFor(Paint paint, float base, float small, float gap,
                           String main, String unit) {
        paint.setTextSize(base);
        float mainWidth = paint.measureText(main);
        paint.setTextSize(small);
        return mainWidth + gap + paint.measureText(unit);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void cancel(int id) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(id);
        } catch (Throwable ignored) {
        }
        lastRank.remove(id);
    }

    private void cancelAll() {
        cancel(NOTIF_TEMP);
        cancel(NOTIF_BATTERY);
        cancel(NOTIF_TOTAL);
        cancel(NOTIF_TRIP);
        cancel(NOTIF_WIFI);
        cancel(NOTIF_BRIGHTNESS);
    }

    private void clearOldNotifications() {
        for (int id : new int[]{18020, 18024, 18026, 18028, 18021}) cancel(id);
        for (int base = 16000; base <= 16990; base += 10) {
            cancel(base + 20);
            cancel(base + 24);
            cancel(base + 26);
            cancel(base + 28);
            cancel(base + 21);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        executor.shutdownNow();
        cancelAll();
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
