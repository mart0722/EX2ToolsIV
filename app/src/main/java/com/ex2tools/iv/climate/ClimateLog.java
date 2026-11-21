package com.ex2tools.iv.climate;

import android.content.Context;
import android.os.Environment;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.sensor.MiBeacon;
import com.ex2tools.iv.state.VehicleState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CSV-журнал климат-контроля: одна строка на пакет датчика или действие.
 *
 * Файл: Android/data/com.ex2tools.iv/files/climate.csv (доступен по ADB),
 * экспорт — в Download/EX2ToolsIV/climate-<дата>.csv.
 */
public final class ClimateLog {

    private static final String NAME = "climate.csv";
    private static final long MAX_BYTES = 4 * 1024 * 1024L;
    private static final String HEADER =
            "time,epoch_ms,event,cabin_c,humidity,battery,age_s,target,trim,applied,"
                    + "outside_c,inside_raw,ambient_raw,fan,vent,ac,auto,recirc,gear,speed_kmh\n";

    private ClimateLog() {
    }

    public static File file(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir != null) return new File(dir, NAME);
        } catch (Throwable ignored) {
        }
        return new File(c.getFilesDir(), NAME);
    }

    public static synchronized void append(Context c, String event, MiBeacon.Reading reading) {
        try {
            File f = file(c);
            if (f.length() > MAX_BYTES) {
                f.delete();
            }
            boolean fresh = !f.exists() || f.length() == 0;
            FileWriter out = new FileWriter(f, true);
            if (fresh) out.write(HEADER);
            out.write(row(c, event, reading));
            out.close();
        } catch (Throwable t) {
            LogStore.add(c, "climate log failed: " + t);
        }
    }

    private static String row(Context c, String event, MiBeacon.Reading reading) {
        long now = System.currentTimeMillis();
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(now));
        float cabin = reading == null ? Prefs.sensorTemp(c) : reading.tempC;
        int humidity = reading == null ? Prefs.sensorHumidity(c) : reading.humidity;
        int battery = reading == null ? Prefs.sensorBattery(c) : reading.battery;
        long at = reading == null ? Prefs.sensorAt(c) : reading.at;
        long age = at <= 0 ? -1 : Math.max(0, (now - at) / 1000L);
        float target = Prefs.climateTarget(c);
        float trim = Prefs.climateTrim(c);
        float applied = Math.max(17f, Math.min(32f, target + trim));

        VehicleState state = VehicleState.get();
        Integer insideRaw = intValue(state, CarProps.AC_INSIDE_TEMP);
        Integer ambientRaw = intValue(state, CarProps.AC_AMBIENT_TEMP);
        Integer fan = intValue(state, CarProps.HVAC_FAN_SPEED);
        Integer vent = intValue(state, CarProps.HVAC_FAN_DIRECTION);
        Boolean ac = boolValue(state, CarProps.HVAC_AC_ON_AOSP);
        Boolean auto = boolValue(state, CarProps.HVAC_AUTO_ON);
        Boolean recirc = boolValue(state, CarProps.HVAC_RECIRC_ON_AOSP);
        float outside = Telemetry.outsideTemp(c);
        int gear = Telemetry.gear(c);
        float speed = com.ex2tools.iv.widgets.WidgetsCommon.readSpeed(c);

        return String.format(Locale.US,
                "%s,%d,%s,%.1f,%d,%d,%d,%.1f,%.0f,%.0f,%.1f,%s,%s,%s,%s,%s,%s,%s,%d,%.0f\n",
                time, now, event,
                Float.isNaN(cabin) ? -99f : cabin,
                humidity, battery, age,
                target, trim, applied,
                Float.isNaN(outside) ? -99f : outside,
                value(insideRaw), value(ambientRaw),
                value(fan), value(vent),
                value(ac), value(auto), value(recirc),
                gear == Integer.MIN_VALUE ? -1 : gear,
                Float.isNaN(speed) ? -1f : speed);
    }

    private static Integer intValue(VehicleState state, int id) {
        try {
            int[] areas = state.areas(id, new int[]{0, 117});
            return state.intAny(id, areas);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean boolValue(VehicleState state, int id) {
        try {
            int[] areas = state.areas(id, new int[]{0, 117});
            return state.boolAny(id, areas);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String value(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Копирует журнал в Download/EX2ToolsIV/climate-<дата>.csv.
     * Возвращает путь к файлу или null, если не удалось.
     */
    public static String export(Context c) {
        File source = file(c);
        if (!source.exists() || source.length() == 0) return null;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String name = "climate-" + stamp + ".csv";
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "EX2ToolsIV");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = new File(dir, name);
            FileInputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return target.getAbsolutePath();
        } catch (Throwable t) {
            LogStore.add(c, "climate export failed: " + t);
            return null;
        }
    }

    public static void clear(Context c) {
        try {
            file(c).delete();
        } catch (Throwable ignored) {
        }
    }

    public static long size(Context c) {
        try {
            File f = file(c);
            return f.exists() ? f.length() : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
