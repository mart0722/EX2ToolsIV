package com.ex2tools.iv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;
import com.ex2tools.iv.status.StatusIconService;

public final class StatusBarFragment extends Fragment {

    private FlymeSwitchRow switchWifi;
    private FlymeSwitchRow switchBattery;
    private FlymeSwitchRow switchTemp;
    private FlymeSwitchRow switchTotal;
    private FlymeSwitchRow switchTrip;
    private FlymeSwitchRow switchBrightness;
    private SeekBar interval;
    private TextView intervalLabel;
    private SeekBar brightnessSeek;
    private TextView brightnessLabel;
    private boolean binding;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> {
                if (!isAdded()) return;
                updateFromPrefs();
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status_bar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        switchWifi = view.findViewById(R.id.switchWifi);
        switchBattery = view.findViewById(R.id.switchBattery);
        switchTemp = view.findViewById(R.id.switchStatusTemp);
        switchTotal = view.findViewById(R.id.switchTotalKm);
        switchTrip = view.findViewById(R.id.switchTripKm);
        switchBrightness = view.findViewById(R.id.switchBrightness);
        interval = view.findViewById(R.id.intervalSeek);
        intervalLabel = view.findViewById(R.id.intervalLabel);
        brightnessSeek = view.findViewById(R.id.brightnessSeek);
        brightnessLabel = view.findViewById(R.id.brightnessLabel);

        bind(switchWifi, "status_wifi_enabled", "wifi");
        bind(switchBattery, "status_battery_enabled", "battery");
        bind(switchTemp, "status_temp_ext_enabled", "temp");
        bind(switchTotal, "status_total_km_enabled", "total");
        bind(switchTrip, "status_trip_km_enabled", "trip");
        bind(switchBrightness, "status_brightness_enabled", "brightness");

        interval.setMax(27);
        interval.setProgress(Prefs.intervalSec(c) - 3);
        updateIntervalLabel(Prefs.intervalSec(c));
        interval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = progress + 3;
                updateIntervalLabel(sec);
                if (fromUser) {
                    Prefs.setIntervalSec(requireContext(), sec);
                    if (Prefs.statusAny(requireContext())) {
                        StatusIconService.refresh(requireContext());
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        brightnessSeek.setMax(95);
        brightnessSeek.setProgress(Prefs.statusBrightnessPct(c) - 5);
        updateBrightnessLabel(Prefs.statusBrightnessPct(c));
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = progress + 5;
                updateBrightnessLabel(pct);
                if (fromUser) Prefs.setStatusBrightnessPct(requireContext(), pct);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        updateFromPrefs();
    }

    private void bind(FlymeSwitchRow sw, String prefKey, String orderKey) {
        sw.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context c = requireContext();
            LogStore.add(c, "status toggle " + orderKey + "=" + checked);
            Prefs.setStatus(c, prefKey, checked);
            if (Prefs.statusAny(c)) StatusIconService.refresh(c);
            else StatusIconService.sync(c);
        });
    }

    private void updateFromPrefs() {
        if (!isAdded()) return;
        Context c = requireContext();
        binding = true;
        switchWifi.setChecked(Prefs.statusWifi(c));
        switchBattery.setChecked(Prefs.statusBattery(c));
        switchTemp.setChecked(Prefs.statusTemp(c));
        switchTotal.setChecked(Prefs.statusTotalKm(c));
        switchTrip.setChecked(Prefs.statusTripKm(c));
        switchBrightness.setChecked(Prefs.statusBrightness(c));
        binding = false;
        interval.setProgress(Prefs.intervalSec(c) - 3);
        updateIntervalLabel(Prefs.intervalSec(c));
        brightnessSeek.setProgress(Prefs.statusBrightnessPct(c) - 5);
        updateBrightnessLabel(Prefs.statusBrightnessPct(c));
    }

    private void updateIntervalLabel(int sec) {
        if (intervalLabel != null) {
            intervalLabel.setText(getString(R.string.settings_interval_fmt, sec));
        }
    }

    private void updateBrightnessLabel(int pct) {
        if (brightnessLabel != null) {
            brightnessLabel.setText(getString(R.string.status_brightness_level)
                    + ": " + getString(R.string.status_brightness_fmt, pct));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Prefs.get(requireContext()).registerOnSharedPreferenceChangeListener(prefListener);
        updateFromPrefs();
    }

    @Override
    public void onPause() {
        Prefs.get(requireContext()).unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onPause();
    }
}
