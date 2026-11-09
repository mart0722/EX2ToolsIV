package com.ex2tools.iv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.ChargeStatusProbe;
import com.ex2tools.iv.car.Telemetry;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayDeque;
import java.util.Locale;

public final class OverviewFragment extends Fragment {

    private static final long CHARGE_WINDOW_MS = 30 * 1000L;
    private static final long CHARGE_MIN_WINDOW_MS = 5 * 1000L;
    private static final float CHARGE_MIN_KW = 0.5f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<ChargeSample> chargeWindow = new ArrayDeque<>();
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    private TextView range;
    private TextView consumption;
    private TextView battery;
    private TextView batteryPercent;
    private TextView batteryKwh;
    private TextView batteryStatus;
    private TextView batteryLabel;
    private TextView temp;
    private TextView climateTarget;
    private TextView volt;
    private TextView odometer;
    private BatteryLevelView batteryView;
    private LinearProgressIndicator batteryBar;
    private LinearProgressIndicator voltBar;

    private volatile boolean loading;
    private boolean lastCharging;
    private float lastChargePowerKw;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 2000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        range = view.findViewById(R.id.overviewRange);
        consumption = view.findViewById(R.id.overviewConsumption);
        battery = view.findViewById(R.id.overviewBattery);
        batteryPercent = view.findViewById(R.id.overviewBatteryPercent);
        batteryKwh = view.findViewById(R.id.overviewBatteryKwh);
        batteryStatus = view.findViewById(R.id.overviewBatteryStatus);
        batteryLabel = view.findViewById(R.id.overviewBatteryLabel);
        temp = view.findViewById(R.id.overviewTemp);
        climateTarget = view.findViewById(R.id.overviewClimateTarget);
        volt = view.findViewById(R.id.overviewVolt);
        odometer = view.findViewById(R.id.overviewOdometer);
        batteryView = view.findViewById(R.id.overviewBatteryView);
        batteryBar = view.findViewById(R.id.overviewBatteryBar);
        voltBar = view.findViewById(R.id.overviewVoltBar);

