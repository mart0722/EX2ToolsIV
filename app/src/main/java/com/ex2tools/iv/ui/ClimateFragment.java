package com.ex2tools.iv.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.climate.ClimateLog;
import com.ex2tools.iv.climate.Thermostat;
import com.ex2tools.iv.sensor.CabinSensor;
import com.ex2tools.iv.sensor.MiBeacon;
import com.ex2tools.iv.service.CabinClimateService;
import com.ex2tools.iv.widgets.OverlayWidgetService;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.util.List;
import java.util.Locale;

/** Экран климат-контроля: цель, датчик, ключ, оверлей. */
public final class ClimateFragment extends Fragment {

    private static final int REQ_LOCATION = 41;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView status;
    private TextView sensorStatus;
    private TextView targetValue;
    private FlymeSwitchRow enabledSwitch;
    private FlymeSwitchRow overlaySwitch;

    private Dialog scanDialog;
    private Runnable scanRefresh;

    private boolean binding;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            updateUi();
            handler.postDelayed(this, 1000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_climate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        status = view.findViewById(R.id.climateStatus);
        sensorStatus = view.findViewById(R.id.climateSensorStatus);
        targetValue = view.findViewById(R.id.climateTargetValue);
        enabledSwitch = view.findViewById(R.id.climateEnabled);
        overlaySwitch = view.findViewById(R.id.climateOverlay);

        view.findViewById(R.id.climateTargetMinus).setOnClickListener(v -> {
            WidgetsCommon.stepClimateTarget(requireContext(), -1f);
            updateUi();
        });
        view.findViewById(R.id.climateTargetPlus).setOnClickListener(v -> {
            WidgetsCommon.stepClimateTarget(requireContext(), 1f);
            updateUi();
        });
        view.findViewById(R.id.climateFindSensor).setOnClickListener(v -> openScanDialog());
        view.findViewById(R.id.climateKey).setOnClickListener(v -> openKeyDialog());
        view.findViewById(R.id.climateLogExport).setOnClickListener(v -> exportLog());
        view.findViewById(R.id.climateLogClear).setOnClickListener(v -> {
            ClimateLog.clear(requireContext());
            Toast.makeText(requireContext(), R.string.climate_log_cleared,
                    Toast.LENGTH_SHORT).show();
        });

        enabledSwitch.setChecked(Prefs.climateEnabled(c));
        enabledSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            setEnabled(checked);
        });

        overlaySwitch.setChecked(Prefs.overlayEnabled(c, WidgetsCommon.SIZE_CLIMATE));
        overlaySwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            setOverlay(checked);
        });

        updateUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        CabinClimateService.sync(requireContext());
        if (CabinSensor.hasLocationPermission(requireContext()) && Prefs.climateEnabled(requireContext())) {
            CabinSensor.get(requireContext()).start();
        }
        updateUi();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        if (!Prefs.climateEnabled(requireContext())) {
            CabinSensor.get(requireContext()).stop();
        }
        if (scanDialog != null && scanDialog.isShowing()) {
            scanDialog.dismiss();
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (scanRefresh != null) {
            handler.removeCallbacks(scanRefresh);
            scanRefresh = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            CabinSensor.get(requireContext()).start();
            openScanDialog();
        } else {
            Toast.makeText(requireContext(), R.string.climate_permission, Toast.LENGTH_LONG).show();
        }
    }

    private void setEnabled(boolean enabled) {
        Context c = requireContext();
        Prefs.setClimateEnabled(c, enabled);
        if (enabled) {
            Thermostat.applyTargetNow(c);
        } else {
            Thermostat.restore(c);
        }
        CabinClimateService.sync(c);
        if (enabled) {
            CabinSensor.get(c).start();
            if (!CabinSensor.hasLocationPermission(c)) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_LOCATION);
            }
        } else {
            CabinSensor.get(c).stop();
        }
        updateUi();
        if (getActivity() instanceof com.ex2tools.iv.MainActivity) {
            ((com.ex2tools.iv.MainActivity) getActivity()).refreshClimateNav();
        }
    }

    private void setOverlay(boolean enabled) {
        Context c = requireContext();
        if (enabled && !Settings.canDrawOverlays(c)) {
            binding = true;
            overlaySwitch.setChecked(false);
            binding = false;
            Toast.makeText(c, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + c.getPackageName())));
            } catch (Throwable t) {
                LogStore.add(c, "overlay settings failed: " + t);
            }
            return;
        }
        Prefs.setOverlayEnabled(c, WidgetsCommon.SIZE_CLIMATE, enabled);
        OverlayWidgetService.sync(c);
        LogStore.add(c, "climate overlay: " + (enabled ? "on" : "off"));
    }

    private void updateUi() {
        if (!isAdded()) return;
        Context c = requireContext();
        binding = true;
        enabledSwitch.setChecked(Prefs.climateEnabled(c));
        overlaySwitch.setChecked(Prefs.overlayEnabled(c, WidgetsCommon.SIZE_CLIMATE));
        binding = false;

        status.setText(Prefs.climateEnabled(c)
                ? Thermostat.status(c)
                : getString(R.string.climate_off));
        targetValue.setText(String.format(Locale.US, "%.0f°", Prefs.climateTarget(c)));

        String mac = Prefs.sensorMac(c);
        if (mac.isEmpty()) {
            sensorStatus.setText(getString(R.string.climate_sensor_not_set)
                    + "\n" + CabinSensor.get(c).status());
            return;
        }
        StringBuilder sb = new StringBuilder(mac);
        sb.append(Prefs.sensorKey(c).isEmpty() ? " · ключ не задан" : " · ключ задан");
        float temp = Prefs.sensorTemp(c);
        int humidity = Prefs.sensorHumidity(c);
        int battery = Prefs.sensorBattery(c);
        long at = Prefs.sensorAt(c);
        if (!Float.isNaN(temp) && at > 0) {
            sb.append('\n');
            if (humidity >= 0 && battery >= 0) {
                sb.append(String.format(Locale.US,
                        getString(R.string.climate_sensor_status), temp, humidity, battery));
            } else {
                sb.append(String.format(Locale.US,
                        getString(R.string.climate_sensor_status_short), temp,
                        Math.max(0, humidity)));
            }
            long age = Math.max(0, (System.currentTimeMillis() - at) / 1000L);
            sb.append('\n').append(String.format(Locale.US,
                    getString(R.string.climate_sensor_age), age));
        } else {
            sb.append('\n').append(getString(R.string.climate_sensor_waiting));
        }
        sb.append('\n').append(CabinSensor.get(c).status());
        sensorStatus.setText(sb.toString());
    }

    private void exportLog() {
        Context c = requireContext();
        if (ClimateLog.size(c) == 0) {
            Toast.makeText(c, R.string.climate_log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String path = ClimateLog.export(c);
            String appPath = ClimateLog.file(c).getAbsolutePath();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                String text = path != null
                        ? getString(R.string.climate_log_export_ok, path)
                        : getString(R.string.climate_log_failed, appPath);
                Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
            });
        }, "climate-log-export").start();
    }

    private void openScanDialog() {
        Context c = requireContext();
        if (!CabinSensor.hasLocationPermission(c)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION);
            return;
        }
        CabinSensor sensor = CabinSensor.get(c);
        sensor.start();
        sensor.clearSeen();

        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sensor_scan);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.55f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        LinearLayout list = dialog.findViewById(R.id.scanList);
        TextView hint = dialog.findViewById(R.id.scanHint);
        dialog.findViewById(R.id.scanCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.scanRefresh).setOnClickListener(v -> {
            sensor.clearSeen();
            sensor.restart();
        });

        scanRefresh = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                if (dialog.isShowing()) {
                    populateScanList(list, hint, dialog);
                    handler.postDelayed(this, 1500L);
                }
            }
        };
        dialog.setOnDismissListener(d -> {
            scanDialog = null;
            if (scanRefresh != null) {
                handler.removeCallbacks(scanRefresh);
                scanRefresh = null;
            }
            if (isAdded() && !Prefs.climateEnabled(requireContext())) {
                CabinSensor.get(requireContext()).stop();
            }
        });
        scanDialog = dialog;
        dialog.show();
        handler.post(scanRefresh);
    }

    private void populateScanList(LinearLayout list, TextView hint, Dialog dialog) {
        if (!isAdded()) return;
        Context c = requireContext();
        List<CabinSensor.Seen> seen = CabinSensor.get(c).seenSnapshot();
        seen.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
        list.removeAllViews();
        if (seen.isEmpty()) {
            hint.setText(R.string.climate_scan_hint);
            return;
        }
        hint.setText(R.string.climate_scan_title);
        for (CabinSensor.Seen item : seen) {
            TextView row = new TextView(c);
            row.setTextColor(c.getColor(R.color.flyme_label_level1));
            row.setTextSize(19f);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            StringBuilder sb = new StringBuilder(item.address);
            if (item.name != null && !item.name.isEmpty()) {
                sb.append("  ").append(item.name);
            }
            sb.append("  ").append(item.rssi).append(" dBm");
            if (item.decoded && !Float.isNaN(item.tempC)) {
                sb.append(String.format(Locale.US, "  %.1f°", item.tempC));
            }
            row.setText(sb.toString());
            row.setBackgroundResource(R.drawable.bg_overview_card);
            row.setOnClickListener(v -> {
                Prefs.setSensorMac(c, item.address);
                Prefs.setClimateTrim(c, 0f);
                CabinSensor.get(c).restart();
                Toast.makeText(c, item.address, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                updateUi();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            list.addView(row, params);
        }
    }

    private void openKeyDialog() {
        Context c = requireContext();
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sensor_key);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.55f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        EditText macView = dialog.findViewById(R.id.keyMac);
        EditText keyView = dialog.findViewById(R.id.keyValue);
        macView.setText(Prefs.sensorMac(c));
        keyView.setText(Prefs.sensorKey(c));

        dialog.findViewById(R.id.keyCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.keySave).setOnClickListener(v -> {
            String mac = macView.getText().toString().trim();
            String key = keyView.getText().toString().trim();
            if (!mac.isEmpty() && !MiBeacon.isValidMac(mac)) {
                Toast.makeText(c, R.string.climate_bad_mac, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!key.isEmpty() && !MiBeacon.isValidKey(key)) {
                Toast.makeText(c, R.string.climate_bad_key, Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.setSensorMac(c, mac);
            Prefs.setSensorKey(c, key);
            Prefs.setClimateTrim(c, 0f);
            CabinSensor.get(c).restart();
            dialog.dismiss();
            updateUi();
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
