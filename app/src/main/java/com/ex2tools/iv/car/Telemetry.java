package com.ex2tools.iv.car;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public final class Telemetry {

    private static long lastTempDiag;
    private static long lastHvacDiag;

    private Telemetry() {
    }

    public static boolean carReady(Context c) {
        return CarConnection.get().ensure(c);
    }

    public static int soc(Context c) {
        float v = socRaw(c);
        return Float.isNaN(v) ? -1 : Math.round(v);
    }

    /** Raw SoC with decimal precision, used for charge-power detection. */
    public static float socRaw(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        float v = cc.readFloat(CarProps.SOC, 0);
        if (Float.isNaN(v)) {
            int i = cc.readInt(CarProps.SOC, 0);
            if (i == Integer.MIN_VALUE) return Float.NaN;
            v = i;
        }
        if (v > 100f) v = v / 10f;
        if (v < 0f || v > 100f) return Float.NaN;
        return v;
    }

    public static float outsideTemp(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;

        int amb = cc.readInt(CarProps.AC_AMBIENT_TEMP, 0);
        float env = cc.readFloat(CarProps.ENV_OUTSIDE_TEMPERATURE, 0);
        if (Float.isNaN(env)) {
            int value = cc.readInt(CarProps.ENV_OUTSIDE_TEMPERATURE, 0);
            if (value != Integer.MIN_VALUE) env = value;
        }
        float result = Float.NaN;

        if (amb != Integer.MIN_VALUE && amb != 0) {
            float t = (amb - 80) / 2.0f;
            if (t > -50f && t < 70f) result = t;
        }
        if (Float.isNaN(result) && !Float.isNaN(env) && env != 0f) {
            // EX2 returns ENV_OUTSIDE_TEMPERATURE in tenths of a degree.
            float t = env / 10f;
            if (t > -60f && t < 90f) result = t;
        }
        if ((Float.isNaN(result) || result == 0f)
                && System.currentTimeMillis() - lastTempDiag > 60000L) {
            lastTempDiag = System.currentTimeMillis();
            com.ex2tools.iv.LogStore.add(c, "temp raw amb=" + amb + " env=" + env
                    + " -> " + result);
        }
        return result;
    }
    public static int gear(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Integer.MIN_VALUE;
        return cc.readInt(CarProps.GEAR, 0);
    }

    public static int driveNow(Context c) {
        return readFirst(c, CarProps.DRIVE_MODE);
    }

    public static int regenNow(Context c) {
        return readFirst(c, CarProps.REGEN_MODE);
    }

    public static float rangeKm(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        float v = cc.readFloat(CarProps.RANGE, 0);
        if (Float.isNaN(v)) {
            int i = cc.readInt(CarProps.RANGE, 0);
            if (i == Integer.MIN_VALUE) return Float.NaN;
            v = i;
        }
        return (v < 0f || v > 2000f) ? Float.NaN : v;
    }

    public static float odometerKm(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        float v = readPositive(cc, CarProps.ODOMETER_VENDOR);
        if (Float.isNaN(v)) v = readPositive(cc, CarProps.ODOMETER);
        return v;
    }

    private static float readPositive(CarConnection cc, int prop) {
        for (int area : CarProps.AREAS) {
            float v = cc.readFloat(prop, area);
            if (Float.isNaN(v)) {
                int i = cc.readInt(prop, area);
                if (i == Integer.MIN_VALUE) continue;
                v = i;
            }
            if (v >= 1f && v <= 2000000f) return v;
        }
        return Float.NaN;
    }

    public static float avgConsumption(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        int i = cc.readInt(CarProps.AVG_CONSUMPTION, 0);
        float v;
        if (i != Integer.MIN_VALUE) {
            v = i / 100f;
        } else {
            v = cc.readFloat(CarProps.AVG_CONSUMPTION, 0);
            if (Float.isNaN(v)) return Float.NaN;
            v = v / 100f;
        }
        return (v < 0f || v > 50f) ? Float.NaN : v;
    }

    public static float aux12v(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        int i = cc.readInt(CarProps.AUX_12V, 0);
        float v = i == Integer.MIN_VALUE ? cc.readFloat(CarProps.AUX_12V, 0) : i;
        if (Float.isNaN(v)) return Float.NaN;
        if (v >= 5f && v <= 20f) return v;
        if (v >= 5000f && v <= 20000f) return v / 1000f;
        if (v >= 50f && v <= 200f) return v / 10f;
        return Float.NaN;
    }

    public static float cabinTemp(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        logHvacDiag(c, cc);
        return readValidFloat(cc, CarProps.HVAC_TEMP_CURRENT, CarProps.CABIN_AREAS, -45f, 65f);
    }

    public static float hvacTarget(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;
        float v = readValidFloat(cc, CarProps.HVAC_TEMP_ADJUST, CarProps.HVAC_AREAS, 5f, 40f);
        if (!Float.isNaN(v)) return v;
        v = readValidFloat(cc, CarProps.HVAC_TEMP_AOSP, CarProps.HVAC_AREAS, 5f, 40f);
        if (!Float.isNaN(v)) return v;
        v = readValidFloat(cc, CarProps.HVAC_TEMP_SET, CarProps.HVAC_AREAS, 5f, 40f);
        if (!Float.isNaN(v)) return v;
        return readValidFloat(cc, CarProps.HVAC_TEMP_SET_AOSP, CarProps.HVAC_AREAS, 5f, 40f);
    }

    public static float insideTemp(Context c) {
        return cabinTemp(c);
    }

    private static float readValidFloat(CarConnection cc, int prop, int[] areas,
                                        float min, float max) {
        for (int area : areas) {
            float v = cc.readFloat(prop, area);
            if (Float.isNaN(v)) continue;
            if (v > 60f && v <= 400f) v = v / 10f;
            if (v >= min && v <= max) return v;
        }
        return Float.NaN;
    }

    private static void logHvacDiag(Context c, CarConnection cc) {
        long now = System.currentTimeMillis();
        if (now - lastHvacDiag < 60000L) return;
        lastHvacDiag = now;
        float cabin = readValidFloat(cc, CarProps.HVAC_TEMP_CURRENT, CarProps.CABIN_AREAS, -45f, 65f);
        float adjust = readValidFloat(cc, CarProps.HVAC_TEMP_ADJUST, CarProps.HVAC_AREAS, 5f, 40f);
        float aosp = readValidFloat(cc, CarProps.HVAC_TEMP_AOSP, CarProps.HVAC_AREAS, 5f, 40f);
        float vendor = readValidFloat(cc, CarProps.HVAC_TEMP_SET, CarProps.HVAC_AREAS, 5f, 40f);
        float vendorAosp = readValidFloat(cc, CarProps.HVAC_TEMP_SET_AOSP, CarProps.HVAC_AREAS, 5f, 40f);
        com.ex2tools.iv.LogStore.add(c, "hvac: cabin=" + cabin + " target=" + adjust
                + " aosp=" + aosp + " vendor=" + vendor + " vendorAosp=" + vendorAosp);
    }

    public static float batteryKwh(Context c) {
        int soc = soc(c);
        return soc < 0 ? Float.NaN : soc * CarProps.CAPACITY_KWH / 100f;
    }

    private static int readFirst(Context c, int prop) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Integer.MIN_VALUE;
        for (int area : CarProps.AREAS) {
            int v = cc.readInt(prop, area);
            if (v != Integer.MIN_VALUE) return v;
        }
        return Integer.MIN_VALUE;
    }

    public static boolean wifiEnabled(Context c) {
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    /** 0 = off/disconnected, 1..4 = signal bars. */
    public static int wifiLevel(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return 0;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return 0;
            int rssi = info.getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 5);
            if (level < 1) level = 1;
            if (level > 4) level = 4;
            return level;
        } catch (Throwable t) {
            return 0;
        }
    }
}

