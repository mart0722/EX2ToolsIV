package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.RvcBlackBackground;
import com.ex2tools.iv.car.Ttl65;
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
    private FlymeSwitchRow switchSpeedAutoLock;
    private FlymeSwitchRow switchRvcBlack;
    private FlymeSwitchRow switchTtl65;
    private Dialog rvcProgress;
    private Dialog ttlProgress;
    private boolean rvcBusy;
    private boolean ttlBusy;
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
        switchSpeedAutoLock = view.findViewById(R.id.switchSpeedAutoLock);
        switchRvcBlack = view.findViewById(R.id.switchRvcBlack);
        switchTtl65 = view.findViewById(R.id.switchTtl65);
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

        switchSpeedAutoLock.setChecked(Prefs.speedAutoLock(c));
        switchSpeedAutoLock.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (!checked) {
                binding = true;
                switchSpeedAutoLock.setChecked(true);
                binding = false;
                FlymeDialog.showConfirm(c,
                        getString(R.string.speed_auto_lock_disable_title),
                        getString(R.string.speed_auto_lock_disable_message),
                        getString(R.string.yes),
                        getString(R.string.cancel),
                        () -> applySpeedAutoLock(false));
            } else {
                applySpeedAutoLock(true);
            }
        });

        switchRvcBlack.setChecked(Prefs.rvcBlackBackground(c));
        switchRvcBlack.setOnCheckedChangeListener((b, checked) -> {
            if (binding || rvcBusy) return;
            if (checked) {
                binding = true;
                switchRvcBlack.setChecked(false);
                binding = false;
                Dialog confirm = FlymeDialog.showConfirm(c,
                        getString(R.string.rvc_black_confirm_title),
                        getString(R.string.rvc_black_confirm_message),
                        getString(R.string.confirm_yes),
                        getString(R.string.cancel),
                        () -> beginRvcBlack(true));
                // Also handles back/outside dismissal: the toggle must stay off
                // unless the user actually confirmed the operation.
                if (confirm != null) {
                    confirm.setOnDismissListener(d -> {
                        binding = true;
                        switchRvcBlack.setChecked(false);
                        binding = false;
                    });
                }
            } else {
                beginRvcBlack(false);
            }
        });

        switchTtl65.setChecked(Prefs.ttl65(c));
        switchTtl65.setOnCheckedChangeListener((b, checked) -> {
            if (binding || ttlBusy) return;
            beginTtl65(checked);
        });

        view.findViewById(R.id.settingsCustomCommands).setOnClickListener(v ->
                CustomCommandsDialog.show(c));

        view.findViewById(R.id.settingsReboot).setOnClickListener(v ->
                FlymeDialog.showConfirm(c,
                        getString(R.string.settings_reboot_confirm_title),
                        getString(R.string.settings_reboot_confirm_message),
                        getString(R.string.yes),
                        getString(R.string.cancel),
                        this::rebootHeadUnit));

        view.findViewById(R.id.settingsDefaults).setOnClickListener(v ->
                FlymeDialog.showConfirm(c,
                        getString(R.string.settings_restore_defaults),
                        getString(R.string.restore_defaults_confirm),
                        getString(R.string.yes),
                        getString(R.string.cancel),
                        () -> new Thread(() -> {
                            boolean ttlWasEnabled = Prefs.ttl65(c);
                            ServiceModeManager.restoreAll(c);
                            Prefs.reset(c);
                            if (ttlWasEnabled) Ttl65.apply(c, false);
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

    @Override
    public void onDestroyView() {
        if (rvcProgress != null) {
            try { rvcProgress.dismiss(); } catch (Throwable ignored) {}
            rvcProgress = null;
        }
        if (ttlProgress != null) {
            try { ttlProgress.dismiss(); } catch (Throwable ignored) {}
            ttlProgress = null;
        }
        super.onDestroyView();
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
        if (switchSpeedAutoLock != null) {
            binding = true;
            switchSpeedAutoLock.setChecked(Prefs.speedAutoLock(c));
            binding = false;
        }
        if (switchRvcBlack != null) {
            binding = true;
            switchRvcBlack.setChecked(Prefs.rvcBlackBackground(c));
            binding = false;
        }
        if (switchTtl65 != null && !ttlBusy) {
            binding = true;
            switchTtl65.setChecked(Prefs.ttl65(c));
            binding = false;
        }
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
        refreshSpeedAutoLock();
    }

    @Override
    public void onPause() {
        Prefs.get(requireContext()).unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onPause();
    }

    private void applySpeedAutoLock(boolean enabled) {
        Context c = requireContext();
        Prefs.setSpeedAutoLock(c, enabled);
        new Thread(() -> {
            CarConnection cc = CarConnection.get();
            boolean ready = cc.ensure(c);
            final boolean ok = ready
                    && cc.writeInt(CarProps.SPEED_AUTO_LOCK, 0, enabled ? 2 : 1);
            LogStore.add(c, "speed auto lock " + (enabled ? "on" : "off") + ": " + ok);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (!ok) {
                    binding = true;
                    switchSpeedAutoLock.setChecked(!enabled);
                    binding = false;
                    Toast.makeText(c, R.string.speed_auto_lock_error, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(c, enabled
                            ? R.string.speed_auto_lock_on
                            : R.string.speed_auto_lock_off, Toast.LENGTH_SHORT).show();
                }
            });
        }, "speed-auto-lock").start();
    }

    private void refreshSpeedAutoLock() {
        Context c = requireContext();
        new Thread(() -> {
            CarConnection cc = CarConnection.get();
            if (!cc.ensure(c)) return;
            int value = cc.readInt(CarProps.SPEED_AUTO_LOCK, 0);
            if (value == Integer.MIN_VALUE) return;
            boolean actual;
            if (value == 1) actual = false;
            else if (value == 2 || value == 3) actual = true;
            else return;
            Prefs.setSpeedAutoLock(c, actual);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || switchSpeedAutoLock == null) return;
                binding = true;
                switchSpeedAutoLock.setChecked(actual);
                binding = false;
            });
        }, "speed-auto-lock-read").start();
    }

    private void beginRvcBlack(final boolean enable) {
        final Context app = requireContext().getApplicationContext();
        rvcBusy = true;
        showRvcProgress(enable);
        new Thread(() -> {
            boolean ok = enable
                    ? RvcBlackBackground.apply(app)
                    : RvcBlackBackground.remove(app);
            new Handler(Looper.getMainLooper()).post(() -> finishRvcBlack(enable, ok, app));
        }, "rvc-black").start();
    }

    private void showRvcProgress(boolean enable) {
        Context c = requireContext();
        if (rvcProgress != null) {
            try { rvcProgress.dismiss(); } catch (Throwable ignored) {}
        }
        rvcProgress = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        rvcProgress.requestWindowFeature(Window.FEATURE_NO_TITLE);
        rvcProgress.setContentView(R.layout.dialog_rvc_progress);
        rvcProgress.setCanceledOnTouchOutside(false);
        rvcProgress.setCancelable(false);
        TextView title = rvcProgress.findViewById(R.id.progressTitle);
        TextView message = rvcProgress.findViewById(R.id.progressMessage);
        ProgressBar bar = rvcProgress.findViewById(R.id.progressBar);
        title.setText(enable ? R.string.rvc_black_applying : R.string.rvc_black_removing);
        message.setText(enable ? R.string.rvc_black_confirm_message : R.string.rvc_black_removing);
        bar.setIndeterminate(true);
        Window window = rvcProgress.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.52f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        rvcProgress.show();
    }

    private void finishRvcBlack(boolean enable, boolean ok, Context app) {
        rvcBusy = false;
        if (rvcProgress != null) {
            try { rvcProgress.dismiss(); } catch (Throwable ignored) {}
            rvcProgress = null;
        }
        if (ok) {
            Prefs.setRvcBlackBackground(app, enable);
        }
        if (!isAdded()) return;
        Context c = requireContext();
        binding = true;
        switchRvcBlack.setChecked(ok ? enable : !enable);
        binding = false;
        if (ok) {
            Toast.makeText(c, enable ? R.string.rvc_black_enabled
                    : R.string.rvc_black_disabled, Toast.LENGTH_SHORT).show();
        } else {
            FlymeDialog.showMessage(c,
                    getString(R.string.rvc_black_title),
                    getString(R.string.rvc_black_failed),
                    getString(R.string.ok), null);
        }
    }

    private void beginTtl65(final boolean enable) {
        final Context app = requireContext().getApplicationContext();
        ttlBusy = true;
        showTtlProgress(enable);
        new Thread(() -> {
            boolean ok;
            try {
                ok = Ttl65.apply(app, enable);
            } catch (Throwable t) {
                LogStore.add(app, "ttl65 failed: " + t);
                ok = false;
            }
            boolean result = ok;
            new Handler(Looper.getMainLooper()).post(() -> finishTtl65(enable, result, app));
        }, "ttl65").start();
    }

    private void showTtlProgress(boolean enable) {
        Context c = requireContext();
        if (ttlProgress != null) {
            try { ttlProgress.dismiss(); } catch (Throwable ignored) {}
        }
        ttlProgress = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        ttlProgress.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ttlProgress.setContentView(R.layout.dialog_rvc_progress);
        ttlProgress.setCanceledOnTouchOutside(false);
        ttlProgress.setCancelable(false);
        TextView title = ttlProgress.findViewById(R.id.progressTitle);
        TextView message = ttlProgress.findViewById(R.id.progressMessage);
        ProgressBar bar = ttlProgress.findViewById(R.id.progressBar);
        title.setText(enable ? R.string.ttl65_applying : R.string.ttl65_removing);
        message.setText(R.string.ttl65_progress_message);
        bar.setIndeterminate(true);
        Window window = ttlProgress.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.52f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        ttlProgress.show();
    }

    private void finishTtl65(boolean enable, boolean ok, Context app) {
        ttlBusy = false;
        if (ttlProgress != null) {
            try { ttlProgress.dismiss(); } catch (Throwable ignored) {}
            ttlProgress = null;
        }
        if (ok) {
            Prefs.setTtl65(app, enable);
        }
        if (!isAdded()) return;
        Context c = requireContext();
        binding = true;
        switchTtl65.setChecked(ok ? enable : !enable);
        binding = false;
        if (ok) {
            Toast.makeText(c, enable ? R.string.ttl65_enabled
                    : R.string.ttl65_disabled, Toast.LENGTH_SHORT).show();
        } else {
            FlymeDialog.showMessage(c,
                    getString(R.string.ttl65_title),
                    getString(R.string.ttl65_failed),
                    getString(R.string.ok), null);
        }
    }

    private void rebootHeadUnit() {
        if (!isAdded()) return;
        final Context app = requireContext().getApplicationContext();
        LogStore.add(app, "reboot requested from settings");
        Toast.makeText(app, R.string.settings_reboot_started, Toast.LENGTH_SHORT).show();
        // Match Sapo: perform the actual reboot from a worker thread, not from
        // the UI thread that is currently dismissing the confirmation dialog.
        new Thread(() -> {
            try {
                Thread.sleep(350L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    pm.reboot(null);
                    return;
                }
            } catch (Throwable t) {
                LogStore.add(app, "PowerManager.reboot failed: " + t);
            }
            try {
                Process p = new ProcessBuilder("sh", "-c", "reboot").start();
                p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable t) {
                LogStore.add(app, "shell reboot failed: " + t);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(app, R.string.settings_reboot_failed,
                                Toast.LENGTH_LONG).show());
            }
        }, "head-unit-reboot").start();
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


