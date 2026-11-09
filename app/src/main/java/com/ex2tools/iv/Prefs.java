package com.ex2tools.iv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ex2tools.iv.car.CarProps;

public final class Prefs {

    private static final String FILE = "ex2tools_prefs";
    private static volatile SharedPreferences cached;

    private Prefs() {
    }

    public static int themeMode(Context c) {
        return get(c).getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context c, int mode) {
        get(c).edit().putInt("theme_mode", mode).apply();
    }

    public static SharedPreferences get(Context context) {
        SharedPreferences local = cached;
        if (local != null) return local;
        synchronized (Prefs.class) {
            local = cached;
            if (local != null) return local;
            Context c = context;
            try {
                c = context.createDeviceProtectedStorageContext();
            } catch (Throwable ignored) {
            }
            local = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            cached = local;
            return local;
        }
    }

    public static int drive(Context c) {
        return get(c).getInt("drive", CarProps.DRIVE_COMFORT);
    }

    public static void setDrive(Context c, int value) {
        get(c).edit().putInt("drive", value).apply();
    }

    public static int regen(Context c) {
        return get(c).getInt("regen", CarProps.REGEN_MEDIUM);
    }

    public static void setRegen(Context c, int value) {
        get(c).edit().putInt("regen", value).apply();
    }

    public static boolean autoApply(Context c) {
        return get(c).getBoolean("auto_apply", true);
    }

    public static void setAutoApply(Context c, boolean value) {
        get(c).edit().putBoolean("auto_apply", value).apply();
    }

    public static boolean statusWifi(Context c) {
        SharedPreferences p = get(c);
        if (p.contains("status_wifi_enabled")) return p.getBoolean("status_wifi_enabled", false);
        return p.getBoolean("status_wifi", false);
    }

    public static boolean statusBattery(Context c) {
        SharedPreferences p = get(c);
        if (p.contains("status_battery_enabled")) return p.getBoolean("status_battery_enabled", false);
        return p.getBoolean("status_battery", false);
    }

    public static boolean statusBrightness(Context c) {
        return get(c).getBoolean("status_brightness_enabled", false);
    }

    public static boolean statusTemp(Context c) {
        return get(c).getBoolean("status_temp_ext_enabled", false);
    }

    public static boolean statusTotalKm(Context c) {
        return get(c).getBoolean("status_total_km_enabled", false);
    }

    public static boolean statusTripKm(Context c) {
        return get(c).getBoolean("status_trip_km_enabled", false);
    }

    public static boolean statusAny(Context c) {
        return statusWifi(c) || statusBattery(c) || statusTemp(c)
                || statusTotalKm(c) || statusTripKm(c) || statusBrightness(c);
    }

    public static int statusBrightnessPct(Context c) {
        return Math.max(5, Math.min(100, get(c).getInt("status_brightness_pct", 50)));
    }

    public static void setStatusBrightnessPct(Context c, int value) {
        get(c).edit().putInt("status_brightness_pct", Math.max(5, Math.min(100, value))).apply();
    }

    public static int statusBrightnessSaved(Context c) {
        return get(c).getInt("status_brightness_saved", -1);
    }

    public static void setStatusBrightnessSaved(Context c, int value) {
        get(c).edit().putInt("status_brightness_saved", value).apply();
    }

    public static int statusBrightnessApplied(Context c) {
        return get(c).getInt("status_brightness_applied", 0);
    }

    public static void setStatusBrightnessApplied(Context c, int value) {
        get(c).edit().putInt("status_brightness_applied", value).apply();
    }

    public static boolean statusEnabled(Context c, String key) {
        switch (key) {
            case "wifi":
                return statusWifi(c);
            case "battery":
                return statusBattery(c);
            case "temp":
                return statusTemp(c);
            case "total":
                return statusTotalKm(c);
            case "trip":
                return statusTripKm(c);
            case "brightness":
                return statusBrightness(c);
            default:
                return false;
        }
    }

