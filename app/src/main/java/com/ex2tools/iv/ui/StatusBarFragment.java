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
    private FlymeSwitchRow switchWifiAuto;
    private FlymeSwitchRow switchBattery;
    private FlymeSwitchRow switchTemp;
    private FlymeSwitchRow switchTotal;
    private FlymeSwitchRow switchTrip;
    private FlymeSwitchRow switchBrightness;
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
        switchWifiAuto = view.findViewById(R.id.switchWifiAuto);
        switchBattery = view.findViewById(R.id.switchBattery);
        switchTemp = view.findViewById(R.id.switchStatusTemp);
        switchTotal = view.findViewById(R.id.switchTotalKm);
        switchTrip = view.findViewById(R.id.switchTripKm);
        switchBrightness = view.findViewById(R.id.switchBrightness);
        brightnessSeek = view.findViewById(R.id.brightnessSeek);
        brightnessLabel = view.findViewById(R.id.brightnessLabel);

        bind(switchWifi, "status_wifi_enabled", "wifi");
        bindWifiAuto();
        bind(switchBattery, "status_battery_enabled", "battery");
        bind(switchTemp, "status_temp_ext_enabled", "temp");
        bind(switchTotal, "status_total_km_enabled", "total");
        bind(switchTrip, "status_trip_km_enabled", "trip");
        bind(switchBrightness, "status_brightness_enabled", "brightness");

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
            if (Prefs.statusAny(c)) {
                StatusIconService.refresh(c);
            } else {
                StatusIconService.sync(c);
            }
            if (!checked) StatusIconService.cancelNotifications(c);
            updateFromPrefs();
        });
    }

    private void bindWifiAuto() {
        switchWifiAuto.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context c = requireContext();
            LogStore.add(c, "status wifi auto=" + checked);
            Prefs.setWifiAutoOnBoot(c, checked);
            StatusIconService.sync(c);
        });
    }

    private void updateFromPrefs() {
        if (!isAdded()) return;
        Context c = requireContext();
        boolean wifi = Prefs.statusWifi(c);
        binding = true;
        switchWifi.setChecked(wifi);
        switchWifiAuto.setChecked(wifi && Prefs.wifiAutoOnBoot(c));
        switchWifiAuto.setVisibility(wifi ? View.VISIBLE : View.GONE);
        switchBattery.setChecked(Prefs.statusBattery(c));
        switchTemp.setChecked(Prefs.statusTemp(c));
        switchTotal.setChecked(Prefs.statusTotalKm(c));
        switchTrip.setChecked(Prefs.statusTripKm(c));
        switchBrightness.setChecked(Prefs.statusBrightness(c));
        binding = false;
        brightnessSeek.setProgress(Prefs.statusBrightnessPct(c) - 5);
        updateBrightnessLabel(Prefs.statusBrightnessPct(c));
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
