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

    public static boolean fastWidgetResponse(Context c) {
        return get(c).getBoolean("fast_widget_response", false);
    }

    public static void setFastWidgetResponse(Context c, boolean enabled) {
        get(c).edit().putBoolean("fast_widget_response", enabled).apply();
    }

    public static int themeMode(Context c) {
        return get(c).getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context c, int mode) {
        get(c).edit().putInt("theme_mode", mode).apply();
    }

    /** Последняя уставка печки, которую выставлял пользователь. */
    public static int lastTempSet(Context c) {
        return get(c).getInt("last_temp_set", -1);
    }

    public static void setLastTempSet(Context c, int value) {
        get(c).edit().putInt("last_temp_set", value).apply();
    }

    /** Запрет перетаскивания оверлеев. */
    public static boolean overlayLock(Context c) {
        return get(c).getBoolean("overlay_lock", false);
    }

    public static void setOverlayLock(Context c, boolean value) {
        get(c).edit().putBoolean("overlay_lock", value).apply();
    }

    /** Показывать пробег поездки вместо цифр на стрелочном спидометре. */
    public static boolean speedShowTrip(Context c) {
        return get(c).getBoolean("speed_show_trip", false);
    }

    public static void setSpeedShowTrip(Context c, boolean value) {
        get(c).edit().putBoolean("speed_show_trip", value).apply();
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

    public static boolean speedAutoLock(Context c) {
        return get(c).getBoolean("speed_auto_lock_enabled", true);
    }

    public static void setSpeedAutoLock(Context c, boolean value) {
        get(c).edit().putBoolean("speed_auto_lock_enabled", value).apply();
    }

    public static boolean rvcBlackBackground(Context c) {
        return get(c).getBoolean("rvc_black_bg", false);
    }

    public static void setRvcBlackBackground(Context c, boolean value) {
        get(c).edit().putBoolean("rvc_black_bg", value).apply();
    }

    public static boolean ttl65(Context c) {
        return get(c).getBoolean("ttl_65_ativo", false);
    }

    public static void setTtl65(Context c, boolean value) {
        get(c).edit().putBoolean("ttl_65_ativo", value).apply();
    }

    // --- автоблокировка экрана ---

    public static boolean autoLock(Context c) {
        return get(c).getBoolean("auto_lock_enabled", false);
    }

    public static void setAutoLock(Context c, boolean value) {
        get(c).edit().putBoolean("auto_lock_enabled", value).apply();
    }

    public static int autoLockTimeoutSec(Context c) {
        return get(c).getInt("auto_lock_timeout_sec", 60);
    }

    public static void setAutoLockTimeoutSec(Context c, int value) {
        get(c).edit().putInt("auto_lock_timeout_sec", Math.max(15, value)).apply();
    }

    // --- обогрев руля: отложенный запрос (машина включает его только в D) ---

    public static boolean wheelHeatPending(Context c) {
        return get(c).getBoolean("wheel_heat_pending", false);
    }

    public static void setWheelHeatPending(Context c, boolean value) {
        get(c).edit().putBoolean("wheel_heat_pending", value).apply();
    }

    public static long wheelHeatPendingAt(Context c) {
        return get(c).getLong("wheel_heat_pending_at", 0L);
    }

    public static void setWheelHeatPendingAt(Context c, long value) {
        get(c).edit().putLong("wheel_heat_pending_at", value).apply();
    }

    public static long wheelHeatRetryAt(Context c) {
        return get(c).getLong("wheel_heat_retry_at", 0L);
    }

    public static void setWheelHeatRetryAt(Context c, long value) {
        get(c).edit().putLong("wheel_heat_retry_at", value).apply();
    }

    public static boolean statusWifi(Context c) {
        SharedPreferences p = get(c);
        if (p.contains("status_wifi_enabled")) return p.getBoolean("status_wifi_enabled", false);
        return p.getBoolean("status_wifi", false);
    }

    public static boolean wifiAutoOnBoot(Context c) {
        return get(c).getBoolean("wifi_auto_on_boot", false);
    }

    public static void setWifiAutoOnBoot(Context c, boolean value) {
        get(c).edit().putBoolean("wifi_auto_on_boot", value).apply();
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
        SharedPreferences.Editor editor = get(c).edit().putBoolean(key, value);
        if (!value && ("status_wifi_enabled".equals(key) || "status_wifi".equals(key))) {
            editor.putBoolean("wifi_auto_on_boot", false);
        }
        editor.apply();
    }

    public static int intervalSec(Context c) {
        return 3;
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

    public static final int WIDGET_MODE_UNSET = -1;
    public static final int WIDGET_MODE_STOCK = 0;
    public static final int WIDGET_MODE_THIRD_PARTY = 1;

    public static int widgetMode(Context c) {
        return get(c).getInt("widget_mode", WIDGET_MODE_UNSET);
    }

    public static void setWidgetMode(Context c, int mode) {
        get(c).edit().putInt("widget_mode", mode == WIDGET_MODE_STOCK
                ? WIDGET_MODE_STOCK : WIDGET_MODE_THIRD_PARTY).apply();
    }

    public static boolean widgetModeManual(Context c) {
        return get(c).getBoolean("widget_mode_manual", false);
    }

    public static void setWidgetModeManual(Context c, boolean value) {
        get(c).edit().putBoolean("widget_mode_manual", value).apply();
    }

    public static String widgetModeLauncher(Context c) {
        return get(c).getString("widget_mode_launcher", "");
    }

    public static void setWidgetModeLauncher(Context c, String pkg) {
        get(c).edit().putString("widget_mode_launcher", pkg == null ? "" : pkg).apply();
    }

    public static boolean stockLauncherCardsHidden(Context c) {
        return get(c).getBoolean("stock_launcher_cards_hidden", false);
    }

    public static void setStockLauncherCardsHidden(Context c, boolean hidden) {
        get(c).edit().putBoolean("stock_launcher_cards_hidden", hidden).apply();
    }

    public static int speedMode(Context c) {
        return get(c).getInt("speed_mode", 0) == 1 ? 1 : 0;
    }

    public static void setSpeedMode(Context c, int mode) {
        get(c).edit().putInt("speed_mode", mode == 1 ? 1 : 0).apply();
    }

    public static int overlaySpeedCells(Context c) {
        return Math.max(2, Math.min(4, get(c).getInt("overlay_speed_cells", 2)));
    }

    public static void setOverlaySpeedCells(Context c, int cells) {
        get(c).edit().putInt("overlay_speed_cells", Math.max(2, Math.min(4, cells))).apply();
    }

    public static boolean speedBackground(Context c) {
        return get(c).getBoolean("speed_background", true);
    }

    public static void setSpeedBackground(Context c, boolean value) {
        get(c).edit().putBoolean("speed_background", value).apply();
    }

    public static boolean speedOnLockScreen(Context c) {
        return get(c).getBoolean("speed_on_lock_screen", false);
    }

    public static void setSpeedOnLockScreen(Context c, boolean enabled) {
        get(c).edit().putBoolean("speed_on_lock_screen", enabled).apply();
    }

    public static int speedColor(Context c) {
        return Math.max(0, Math.min(2, get(c).getInt("speed_color", 0)));
    }

    public static void setSpeedColor(Context c, int value) {
        get(c).edit().putInt("speed_color", Math.max(0, Math.min(2, value))).apply();
    }

    public static float speedCache(Context c) {
        return get(c).getFloat("speed_cache", Float.NaN);
    }

    public static void setSpeedCache(Context c, float value) {
        if (Float.isNaN(value)) return;
        get(c).edit().putFloat("speed_cache", value).apply();
    }

    public static boolean overlayEnabled(Context c, String key) {
        return get(c).getBoolean("overlay_" + key + "_enabled", false);
    }

    public static void setOverlayEnabled(Context c, String key, boolean value) {
        get(c).edit().putBoolean("overlay_" + key + "_enabled", value).apply();
    }

    public static boolean overlayAlternateSelected(Context c, String primary, String alternate) {
        SharedPreferences prefs = get(c);
        boolean primaryEnabled = prefs.getBoolean("overlay_" + primary + "_enabled", false);
        boolean alternateEnabled = prefs.getBoolean("overlay_" + alternate + "_enabled", false);
        // App versions before the format selector only stored enabled widget keys.
        if (primaryEnabled != alternateEnabled) return alternateEnabled;
        return prefs.getBoolean("overlay_" + primary + "_alternate", false);
    }

    public static void setOverlayVariant(Context c, String primary, String alternate,
                                         boolean useAlternate, boolean enabled) {
        SharedPreferences prefs = get(c);
        String from = useAlternate ? primary : alternate;
        String to = useAlternate ? alternate : primary;
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("overlay_" + primary + "_alternate", useAlternate)
                .putBoolean("overlay_" + primary + "_enabled", enabled && !useAlternate)
                .putBoolean("overlay_" + alternate + "_enabled", enabled && useAlternate);
        // Keep position and pin state when the user switches the widget format,
        // so the alternate variant appears in the same place and stays pinned.
        if (prefs.contains("overlay_" + from + "_x")) {
            editor.putInt("overlay_" + to + "_x", prefs.getInt("overlay_" + from + "_x", 0));
            editor.putInt("overlay_" + to + "_y", prefs.getInt("overlay_" + from + "_y", 0));
        }
        editor.putBoolean("overlay_" + to + "_pinned",
                prefs.getBoolean("overlay_" + from + "_pinned", false));
        copyIfAbsent(prefs, editor, "overlay_" + from + "_alpha_pct", "overlay_" + to + "_alpha_pct");
        copyIfAbsent(prefs, editor, "overlay_" + from + "_size_pct", "overlay_" + to + "_size_pct");
        editor.apply();
    }

    private static void copyIfAbsent(SharedPreferences prefs, SharedPreferences.Editor editor,
                                     String from, String to) {
        if (!prefs.contains(from) || prefs.contains(to)) return;
        Object value = prefs.getAll().get(from);
        if (value instanceof Integer) editor.putInt(to, (Integer) value);
        else if (value instanceof Boolean) editor.putBoolean(to, (Boolean) value);
        else if (value instanceof Float) editor.putFloat(to, (Float) value);
        else if (value instanceof Long) editor.putLong(to, (Long) value);
        else if (value instanceof String) editor.putString(to, (String) value);
    }

    public static boolean overlayPinned(Context c, String key) {
        return get(c).getBoolean("overlay_" + key + "_pinned", false);
    }

    public static void setOverlayPinned(Context c, String key, boolean value) {
        get(c).edit().putBoolean("overlay_" + key + "_pinned", value).apply();
    }

    public static boolean ventDirectionButtons(Context c) {
        return get(c).getBoolean("vent_direction_buttons", false);
    }

    public static void setVentDirectionButtons(Context c, boolean enabled) {
        get(c).edit().putBoolean("vent_direction_buttons", enabled).apply();
    }

    public static int glassWidgetSizePct(Context c) {
        return Math.max(WIDGET_SIZE_MIN_PCT,
                Math.min(WIDGET_SIZE_MAX_PCT, get(c).getInt("glass_widget_size_pct", 100)));
    }

    public static void setGlassWidgetSizePct(Context c, int pct) {
        get(c).edit().putInt("glass_widget_size_pct",
                Math.max(WIDGET_SIZE_MIN_PCT, Math.min(WIDGET_SIZE_MAX_PCT, pct))).apply();
    }

    public static int glassWidgetIconSizePct(Context c) {
        return clampClimateIconSize(get(c).getInt("glass_widget_icon_size_pct", 100));
    }

    public static void setGlassWidgetIconSizePct(Context c, int pct) {
        get(c).edit().putInt("glass_widget_icon_size_pct", clampClimateIconSize(pct)).apply();
    }

    public enum ClimateIconStyle {
        STANDARD, THIN, EXTRA_THIN, CHROME_THIN, CHROME;

        public boolean isChrome() {
            return this == CHROME || this == CHROME_THIN;
        }
    }

    public static ClimateIconStyle climateIconStyle(Context c) {
        SharedPreferences prefs = get(c);
        return switch (prefs.getString("climate_icon_style", "")) {
            case "STANDARD" -> ClimateIconStyle.STANDARD;
            case "THIN" -> ClimateIconStyle.THIN;
            // The 0.25 px outline was removed from the UI; keep the preference
            // forward-compatible by using the nearest remaining style.
            case "EXTRA_THIN" -> ClimateIconStyle.THIN;
            case "CHROME_THIN" -> ClimateIconStyle.CHROME_THIN;
            case "CHROME" -> ClimateIconStyle.CHROME;
            default -> ClimateIconStyle.STANDARD;
        };
    }

    public static ClimateIconStyle preferredChromeIconStyle(Context c) {
        boolean bold = get(c).getBoolean("climate_icon_chrome_bold",
                climateIconStyle(c) == ClimateIconStyle.CHROME);
        return bold ? ClimateIconStyle.CHROME : ClimateIconStyle.CHROME_THIN;
    }

    public static void setClimateIconStyle(Context c, ClimateIconStyle style) {
        SharedPreferences prefs = get(c);
        boolean boldChrome = style.isChrome()
                ? style == ClimateIconStyle.CHROME
                : preferredChromeIconStyle(c) == ClimateIconStyle.CHROME;
        prefs.edit()
                .putString("climate_icon_style", style.name())
                .putBoolean("climate_icon_chrome_bold", boldChrome)
                .apply();
    }

    public static final int CLIMATE_ICON_MIN_PCT = 80;
    public static final int CLIMATE_ICON_MAX_PCT = 150;

    public static int climateIconSizePct(Context c, String widgetKey) {
        SharedPreferences prefs = get(c);
        return clampClimateIconSize(prefs.getInt("climate_icon_size_" + widgetKey,
                prefs.getInt("climate_icon_size_pct", 100)));
    }

    public static void setClimateIconSizePct(Context c, String widgetKey, int pct) {
        get(c).edit().putInt("climate_icon_size_" + widgetKey, clampClimateIconSize(pct)).apply();
    }

    public static void clearWidgetIconSettings(Context c, String widgetKey) {
        get(c).edit()
                .remove("climate_icon_size_" + widgetKey)
                .remove("climate_icon_labels_" + widgetKey)
                .apply();
    }

    private static int clampClimateIconSize(int pct) {
        return Math.max(CLIMATE_ICON_MIN_PCT, Math.min(CLIMATE_ICON_MAX_PCT, pct));
    }

    public static boolean heatingSingleRow(Context c) {
        return get(c).getBoolean("widget_heating_single_row", false);
    }

    public static boolean heatingHideMaxAir(Context c) {
        return get(c).getBoolean("widget_heating_hide_max_air", false);
    }

    public static void setHeatingLayout(Context c, boolean singleRow, boolean hideMaxAir) {
        get(c).edit()
                .putBoolean("widget_heating_single_row", singleRow)
                .putBoolean("widget_heating_hide_max_air", hideMaxAir)
                .apply();
    }

    public static boolean showHeatingMaxAir(Context c) {
        return !heatingSingleRow(c) || !heatingHideMaxAir(c);
    }

    public static boolean showVentRecirc(Context c) {
        return get(c).getBoolean("widget_vent_show_recirc", true);
    }

    public static void setShowVentRecirc(Context c, boolean show) {
        get(c).edit().putBoolean("widget_vent_show_recirc", show).apply();
    }

    public static boolean widgetTitles(Context c) {
        return get(c).getBoolean("widget_titles", true);
    }

    public static void setWidgetTitles(Context c, boolean enabled) {
        get(c).edit().putBoolean("widget_titles", enabled).apply();
    }

    public static boolean climateIconLabels(Context c) {
        return get(c).getBoolean("climate_icon_labels", true);
    }

    public static void setClimateIconLabels(Context c, boolean enabled) {
        get(c).edit().putBoolean("climate_icon_labels", enabled).apply();
    }

    public static boolean climateIconLabels(Context c, String widgetKey) {
        return get(c).getBoolean("climate_icon_labels_" + widgetKey, climateIconLabels(c));
    }

    public static void setClimateIconLabels(Context c, String widgetKey, boolean enabled) {
        SharedPreferences.Editor editor = get(c).edit();
        String key = "climate_icon_labels_" + widgetKey;
        if (enabled == climateIconLabels(c)) editor.remove(key);
        else editor.putBoolean(key, enabled);
        editor.apply();
    }

    public static boolean widgetPowerOffWithoutConfirmation(Context c) {
        return get(c).getBoolean("widget_power_off_without_confirmation", false);
    }

    public static boolean wipersOnLock(Context c) {
        return get(c).getBoolean("wipers_on_lock", false);
    }

    public static void setWipersOnLock(Context c, boolean enabled) {
        get(c).edit().putBoolean("wipers_on_lock", enabled).apply();
    }

    public static void setWidgetPowerOffWithoutConfirmation(Context c, boolean enabled) {
        get(c).edit().putBoolean("widget_power_off_without_confirmation", enabled).apply();
    }

    public static boolean widgetCardBorder(Context c) {
        return get(c).getBoolean("widget_card_border", true);
    }

    public static void setWidgetCardBorder(Context c, boolean visible) {
        get(c).edit().putBoolean("widget_card_border", visible).apply();
    }

    public static boolean widgetButtonBorder(Context c) {
        return get(c).getBoolean("widget_button_border", true);
    }

    public static void setWidgetButtonBorder(Context c, boolean visible) {
        get(c).edit().putBoolean("widget_button_border", visible).apply();
    }

    public static boolean widgetAccentBoost(Context c) {
        return get(c).getBoolean("widget_accent_boost", false);
    }

    public static void setWidgetAccentBoost(Context c, boolean enabled) {
        get(c).edit().putBoolean("widget_accent_boost", enabled).apply();
    }

    public enum OverlayPlacement {
        FREE, CORNERS, GRID, NEIGHBORS
    }

    public static OverlayPlacement overlayPlacement(Context c) {
        SharedPreferences prefs = get(c);
        return switch (prefs.getString("overlay_placement", "")) {
            case "FREE" -> OverlayPlacement.FREE;
            case "CORNERS" -> OverlayPlacement.CORNERS;
            case "GRID" -> OverlayPlacement.GRID;
            case "AUTO_GRID", "NEIGHBORS" -> OverlayPlacement.NEIGHBORS;
            default -> prefs.getBoolean("overlay_docked", false)
                    ? OverlayPlacement.CORNERS : OverlayPlacement.FREE;
        };
    }

    public static void setOverlayPlacement(Context c, OverlayPlacement placement) {
        get(c).edit().putString("overlay_placement", placement.name()).apply();
    }

    public static int overlayCorner(Context c, String key) {
        return Math.max(0, Math.min(3, get(c).getInt("overlay_" + key + "_corner", 0)));
    }

    public static void setOverlayCorner(Context c, String key, int corner) {
        get(c).edit().putInt("overlay_" + key + "_corner", corner).apply();
    }

    public static int overlayX(Context c, String key, int fallback) {
        return get(c).getInt("overlay_" + key + "_x", fallback);
    }

    public static int overlayY(Context c, String key, int fallback) {
        return get(c).getInt("overlay_" + key + "_y", fallback);
    }

    public static void setOverlayPos(Context c, String key, int x, int y) {
        get(c).edit()
                .putInt("overlay_" + key + "_x", x)
                .putInt("overlay_" + key + "_y", y)
                .apply();
    }

    public static List<String> overlayApps(Context c) {
        return readList(get(c).getString("overlay_apps_list", "[]"));
    }

    public static void setOverlayApps(Context c, List<String> value) {
        get(c).edit().putString("overlay_apps_list", writeList(value)).apply();
    }

    public static int overlayAppsLayout(Context c) {
        return get(c).getInt("overlay_apps_layout", 0) == 1 ? 1 : 0;
    }

    public static void setOverlayAppsLayout(Context c, int value) {
        get(c).edit().putInt("overlay_apps_layout", value == 1 ? 1 : 0).apply();
    }

    public static int overlaySizePct(Context c, String key) {
        return Math.max(50, Math.min(250,
                get(c).getInt("overlay_" + key + "_size_pct", 100)));
    }

    public static boolean overlayWideTemperature(Context c) {
        return get(c).getBoolean("overlay_adapt_climate_size", false);
    }

    public static void setOverlayWideTemperature(Context c, boolean enabled) {
        get(c).edit().putBoolean("overlay_adapt_climate_size", enabled).apply();
    }

    public static void setOverlaySizePct(Context c, String key, int value) {
        get(c).edit().putInt("overlay_" + key + "_size_pct",
                Math.max(50, Math.min(250, value))).apply();
    }

    /** One size slider for all overlay widgets instead of per-widget sliders. */
    public static boolean widgetsGlobalSize(Context c) {
        return get(c).getBoolean("widgets_global_size", false);
    }

    public static void setWidgetsGlobalSize(Context c, boolean enabled) {
        get(c).edit().putBoolean("widgets_global_size", enabled).apply();
    }

    public static int overlayGlobalSizePct(Context c) {
        return Math.max(WIDGET_SIZE_MIN_PCT, Math.min(WIDGET_SIZE_MAX_PCT,
                get(c).getInt("overlay_global_size_pct", 100)));
    }

    public static void setOverlayGlobalSizePct(Context c, int value) {
        get(c).edit().putInt("overlay_global_size_pct",
                Math.max(WIDGET_SIZE_MIN_PCT, Math.min(WIDGET_SIZE_MAX_PCT, value))).apply();
    }

    public static int overlayAlphaPct(Context c, String key) {
        return Math.max(0, Math.min(100,
                get(c).getInt("overlay_" + key + "_alpha_pct", 100)));
    }

    public static void setOverlayAlphaPct(Context c, String key, int value) {
        get(c).edit().putInt("overlay_" + key + "_alpha_pct",
                Math.max(0, Math.min(100, value))).apply();
    }

    public static int widgetSizePct(Context c) {
        return Math.max(50, Math.min(250, get(c).getInt("widget_size_pct", 100)));
    }

    public static void setWidgetSizePct(Context c, int value) {
        get(c).edit().putInt("widget_size_pct", Math.max(50, Math.min(250, value))).apply();
    }

    public static final int WIDGET_SIZE_MIN_PCT = 50;
    public static final int WIDGET_SIZE_MAX_PCT = 300;

    public static int widgetSizePct(Context c, String key) {
        int fallback = widgetSizePct(c);
        return Math.max(WIDGET_SIZE_MIN_PCT, Math.min(WIDGET_SIZE_MAX_PCT,
                get(c).getInt("widget_size_" + key + "_pct", fallback)));
    }

    public static void setWidgetSizePct(Context c, String key, int value) {
        get(c).edit().putInt("widget_size_" + key + "_pct",
                Math.max(WIDGET_SIZE_MIN_PCT, Math.min(WIDGET_SIZE_MAX_PCT, value))).apply();
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
        return get(c).getBoolean("clock_date", true);
    }

    public static void setClockDate(Context c, boolean value) {
        get(c).edit().putBoolean("clock_date", value).apply();
    }

    public static boolean clockSeconds(Context c) {
        return get(c).getBoolean("clock_seconds", false);
    }

    public static void setClockSeconds(Context c, boolean value) {
        get(c).edit().putBoolean("clock_seconds", value).apply();
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

    public static boolean steeringClimate(Context c) {
        return get(c).getBoolean("steering_climate", false);
    }

    public static void setSteeringClimate(Context c, boolean value) {
        get(c).edit().putBoolean("steering_climate", value).apply();
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

    public static boolean voiceHeatingDefaultsAdded(Context c) {
        return get(c).getBoolean("voice_heating_defaults_v1", false);
    }

    public static void setVoiceCommandsWithDefaults(Context c, String value) {
        get(c).edit().putString("voice_custom_commands", value)
                .putBoolean("voice_heating_defaults_v1", true).apply();
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

    // --- климат-контроль и внешний датчик ---

    /** Показывать тестовый климат-контроль в интерфейсе. По умолчанию скрыт. */
    public static boolean climateUiVisible(Context c) {
        return get(c).getBoolean("climate_ui_visible", false);
    }

    public static void setClimateUiVisible(Context c, boolean value) {
        get(c).edit().putBoolean("climate_ui_visible", value).apply();
    }

    public static boolean climateEnabled(Context c) {
        return get(c).getBoolean("climate_enabled", false);
    }

    public static void setClimateEnabled(Context c, boolean value) {
        get(c).edit().putBoolean("climate_enabled", value).apply();
    }

    /** Желаемая температура салона для климат-контроля. */
    public static float climateTarget(Context c) {
        return get(c).getFloat("climate_target", 22f);
    }

    public static void setClimateTarget(Context c, float value) {
        float v = Math.max(17f, Math.min(32f, value));
        get(c).edit().putFloat("climate_target", v).apply();
    }

    /** Поправка уставки, которую контроллер выставил сам. */
    public static float climateTrim(Context c) {
        return get(c).getFloat("climate_trim", 0f);
    }

    public static void setClimateTrim(Context c, float value) {
        get(c).edit().putFloat("climate_trim", value).apply();
    }

    public static long climateActionAt(Context c) {
        return get(c).getLong("climate_action_at", 0L);
    }

    public static void setClimateActionAt(Context c, long value) {
        get(c).edit().putLong("climate_action_at", value).apply();
    }

    /** Последняя уставка, которую выставил контроллер (для отлова ручной правки). */
    public static float climateApplied(Context c) {
        return get(c).getFloat("climate_applied", Float.NaN);
    }

    public static void setClimateApplied(Context c, float value) {
        get(c).edit().putFloat("climate_applied", value).apply();
    }

    // --- сохранение обогревов ---

    // --- окна следуют за дверью ---

    public static boolean windowFollowDoor(Context c) {
        return get(c).getBoolean("window_follow_door", false);
    }

    public static void setWindowFollowDoor(Context c, boolean value) {
        get(c).edit().putBoolean("window_follow_door", value).apply();
    }

    public static boolean comfortRestore(Context c) {        return get(c).getBoolean("comfort_restore", false);
    }

    public static void setComfortRestore(Context c, boolean value) {
        get(c).edit().putBoolean("comfort_restore", value).apply();
    }

    public static int savedWheelHeat(Context c) {
        return get(c).getInt("saved_wheel_heat", 0);
    }

    public static void setSavedWheelHeat(Context c, int value) {
        get(c).edit().putInt("saved_wheel_heat", Math.max(0, Math.min(3, value))).apply();
    }

    public static int savedSeatHeat(Context c, int area) {
        String key = area == 4 ? "saved_seat_heat_4" : "saved_seat_heat_1";
        return get(c).getInt(key, 0);
    }

    public static void setSavedSeatHeat(Context c, int area, int value) {
        String key = area == 4 ? "saved_seat_heat_4" : "saved_seat_heat_1";
        get(c).edit().putInt(key, Math.max(0, Math.min(3, value))).apply();
    }

    public static String sensorMac(Context c) {        String value = get(c).getString("sensor_mac", "");
        return value == null ? "" : value;
    }

    public static void setSensorMac(Context c, String value) {
        get(c).edit().putString("sensor_mac", value == null ? "" : value.trim()).apply();
    }

    public static String sensorKey(Context c) {
        String value = get(c).getString("sensor_key", "");
        return value == null ? "" : value;
    }

    public static void setSensorKey(Context c, String value) {
        get(c).edit().putString("sensor_key", value == null ? "" : value.trim()).apply();
    }

    public static void setSensorReading(Context c, float temp, int humidity, int battery, long at) {
        get(c).edit()
                .putFloat("sensor_temp", temp)
                .putInt("sensor_humidity", humidity)
                .putInt("sensor_battery", battery)
                .putLong("sensor_at", at)
                .apply();
    }

    public static float sensorTemp(Context c) {
        return get(c).getFloat("sensor_temp", Float.NaN);
    }

    public static int sensorHumidity(Context c) {
        return get(c).getInt("sensor_humidity", -1);
    }

    public static int sensorBattery(Context c) {
        return get(c).getInt("sensor_battery", -1);
    }

    public static long sensorAt(Context c) {
        return get(c).getLong("sensor_at", 0L);
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