    public static List<String> statusOrder(Context c) {
        List<String> out = readList(get(c).getString("status_order", "[]"));
        String[] keys = {"wifi", "battery", "temp", "total", "trip", "brightness"};
        for (String key : keys) {
            if (statusEnabled(c, key) && !out.contains(key)) out.add(key);
        }
        out.removeIf(key -> !statusEnabled(c, key));
        return out;
    }

    public static void setStatusOrder(Context c, List<String> order) {
        get(c).edit().putString("status_order", writeList(order)).apply();
    }

    public static void appendStatusOrder(Context c, String key) {
        List<String> order = statusOrder(c);
        if (!order.contains(key)) order.add(key);
        setStatusOrder(c, order);
    }

    public static void removeStatusOrder(Context c, String key) {
        List<String> order = statusOrder(c);
        order.remove(key);
        setStatusOrder(c, order);
    }

    public static int statusNotifBase(Context c) {
        return get(c).getInt("status_notif_base", 16000);
    }

    public static void setStatusNotifBase(Context c, int base) {
        get(c).edit().putInt("status_notif_base", base).apply();
    }

    public static String statusOrderSig(Context c) {
        return get(c).getString("status_order_sig", "");
    }

    public static void setStatusOrderSig(Context c, String sig) {
        get(c).edit().putString("status_order_sig", sig).apply();
    }

    public static boolean legacyStatusIdsCleared(Context c) {
        return get(c).getBoolean("status_legacy_ids_cleared", false);
    }

    public static void setLegacyStatusIdsCleared(Context c) {
        get(c).edit().putBoolean("status_legacy_ids_cleared", true).apply();
    }

    public static boolean oldStatusGenerationsCleared(Context c) {
        return get(c).getBoolean("status_old_generations_cleared", false);
    }

    public static void setOldStatusGenerationsCleared(Context c) {
        get(c).edit().putBoolean("status_old_generations_cleared", true).apply();
    }

    public static float tripStartKm(Context c) {
        return get(c).getFloat("trip_start_odometer_km", -1f);
    }

    public static void setTripStartKm(Context c, float value) {
        get(c).edit().putFloat("trip_start_odometer_km", value).apply();
    }


    public static boolean bottomBattery(Context c) {
        return get(c).getBoolean("bottom_battery", true);
    }

    public static boolean bottomTemp(Context c) {
        return get(c).getBoolean("bottom_temp", true);
    }

    public static boolean bottomAny(Context c) {
        return bottomBattery(c) || bottomTemp(c);
    }

