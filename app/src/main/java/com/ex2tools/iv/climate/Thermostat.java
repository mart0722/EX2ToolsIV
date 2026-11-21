package com.ex2tools.iv.climate;

import android.content.Context;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.sensor.CabinSensor;
import com.ex2tools.iv.sensor.MiBeacon;
import com.ex2tools.iv.state.VehicleState;

import java.util.Locale;

/**
 * Климат-контроль по внешнему датчику.
 *
 * Пользователь задаёт желаемую температуру салона, датчик даёт фактическую.
 * Контроллер двигает уставку штатного климата в пределах ±2 °C от желаемой
 * (шаг 1 °C, не чаще одного шага за пакет датчика). Вентилятор, режимы обдува,
 * AC и рециркуляцию не трогаем: этим управляет сам автомобиль.
 *
 * Почему только уставка: при неизменной уставке скорость вентилятора влияет
 * на скорость изменения, но не на установившуюся температуру. Смещение
 * уставки — единственный надёжный рычаг, который не воюет с ECU.
 */
public final class Thermostat {

    /** Зона нечувствительности, °C. */
    public static final float DEADBAND = 0.5f;
    /** Максимальная поправка уставки, °C. */
    public static final float MAX_TRIM = 2f;
    /** Минимальный интервал между шагами, мс. */
    private static final long MIN_ACTION_MS = 45_000L;
    /** Данные старше этого возраста не используем, мс. */
    public static final long STALE_MS = 6 * 60_000L;
    /** Через сколько без ошибки начинаем возвращать поправку к нулю, мс. */
    private static final long RELAX_AFTER_MS = 3 * 60_000L;

    private Thermostat() {
    }

    /** Вызывается на каждый расшифрованный пакет датчика. */
    public static synchronized void onReading(Context c, MiBeacon.Reading reading) {
        if (reading == null || Float.isNaN(reading.tempC)) return;
        if (!Prefs.climateEnabled(c)) return;
        if (System.currentTimeMillis() - reading.at > STALE_MS) return;
        VehicleState state = VehicleState.get();
        if (!state.isReady() && !state.ensure(c)) return;

        float target = Prefs.climateTarget(c);
        float trim = Prefs.climateTrim(c);
        float cabin = reading.tempC;
        float error = cabin - target;
        long now = System.currentTimeMillis();
        long sinceAction = now - Prefs.climateActionAt(c);

        // Если уставку поменяли руками в штатном климате — принимаем её как новую цель.
        float applied = Prefs.climateApplied(c);
        if (!Float.isNaN(applied) && sinceAction > 20_000L) {
            Float current = ClimateController.get().currentTarget();
            if (current != null && Math.abs(current - applied) >= 0.5f) {
                float value = Math.max(17f, Math.min(32f, Math.round(current)));
                Prefs.setClimateTarget(c, value);
                Prefs.setClimateTrim(c, 0f);
                Prefs.setClimateApplied(c, current);
                LogStore.add(c, String.format(Locale.US,
                        "climate: manual setpoint %.1f, new target %.0f", current, value));
                return;
            }
        }

        if (Math.abs(error) <= DEADBAND) {
            if (Math.abs(trim) >= 0.5f && sinceAction > RELAX_AFTER_MS) {
                float next = trim > 0 ? trim - 1f : trim + 1f;
                if (Math.abs(next) < 0.5f) next = 0f;
                apply(c, target, next, "стабилизация", cabin);
            }
            return;
        }

        if (sinceAction < MIN_ACTION_MS) return;

        float next = error > 0
                ? Math.max(-MAX_TRIM, trim - 1f)
                : Math.min(MAX_TRIM, trim + 1f);
        if (Math.abs(next - trim) < 0.5f) return;

        apply(c, target, next, error > 0 ? "теплее нормы" : "холоднее нормы", cabin);
    }

    /**
     * Пользователь изменил желаемую температуру: сбрасываем поправку и сразу
     * выставляем новую уставку, чтобы не ждать следующего пакета.
     */
    public static synchronized void onUserTarget(Context c, float target) {
        if (!Prefs.climateEnabled(c)) return;
        Prefs.setClimateTrim(c, 0f);
        Prefs.setClimateActionAt(c, System.currentTimeMillis());
        ClimateController.get().setTempSilent(target);
        Prefs.setClimateApplied(c, target);
        LogStore.add(c, String.format(Locale.US,
                "climate: target %.1f, trim reset", target));
        ClimateLog.append(c, "target", null);
    }

    /** Выставить желаемую температуру сразу при включении режима. */
    public static synchronized void applyTargetNow(Context c) {
        if (Prefs.sensorMac(c).isEmpty() || Prefs.sensorKey(c).isEmpty()) {
            LogStore.add(c, "climate: sensor not configured, setpoint untouched");
            return;
        }
        float target = Prefs.climateTarget(c);
        Prefs.setClimateTrim(c, 0f);
        Prefs.setClimateActionAt(c, System.currentTimeMillis());
        ClimateController.get().setTempSilent(target);
        Prefs.setClimateApplied(c, target);
        LogStore.add(c, String.format(Locale.US, "climate: on, setpoint %.0f", target));
        ClimateLog.append(c, "on", null);
    }

    /** Снять поправку и вернуть штатную уставку (при выключении режима). */
    public static synchronized void restore(Context c) {
        float trim = Prefs.climateTrim(c);
        if (Math.abs(trim) < 0.5f) return;
        float target = Prefs.climateTarget(c);
        ClimateController.get().setTempSilent(target);
        Prefs.setClimateTrim(c, 0f);
        Prefs.setClimateApplied(c, target);
        LogStore.add(c, String.format(Locale.US, "climate: restored %.1f", target));
        ClimateLog.append(c, "restore", null);
    }

    private static void apply(Context c, float target, float trim, String why, float cabin) {
        float applied = Math.max(17f, Math.min(32f, target + trim));
        ClimateController.get().setTempSilent(applied);
        Prefs.setClimateTrim(c, trim);
        Prefs.setClimateApplied(c, applied);
        Prefs.setClimateActionAt(c, System.currentTimeMillis());
        LogStore.add(c, String.format(Locale.US,
                "climate %s: cabin %.1f, target %.1f, trim %+.0f -> %.0f",
                why, cabin, target, trim, applied));
        ClimateLog.append(c, "trim", null);
    }

    /** Короткий статус для настроек и уведомления. */
    public static String status(Context c) {
        if (!Prefs.climateEnabled(c)) return "выключен";
        if (Prefs.sensorMac(c).isEmpty()) return "датчик не настроен";
        CabinSensor sensor = CabinSensor.get(c);
        MiBeacon.Reading reading = sensor.last();
        if (reading == null) {
            float temp = Prefs.sensorTemp(c);
            long at = Prefs.sensorAt(c);
            if (Float.isNaN(temp) || at <= 0) return "ожидание датчика";
            reading = new MiBeacon.Reading();
            reading.tempC = temp;
            reading.at = at;
        }
        long age = System.currentTimeMillis() - reading.at;
        if (age > STALE_MS) {
            return "нет данных датчика (" + (age / 60_000L) + " мин)";
        }
        float target = Prefs.climateTarget(c);
        float trim = Prefs.climateTrim(c);
        String suffix = Math.abs(trim) < 0.5f ? ""
                : String.format(Locale.US, " (%+.0f°)", trim);
        return String.format(Locale.US, "салон %.1f° → %.0f°%s",
                reading.tempC, target, suffix);
    }
}
