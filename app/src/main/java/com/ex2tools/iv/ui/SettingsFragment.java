package com.ex2tools.iv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.screenshot.ScreenshotButtonService;
import com.ex2tools.iv.service.DriveRegenService;
import com.ex2tools.iv.service.DriveRegenWatchService;
import com.ex2tools.iv.servicemode.ServiceModeManager;
import com.ex2tools.iv.status.StatusIconService;
import com.ex2tools.iv.tasks.TaskManagerButtonService;
import com.ex2tools.iv.ui.FlymeSwitchRow;

public final class SettingsFragment extends Fragment {

    private FlymeSegmentedGroup driveGroup;
    private FlymeSegmentedGroup regenGroup;
    private FlymeSegmentedGroup themeGroup;
    private FlymeSwitchRow autoApply;
    private FlymeSwitchRow switchScreenshotButton;
    private FlymeSwitchRow switchTaskButton;
    private TextView applyResult;
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
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        driveGroup = view.findViewById(R.id.driveGroup);
        regenGroup = view.findViewById(R.id.regenGroup);
        themeGroup = view.findViewById(R.id.themeGroup);
        autoApply = view.findViewById(R.id.autoApply);
        switchScreenshotButton = view.findViewById(R.id.switchScreenshotButton);
        switchTaskButton = view.findViewById(R.id.switchTaskButton);
        applyResult = view.findViewById(R.id.applyResult);

        binding = true;
        driveGroup.check(driveButton(Prefs.drive(c)));
        regenGroup.check(regenButton(Prefs.regen(c)));
        themeGroup.check(themeButton(Prefs.themeMode(c)));
        binding = false;

        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            int mode = checkedId == R.id.themeLight ? 1 : checkedId == R.id.themeDark ? 2 : 0;
            Prefs.setThemeMode(requireContext(), mode);
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).dropHiddenFragments();
            }
            AppCompatDelegate.setDefaultNightMode(mode == 1
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });

        driveGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            Prefs.setDrive(requireContext(), driveValue(checkedId));
        });
        regenGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            Prefs.setRegen(requireContext(), regenValue(checkedId));
        });

        autoApply.setChecked(Prefs.autoApply(c));
        autoApply.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAutoApply(requireContext(), checked);
            DriveRegenWatchService.sync(requireContext());
        });

        view.findViewById(R.id.applyNow).setOnClickListener(v -> {
            applyResult.setText(R.string.applying);
            ((MainActivity) requireActivity()).restartDriveApply(true);
        });

        switchScreenshotButton.setChecked(Prefs.screenshotButton(c));
        switchScreenshotButton.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setScreenshotButton(c, checked);
            ScreenshotButtonService.sync(c);
        });

        switchTaskButton.setChecked(Prefs.taskButton(c));
        switchTaskButton.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setTaskButton(c, checked);
            if (checked && !Settings.canDrawOverlays(c)) {
                Toast.makeText(c, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
                ((MainActivity) requireActivity()).openOverlaySettings();
            }
            TaskManagerButtonService.sync(c);
        });

        view.findViewById(R.id.settingsCustomCommands).setOnClickListener(v ->
                CustomCommandsDialog.show(c));

        view.findViewById(R.id.settingsDefaults).setOnClickListener(v ->
                FlymeDialog.showConfirm(c,
                        getString(R.string.settings_restore_defaults),
                        getString(R.string.restore_defaults_confirm),
                        getString(R.string.yes),
                        getString(R.string.cancel),
                        () -> new Thread(() -> {
                            ServiceModeManager.restoreAll(c);
                            Prefs.reset(c);
                            StatusIconService.stop(c);
                            StatusIconService.sync(c);
                            ScreenshotButtonService.stop(c);
                            ScreenshotButtonService.sync(c);
                            TaskManagerButtonService.stop(c);
                            DriveRegenWatchService.stop(c);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                updateFromPrefs();
                                Toast.makeText(c, R.string.done, Toast.LENGTH_SHORT).show();
                            });
                        }).start()));

        updateFromPrefs();
    }

    private void updateFromPrefs() {
        if (!isAdded()) return;
        Context c = requireContext();
        binding = true;
        driveGroup.check(driveButton(Prefs.drive(c)));
        regenGroup.check(regenButton(Prefs.regen(c)));
        if (themeGroup != null) themeGroup.check(themeButton(Prefs.themeMode(c)));
        binding = false;
        autoApply.setChecked(Prefs.autoApply(c));
        setSwitch(switchScreenshotButton, Prefs.screenshotButton(c));
        setSwitch(switchTaskButton, Prefs.taskButton(c));
        String result = Prefs.lastResult(c);
        String applied = Prefs.lastApplied(c);
        if (result.isEmpty()) {
            applyResult.setText(R.string.regen_apply_hint);
        } else {
            applyResult.setText(result + (applied.isEmpty() ? "" : "\n" + applied));
        }
    }

    private void setSwitch(FlymeSwitchRow sw, boolean checked) {
        if (sw == null || sw.isChecked() == checked) return;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
        bindAgain(sw);
    }

    private void bindAgain(FlymeSwitchRow sw) {
        if (sw == switchScreenshotButton) {
            sw.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setScreenshotButton(requireContext(), checked);
                ScreenshotButtonService.sync(requireContext());
            });
        } else if (sw == switchTaskButton) {
            sw.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setTaskButton(requireContext(), checked);
                TaskManagerButtonService.sync(requireContext());
            });
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

    private int themeButton(int mode) {
        if (mode == 1) return R.id.themeLight;
        if (mode == 2) return R.id.themeDark;
        return R.id.themeSystem;
    }

    private int driveButton(int value) {
        if (value == CarProps.DRIVE_ECO) return R.id.driveEco;
        if (value == CarProps.DRIVE_SPORT) return R.id.driveSport;
        return R.id.driveComfort;
    }

    private int regenButton(int value) {
        if (value == CarProps.REGEN_WEAK) return R.id.regenWeak;
        if (value == CarProps.REGEN_STRONG) return R.id.regenStrong;
        return R.id.regenMedium;
    }

    private int driveValue(int buttonId) {
        if (buttonId == R.id.driveEco) return CarProps.DRIVE_ECO;
        if (buttonId == R.id.driveSport) return CarProps.DRIVE_SPORT;
        return CarProps.DRIVE_COMFORT;
    }

    private int regenValue(int buttonId) {
        if (buttonId == R.id.regenWeak) return CarProps.REGEN_WEAK;
        if (buttonId == R.id.regenStrong) return CarProps.REGEN_STRONG;
        return CarProps.REGEN_MEDIUM;
    }
}