    public static void setBottom(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static int bottomX(Context c) {
        return get(c).getInt("bottom_x", -1);
    }

    public static int bottomY(Context c) {
        return get(c).getInt("bottom_y", -1);
    }

    public static void setBottomPos(Context c, int x, int y) {
        get(c).edit().putInt("bottom_x", x).putInt("bottom_y", y).apply();
    }

    public static boolean screenshotButton(Context c) {
        return get(c).getBoolean("screenshot_button", false);
    }

    public static void setScreenshotButton(Context c, boolean value) {
        get(c).edit().putBoolean("screenshot_button", value).apply();
    }

    public static int screenshotX(Context c) {
        return get(c).getInt("screenshot_x", -1);
    }

    public static int screenshotY(Context c) {
        return get(c).getInt("screenshot_y", -1);
    }

    public static void setScreenshotPos(Context c, int x, int y) {
        get(c).edit().putInt("screenshot_x", x).putInt("screenshot_y", y).apply();
    }

    public static boolean taskButton(Context c) {
        return get(c).getBoolean("task_button", false);
    }

    public static void setTaskButton(Context c, boolean value) {
        get(c).edit().putBoolean("task_button", value).apply();
    }

    public static int taskX(Context c) {
        return get(c).getInt("task_x", -1);
    }

    public static int taskY(Context c) {
        return get(c).getInt("task_y", -1);
    }

    public static void setTaskPos(Context c, int x, int y) {
        get(c).edit().putInt("task_x", x).putInt("task_y", y).apply();
    }

    public static Set<String> lockedApps(Context c) {
        return readSet(get(c).getString("locked_apps", "[]"));
    }

    public static void setLockedApps(Context c, Set<String> value) {
        get(c).edit().putString("locked_apps", writeSet(value)).apply();
    }

    public static boolean hideSelf(Context c) {
        return get(c).getBoolean("hide_self", true);
    }

    public static void setHideSelf(Context c, boolean value) {
        get(c).edit().putBoolean("hide_self", value).apply();
    }

    public static void setStatus(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static int intervalSec(Context c) {
        SharedPreferences p = get(c);
        int v = p.getInt("status_interval_sec", 3);
        if (!p.getBoolean("status_interval_user", false) && v == 5) v = 3;
        return Math.max(3, Math.min(30, v));
    }

    public static void setIntervalSec(Context c, int value) {
        get(c).edit()
                .putInt("status_interval_sec", Math.max(3, Math.min(30, value)))
                .putBoolean("status_interval_user", true)
                .apply();
    }

    public static boolean fallbackPill(Context c) {
        return get(c).getBoolean("fallback_pill", false);
    }

    public static void setFallbackPill(Context c, boolean value) {
        get(c).edit().putBoolean("fallback_pill", value).apply();
    }

    public static boolean triggerLeft(Context c) {
        return get(c).getBoolean("trigger_left", true);
    }

    public static boolean triggerRight(Context c) {
        return get(c).getBoolean("trigger_right", true);
    }

    public static void setTrigger(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static int widgetBgOpacity(Context c) {
        return Math.max(0, Math.min(100, get(c).getInt("widget_bg_opacity_pct", 100)));
    }

    public static void setWidgetBgOpacity(Context c, int value) {
        get(c).edit().putInt("widget_bg_opacity_pct", Math.max(0, Math.min(100, value))).apply();
    }

    public static int widgetSizePct(Context c) {
        return Math.max(50, Math.min(200, get(c).getInt("widget_size_pct", 100)));
    }

    public static void setWidgetSizePct(Context c, int value) {
        get(c).edit().putInt("widget_size_pct", Math.max(50, Math.min(200, value))).apply();
    }

    public static int widgetSizePct(Context c, String key) {
        int fallback = widgetSizePct(c);
        return Math.max(50, Math.min(200,
                get(c).getInt("widget_size_" + key + "_pct", fallback)));
    }

    public static void setWidgetSizePct(Context c, String key, int value) {
        get(c).edit().putInt("widget_size_" + key + "_pct",
                Math.max(50, Math.min(200, value))).apply();
    }

    public static int widgetFanCache(Context c) {
        return get(c).getInt("widget_cache_fan", -1);
    }

    public static long widgetFanCacheTs(Context c) {
        return get(c).getLong("widget_cache_fan_ts", 0L);
    }

    public static void setWidgetFanCache(Context c, int value) {
        get(c).edit()
                .putInt("widget_cache_fan", value)
                .putLong("widget_cache_fan_ts", System.currentTimeMillis())
                .apply();
    }

    public static float widgetTempCache(Context c) {
        return get(c).getFloat("widget_cache_temp", Float.NaN);
    }

    public static long widgetTempCacheTs(Context c) {
        return get(c).getLong("widget_cache_temp_ts", 0L);
    }

    public static void setWidgetTempCache(Context c, float value) {
        get(c).edit()
                .putFloat("widget_cache_temp", value)
                .putLong("widget_cache_temp_ts", System.currentTimeMillis())
                .apply();
    }

    public static int clockFont(Context c) {
        return get(c).getInt("clock_font", 0);
    }

    public static void setClockFont(Context c, int value) {
        get(c).edit().putInt("clock_font", value == 1 ? 1 : 0).apply();
    }

    public static int clockColor(Context c) {
        return get(c).getInt("clock_color", 0);
    }

    public static void setClockColor(Context c, int value) {
        get(c).edit().putInt("clock_color", Math.max(0, Math.min(2, value))).apply();
    }

    public static boolean clockDate(Context c) {
        return get(c).getBoolean("clock_date", false);
    }

    public static void setClockDate(Context c, boolean value) {
        get(c).edit().putBoolean("clock_date", value).apply();
    }

    public static boolean voiceEnabled(Context c) {
        return get(c).getBoolean("voice_enabled", false);
    }

    public static void setVoiceEnabled(Context c, boolean value) {
        get(c).edit().putBoolean("voice_enabled", value).apply();
    }

    public static boolean voiceWake(Context c) {
        return get(c).getBoolean("voice_wake_enabled", true);
    }

    public static void setVoiceWake(Context c, boolean value) {
        get(c).edit().putBoolean("voice_wake_enabled", value).apply();
    }

    public static boolean voiceMicKey(Context c) {
        return get(c).getBoolean("voice_mic_key", true);
    }

    public static void setVoiceMicKey(Context c, boolean value) {
        get(c).edit().putBoolean("voice_mic_key", value).apply();
    }

    public static boolean voiceOverlay(Context c) {
        return get(c).getBoolean("voice_overlay", true);
    }

    public static void setVoiceOverlay(Context c, boolean value) {
        get(c).edit().putBoolean("voice_overlay", value).apply();
    }

    public static String voiceCustomCommands(Context c) {
        return get(c).getString("voice_custom_commands", "[]");
    }

    public static void setVoiceCustomCommands(Context c, String value) {
        get(c).edit().putString("voice_custom_commands", value == null ? "[]" : value).apply();
    }

    public static int triggerTaps(Context c) {
        return get(c).getInt("trigger_taps", 5);
    }

    public static int triggerWindowMs(Context c) {
        return get(c).getInt("trigger_window_ms", 6000);
    }

    public static Set<String> hidden(Context c) {
        return readSet(get(c).getString("hidden_apps", "[]"));
    }

    public static void setHidden(Context c, Set<String> value) {
        get(c).edit().putString("hidden_apps", writeSet(value)).apply();
    }

    public static boolean serviceActive(Context c) {
        return get(c).getBoolean("service_active", false);
    }

    public static void setServiceActive(Context c, boolean value) {
        get(c).edit().putBoolean("service_active", value).apply();
    }

    public static void flush(Context c) {
        get(c).edit().commit();
    }

    public static boolean experimental(Context c) {
        return get(c).getBoolean("experimental", false);
    }

    public static void setExperimental(Context c, boolean value) {
        get(c).edit().putBoolean("experimental", value).apply();
    }

    public static Set<String> excluded(Context c) {
        return readSet(get(c).getString("excluded_apps", "[]"));
    }

    public static void setExcluded(Context c, Set<String> value) {
        get(c).edit().putString("excluded_apps", writeSet(value)).apply();
    }

    public static String lastResult(Context c) {
        return get(c).getString("last_result", "");
    }

    public static void setLastResult(Context c, String value) {
        get(c).edit().putString("last_result", value == null ? "" : value).apply();
    }

    public static String lastApplied(Context c) {
        return get(c).getString("last_applied", "");
    }

    public static void setLastApplied(Context c, String value) {
        get(c).edit().putString("last_applied", value == null ? "" : value).apply();
    }

    public static void reset(Context c) {
        get(c).edit().clear().apply();
    }

    private static Set<String> readSet(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String writeSet(Set<String> set) {
        JSONArray arr = new JSONArray();
        if (set != null) {
            for (String s : set) arr.put(s);
        }
        return arr.toString();
    }

    private static List<String> readList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String writeList(List<String> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (String s : list) arr.put(s);
        }
        return arr.toString();
    }

    public static String toJson(Object... pairs) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                o.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        } catch (Throwable ignored) {
        }
        return o.toString();
    }
}


