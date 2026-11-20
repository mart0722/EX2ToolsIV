package com.ex2tools.iv.car;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Оценка запаса хода по фактическому расходу.
 *
 * Пока накоплено меньше MIN_LEARN_KM километров, показываем штатный запас хода;
 * дальше — свой расчёт: остаток энергии делится на средний расход кВт·ч/км.
 * Накопление скользящее: после WINDOW_KM километров суммы уменьшаются вдвое,
 * чтобы старые поездки постепенно теряли вес.
 */
public final class RangeModel {

    private static final String PREFS = "range_model";
    private static final long MIN_INTERVAL_MS = 10_000L;
    private static final float MIN_LEARN_KM = 20f;
    private static final float WINDOW_KM = 200f;

    private RangeModel() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Накопить дельту SOC/одометра. Вызывать периодически (раз в 30 с и реже). */
    public static void sample(Context c) {
        try {
            float soc = Telemetry.socRaw(c);
            float odo = Telemetry.odometerKm(c);
            if (Float.isNaN(soc) || Float.isNaN(odo)) return;

            long now = System.currentTimeMillis();
            SharedPreferences p = prefs(c);
            float lastSoc = p.getFloat("last_soc", Float.NaN);
            float lastOdo = p.getFloat("last_odo", Float.NaN);
            long lastMs = p.getLong("last_ms", 0L);
            float kmSum = p.getFloat("km", 0f);
            float kwhSum = p.getFloat("kwh", 0f);

            if (!Float.isNaN(lastSoc) && !Float.isNaN(lastOdo)
                    && now - lastMs >= MIN_INTERVAL_MS) {
                float dSoc = lastSoc - soc;          // > 0 — расход
                float dKm = odo - lastOdo;
                if (dSoc > 0.005f && dKm > 0.02f && dKm < 50f) {
                    kmSum += dKm;
                    kwhSum += dSoc * CarProps.CAPACITY_KWH / 100f;
                    if (kmSum > WINDOW_KM) {
                        kmSum /= 2f;
                        kwhSum /= 2f;
                    }
                }
            }

            p.edit()
                    .putFloat("last_soc", soc)
                    .putFloat("last_odo", odo)
                    .putLong("last_ms", now)
                    .putFloat("km", kmSum)
                    .putFloat("kwh", kwhSum)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** Сколько километров уже накоплено для обучения. */
    public static float learnedKm(Context c) {
        return prefs(c).getFloat("km", 0f);
    }

    /**
     * Своя оценка запаса хода или NaN, если данных ещё мало.
     */
    public static float estimateKm(Context c) {
        try {
            float km = prefs(c).getFloat("km", 0f);
            float kwh = prefs(c).getFloat("kwh", 0f);
            if (km < MIN_LEARN_KM || kwh < 0.05f) return Float.NaN;
            float soc = Telemetry.socRaw(c);
            if (Float.isNaN(soc)) return Float.NaN;
            float remaining = soc * CarProps.CAPACITY_KWH / 100f;
            float perKm = kwh / km;
            if (perKm <= 0.01f) return Float.NaN;
            float value = remaining / perKm;
            if (value < 1f || value > 1000f) return Float.NaN;
            return value;
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Для настроек: «накоплено 42 км» либо «свой расчёт ≈ 310 км». */
    public static String describe(Context c) {
        float est = estimateKm(c);
        if (!Float.isNaN(est)) {
            return String.format(Locale.US, "%.0f", est);
        }
        return String.format(Locale.US, "%.0f", learnedKm(c));
    }
}