        batteryStatus.setText(R.string.overview_battery_capacity);
        refresh();
    }

    private void refresh() {
        if (loading) return;
        Context c = getContext();
        if (c == null) return;
        loading = true;
        new Thread(() -> {
            Data d = new Data();
            d.ready = Telemetry.carReady(c);
            if (d.ready) {
                d.soc = Telemetry.soc(c);
                d.socRaw = Telemetry.socRaw(c);
                d.temp = Telemetry.outsideTemp(c);
                d.target = Telemetry.hvacTarget(c);
                d.range = Telemetry.rangeKm(c);
                d.consumption = Telemetry.avgConsumption(c);
                d.volt = Telemetry.aux12v(c);
                d.odometer = Telemetry.odometerKm(c);
                d.gear = Telemetry.gear(c);
            } else {
                d.soc = -1;
                d.socRaw = Float.NaN;
                d.temp = Float.NaN;
                d.target = Float.NaN;
                d.range = Float.NaN;
                d.consumption = Float.NaN;
                d.volt = Float.NaN;
                d.odometer = Float.NaN;
                d.gear = Integer.MIN_VALUE;
            }
            d.charging = updateCharging(d.socRaw, d.gear);
            if (!d.charging) d.charging = ChargeStatusProbe.isCharging(c);
            if (d.charging != lastCharging) {
                lastCharging = d.charging;
                LogStore.add(c, "overview charging=" + d.charging
                        + " power=" + lastChargePowerKw
                        + " soc=" + d.soc + " raw=" + d.socRaw + " gear=" + d.gear);
            }
            if (!isAdded()) {
                loading = false;
                return;
            }
            try {
                requireActivity().runOnUiThread(() -> {
                    loading = false;
                    apply(d);
                });
            } catch (Throwable t) {
                loading = false;
            }
        }, "ex2-overview").start();
    }

    private boolean updateCharging(float soc, int gear) {
        if (Float.isNaN(soc)) {
            chargeWindow.clear();
            lastChargePowerKw = 0f;
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        chargeWindow.addLast(new ChargeSample(now, soc));
        while (!chargeWindow.isEmpty()
                && now - chargeWindow.peekFirst().t > CHARGE_WINDOW_MS) {
            chargeWindow.pollFirst();
        }
        ChargeSample first = chargeWindow.peekFirst();
        ChargeSample last = chargeWindow.peekLast();
        if (first == null || last == null
                || last.t - first.t < CHARGE_MIN_WINDOW_MS) {
            lastChargePowerKw = 0f;
            return false;
        }
        float dt = (last.t - first.t) / 1000f;
        float deltaSoc = last.soc - first.soc;
        lastChargePowerKw = (deltaSoc / 100f * CarProps.CAPACITY_KWH) * 3600f / dt;
        return gear == CarProps.GEAR_PARK && lastChargePowerKw >= CHARGE_MIN_KW;
    }

    private void apply(Data d) {
        if (!isAdded()) return;
        Context c = requireContext();

        if (d.soc < 0) {
            battery.setText(R.string.overview_no_data);
            battery.setTextColor(ContextCompat.getColor(c, R.color.text_dim));
            batteryPercent.setVisibility(View.GONE);
            batteryKwh.setText(R.string.overview_no_data);
            batteryView.setLevel(0);
            batteryView.setCharging(false);
            batteryLabel.setText(R.string.overview_battery_label);
            batteryBar.setProgressCompat(0, false);
        } else {
            battery.setText(String.valueOf(d.soc));
            batteryPercent.setVisibility(View.VISIBLE);
            int colorRes = d.soc >= 80
                    ? R.color.emerald
                    : (d.soc >= 20 ? R.color.flyme_blue : R.color.danger);
            int color = ContextCompat.getColor(c, colorRes);
            battery.setTextColor(color);
            batteryPercent.setTextColor(color);
            batteryBar.setIndicatorColor(color);
            batteryBar.setProgressCompat(d.soc, true);
            batteryView.setLevel(d.soc);
            batteryView.setCharging(d.charging);
            batteryLabel.setText(d.charging
                    ? R.string.overview_charging
                    : R.string.overview_battery_label);
            float kwh = d.soc * CarProps.CAPACITY_KWH / 100f;
            batteryKwh.setText(String.format(Locale.US, "%.1f кВт·ч", kwh));
        }

        setMetric(temp, Float.isNaN(d.temp)
                ? null
                : String.valueOf(Math.round(d.temp)), getString(R.string.unit_celsius));

        setMetric(climateTarget, Float.isNaN(d.target)
                ? null
                : String.format(Locale.US, "%.1f", d.target), getString(R.string.unit_celsius));

        setMetric(range, Float.isNaN(d.range)
                ? null
                : String.valueOf(Math.round(d.range)), getString(R.string.unit_km));

        setMetric(consumption, Float.isNaN(d.consumption)
                ? null
                : String.format(Locale.US, "%.1f", d.consumption),
                getString(R.string.unit_consumption), 0.48f);

        setMetric(volt, Float.isNaN(d.volt)
                ? null
                : String.format(Locale.US, "%.1f", d.volt), getString(R.string.unit_volt));

        if (Float.isNaN(d.volt)) {
            voltBar.setProgressCompat(0, false);
        } else {
            int pct = Math.round(clamp((d.volt - 11.0f) / 2.5f * 100f, 0f, 100f));
            int colorRes = d.volt >= 12.0f
                    ? R.color.emerald
                    : (d.volt >= 11.5f ? R.color.flyme_orange : R.color.flyme_red);
            voltBar.setIndicatorColor(ContextCompat.getColor(c, colorRes));
            voltBar.setProgressCompat(pct, true);
        }

        setMetric(odometer, Float.isNaN(d.odometer)
                ? null
                : formatKm(Math.round(d.odometer)), getString(R.string.unit_km));
    }

    private void setMetric(TextView view, String value, String unit) {
        setMetric(view, value, unit, 0.55f);
    }

    private void setMetric(TextView view, String value, String unit, float unitScale) {
        if (view == null) return;
        Context c = getContext();
        if (c == null) return;
        if (value == null || value.isEmpty()) {
            view.setText(R.string.overview_no_data);
            view.setTextColor(ContextCompat.getColor(c, R.color.text_dim));
            return;
        }
        String text = unit == null || unit.isEmpty() ? value : value + " " + unit;
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (unit != null && !unit.isEmpty()) {
            int start = value.length() + 1;
            sb.setSpan(new RelativeSizeSpan(unitScale), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(c, R.color.flyme_label_level2)),
                    start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(sb);
        view.setTextColor(ContextCompat.getColor(c, R.color.flyme_label_level1));
    }

    private static String formatKm(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ');
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(requireContext(), refreshReceiver,
                new IntentFilter(Actions.REFRESH), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop() {
        try {
            requireContext().unregisterReceiver(refreshReceiver);
        } catch (Throwable ignored) {
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private static final class ChargeSample {
        final long t;
        final float soc;

        ChargeSample(long t, float soc) {
            this.t = t;
            this.soc = soc;
        }
    }

    private static final class Data {
        boolean ready;
        int soc;
        float socRaw;
        float temp;
        float target;
        float range;
        float consumption;
        float volt;
        float odometer;
        int gear;
        boolean charging;
    }
}
