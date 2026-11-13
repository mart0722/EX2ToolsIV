package com.ex2tools.iv.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.widgets.ClockWidgetProvider;
import com.ex2tools.iv.widgets.OverlayWidgetService;
import com.ex2tools.iv.widgets.SpeedWidgetProvider;
import com.ex2tools.iv.widgets.WidgetModes;
import com.ex2tools.iv.widgets.WidgetUpdateService;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WidgetsFragment extends Fragment {

    private static final String[] OVERLAY_KEYS = {
            WidgetsCommon.SIZE_DRIVE_REGEN,
            WidgetsCommon.SIZE_DRIVE_MODE,
            WidgetsCommon.SIZE_REGEN,
            WidgetsCommon.SIZE_FAN,
            WidgetsCommon.SIZE_TEMP,
            WidgetsCommon.SIZE_VENT,
            WidgetsCommon.SIZE_VENT_FAN,
            WidgetsCommon.SIZE_HEATING,
            WidgetsCommon.SIZE_BATTERY_RANGE,
            WidgetsCommon.SIZE_LOCK,
            WidgetsCommon.SIZE_APPS
    };

    private static final int[] OVERLAY_LABELS = {
            R.string.widgets_size_drive_regen,
            R.string.widgets_size_drive_mode,
            R.string.widgets_size_regen,
            R.string.widgets_size_fan,
            R.string.widgets_size_temp,
            R.string.widgets_size_vent,
            R.string.widgets_size_vent_fan,
            R.string.widgets_size_heating,
            R.string.widgets_size_battery_range,
            R.string.widgets_size_lock,
            R.string.widgets_size_apps
    };

    private SeekBar opacity;
    private TextView opacityValue;
    private View opacityPreview;
    private FlymeSegmentedGroup fontGroup;
    private FlymeSegmentedGroup colorGroup;
    private FlymeSwitchRow date;
    private TextClock clockPreview;
    private TextView clockDatePreview;

    private FlymeSegmentedGroup modeGroup;
    private View overlayPanel;
    private View standardPanel;
    private LinearLayout overlayList;
    private FlymeSwitchRow speedEnabled;
    private FlymeSwitchRow speedPinned;
    private FlymeSwitchRow speedBackground;
    private FlymeSegmentedGroup speedModeGroup;
    private FlymeSegmentedGroup speedColorGroup;
    private View speedSizeRow;
    private SeekBar speedSize;
    private TextView speedSizeValue;
    private SeekBar speedAlpha;
    private TextView speedAlphaValue;
    private View speedAlphaRow;
    private View speedReset;
    private final Map<String, FlymeSwitchRow> overlayToggles = new LinkedHashMap<>();
    private final Map<String, SeekBar> overlaySizeSeeks = new LinkedHashMap<>();
    private final Map<String, SeekBar> overlayAlphaSeeks = new LinkedHashMap<>();
    private final Map<String, TextView> overlaySizeValues = new LinkedHashMap<>();
    private final Map<String, TextView> overlayAlphaValues = new LinkedHashMap<>();
    private FlymeSegmentedGroup appsModeGroup;
    private TextView appsCount;
    private TextView appsSelect;
    private boolean binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_widgets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        opacity = view.findViewById(R.id.widgetOpacitySeek);
        opacityValue = view.findViewById(R.id.widgetOpacityValue);
        opacityPreview = view.findViewById(R.id.widgetOpacityPreview);
        fontGroup = view.findViewById(R.id.clockFontGroup);
        colorGroup = view.findViewById(R.id.clockColorGroup);
        date = view.findViewById(R.id.clockDate);
        clockPreview = view.findViewById(R.id.clockPreview);
        clockDatePreview = view.findViewById(R.id.clockDatePreview);

        modeGroup = view.findViewById(R.id.widgetModeGroup);
        overlayPanel = view.findViewById(R.id.widgetOverlayPanel);
        standardPanel = view.findViewById(R.id.widgetStandardPanel);
        overlayList = view.findViewById(R.id.widgetOverlayList);
        speedEnabled = view.findViewById(R.id.widgetSpeedOverlayEnabled);
        speedPinned = view.findViewById(R.id.widgetSpeedOverlayPinned);
        speedBackground = view.findViewById(R.id.widgetSpeedBackground);
        speedModeGroup = view.findViewById(R.id.widgetSpeedModeGroup);
        speedColorGroup = view.findViewById(R.id.widgetSpeedColorGroup);
        speedSizeRow = view.findViewById(R.id.widgetSpeedSizeRow);
        speedSize = view.findViewById(R.id.widgetSpeedSizeSeek);
        speedSizeValue = view.findViewById(R.id.widgetSpeedSizeValue);
        speedAlpha = view.findViewById(R.id.widgetSpeedAlphaSeek);
        speedAlphaValue = view.findViewById(R.id.widgetSpeedAlphaValue);
        speedAlphaRow = view.findViewById(R.id.widgetSpeedAlphaRow);
        speedReset = view.findViewById(R.id.widgetSpeedReset);

        buildOverlayRows(c);
        setupModeControls(c);
        setupSpeedControls(c);
        setupStandardControls(c);
        WidgetModes.refreshAuto(c);
        updateAll();
    }

    private void setupModeControls(Context c) {
        View info = requireView().findViewById(R.id.widgetInfo);
        if (info != null) {
            info.setOnClickListener(v -> {
                Context now = requireContext();
                String message = getString(R.string.widgets_info_message)
                        + "\n\n" + getString(R.string.widgets_current_launcher,
                        WidgetModes.launcherName(now) + " (" + WidgetModes.launcherPackage(now) + ")");
                FlymeDialog.showMessage(now,
                        getString(R.string.widgets_info_title),
                        message,
                        getString(R.string.ok), null);
            });
        }

        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            int mode = checkedId == R.id.widgetModeStock
                    ? Prefs.WIDGET_MODE_STOCK : Prefs.WIDGET_MODE_THIRD_PARTY;
            int current = WidgetModes.current(requireContext());
            if (mode == current) return;
            WidgetModes.set(requireContext(), mode);
            updateModeUi();
        });
    }

    private void setupSpeedControls(Context c) {
        speedModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            Prefs.setSpeedMode(requireContext(), checkedId == R.id.widgetSpeedAnalog ? 1 : 0);
            refreshSpeed();
        });
        speedColorGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            int color = checkedId == R.id.widgetSpeedColorBlue ? 2
                    : checkedId == R.id.widgetSpeedColorBlack ? 1 : 0;
            Prefs.setSpeedColor(requireContext(), color);
            refreshSpeed();
        });
        speedBackground.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setSpeedBackground(requireContext(), checked);
            refreshSpeed();
        });
        speedSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = progress + 50;
                if (speedSizeValue != null) {
                    speedSizeValue.setText(getString(R.string.widgets_percent_fmt, pct));
                }
                if (fromUser) {
                    Prefs.setWidgetSizePct(requireContext(), WidgetsCommon.SIZE_SPEED, pct);
                    refreshSpeed();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                refreshSpeed();
                OverlayWidgetService.restart(requireContext());
            }
        });
        speedAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (speedAlphaValue != null) {
                    speedAlphaValue.setText(getString(R.string.widgets_percent_fmt, progress));
                }
                if (fromUser) {
                    Prefs.setOverlayAlphaPct(requireContext(), WidgetsCommon.SIZE_SPEED, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                OverlayWidgetService.restart(requireContext());
            }
        });
        speedEnabled.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setOverlayEnabled(requireContext(), WidgetsCommon.SIZE_SPEED, checked);
            OverlayWidgetService.sync(requireContext());
        });
        speedPinned.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setOverlayPinned(requireContext(), WidgetsCommon.SIZE_SPEED, checked);
            OverlayWidgetService.sync(requireContext());
        });
        if (speedReset != null) {
            speedReset.setOnClickListener(v -> {
                Prefs.setOverlayPos(requireContext(), WidgetsCommon.SIZE_SPEED, 40, 160);
                OverlayWidgetService.restart(requireContext());
            });
        }
    }

    private void setupStandardControls(Context c) {
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOpacityText(progress);
                if (fromUser) {
                    Prefs.setWidgetBgOpacity(requireContext(), progress);
                    if (opacityPreview != null) {
                        opacityPreview.setBackgroundResource(
                                WidgetsCommon.bgDrawableResForPct(requireContext()));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                WidgetUpdateService.refreshOpacity(requireContext());
                refreshSpeed();
            }
        });

        binding = true;
        fontGroup.check(Prefs.clockFont(c) == 1 ? R.id.clockFontCondensed : R.id.clockFontClassic);
        colorGroup.check(colorButton(Prefs.clockColor(c)));
        binding = false;

        fontGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            Prefs.setClockFont(requireContext(), checkedId == R.id.clockFontCondensed ? 1 : 0);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(requireContext());
        });

        colorGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            int mode = checkedId == R.id.clockColorWhite ? 1
                    : checkedId == R.id.clockColorBlack ? 2 : 0;
            Prefs.setClockColor(requireContext(), mode);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(requireContext());
        });

        date.setChecked(Prefs.clockDate(c));
        date.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setClockDate(requireContext(), checked);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(requireContext());
            WidgetUpdateService.refreshOpacity(requireContext());
        });
    }

    private void buildOverlayRows(Context c) {
        if (overlayList == null) return;
        overlayList.removeAllViews();
        overlayToggles.clear();
        overlaySizeSeeks.clear();
        overlayAlphaSeeks.clear();
        overlaySizeValues.clear();
        overlayAlphaValues.clear();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = 0; i < OVERLAY_KEYS.length; i++) {
            final String key = OVERLAY_KEYS[i];
            boolean apps = WidgetsCommon.SIZE_APPS.equals(key);
            View card = inflater.inflate(apps
                    ? R.layout.item_overlay_apps
                    : R.layout.item_overlay_widget, overlayList, false);
            FlymeSwitchRow toggle = card.findViewById(R.id.overlayToggle);
            SeekBar sizeSeek = card.findViewById(R.id.overlaySizeSeek);
            SeekBar alphaSeek = card.findViewById(R.id.overlayAlphaSeek);
            TextView sizeValue = card.findViewById(R.id.overlaySizeValue);
            TextView alphaValue = card.findViewById(R.id.overlayAlphaValue);

            toggle.setText(getString(OVERLAY_LABELS[i]));
            int size = Prefs.overlaySizePct(c, key);
            int alpha = Prefs.overlayAlphaPct(c, key);
            toggle.setChecked(Prefs.overlayEnabled(c, key));
            sizeSeek.setProgress(size - 50);
            alphaSeek.setProgress(alpha);
            sizeValue.setText(getString(R.string.widgets_percent_fmt, size));
            alphaValue.setText(getString(R.string.widgets_percent_fmt, alpha));

            toggle.setOnCheckedChangeListener((b, checked) -> {
                if (binding) return;
                Prefs.setOverlayEnabled(requireContext(), key, checked);
                OverlayWidgetService.sync(requireContext());
                if (apps && checked && Prefs.overlayApps(requireContext()).isEmpty()) {
                    b.post(this::openAppsPicker);
                }
            });

            if (apps) {
                appsModeGroup = card.findViewById(R.id.appsModeGroup);
                appsCount = card.findViewById(R.id.appsCount);
                appsSelect = card.findViewById(R.id.appsSelect);
                binding = true;
                appsModeGroup.check(Prefs.overlayAppsLayout(c) == 1
                        ? R.id.appsModeGrid : R.id.appsModeRow);
                binding = false;
                appsModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (!isChecked || binding) return;
                    Prefs.setOverlayAppsLayout(requireContext(),
                            checkedId == R.id.appsModeGrid ? 1 : 0);
                    OverlayWidgetService.restart(requireContext());
                });
                appsSelect.setOnClickListener(v -> openAppsPicker());
            }
            sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int pct = progress + 50;
                    sizeValue.setText(getString(R.string.widgets_percent_fmt, pct));
                    if (fromUser) {
                        Prefs.setOverlaySizePct(requireContext(), key, pct);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    OverlayWidgetService.restart(requireContext());
                }
            });
            alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    alphaValue.setText(getString(R.string.widgets_percent_fmt, progress));
                    if (fromUser) {
                        Prefs.setOverlayAlphaPct(requireContext(), key, progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    OverlayWidgetService.restart(requireContext());
                }
            });

            overlayToggles.put(key, toggle);
            overlaySizeSeeks.put(key, sizeSeek);
            overlayAlphaSeeks.put(key, alphaSeek);
            overlaySizeValues.put(key, sizeValue);
            overlayAlphaValues.put(key, alphaValue);
            overlayList.addView(card);
        }
    }

    private void updateAll() {
        Context c = requireContext();
        int p = Prefs.widgetBgOpacity(c);
        opacity.setProgress(p);
        updateOpacityText(p);
        if (opacityPreview != null) {
            opacityPreview.setBackgroundResource(WidgetsCommon.bgDrawableResForPct(c));
        }

        int speedPct = Prefs.widgetSizePct(c, WidgetsCommon.SIZE_SPEED);
        if (speedSize != null) speedSize.setProgress(speedPct - 50);
        if (speedSizeValue != null) {
            speedSizeValue.setText(getString(R.string.widgets_percent_fmt, speedPct));
        }
        int speedAlphaPct = Prefs.overlayAlphaPct(c, WidgetsCommon.SIZE_SPEED);
        if (speedAlpha != null) speedAlpha.setProgress(speedAlphaPct);
        if (speedAlphaValue != null) {
            speedAlphaValue.setText(getString(R.string.widgets_percent_fmt, speedAlphaPct));
        }

        binding = true;
        fontGroup.check(Prefs.clockFont(c) == 1 ? R.id.clockFontCondensed : R.id.clockFontClassic);
        colorGroup.check(colorButton(Prefs.clockColor(c)));
        date.setChecked(Prefs.clockDate(c));
        speedModeGroup.check(Prefs.speedMode(c) == 1
                ? R.id.widgetSpeedAnalog : R.id.widgetSpeedDigital);
        speedColorGroup.check(Prefs.speedColor(c) == 1 ? R.id.widgetSpeedColorBlack
                : Prefs.speedColor(c) == 2 ? R.id.widgetSpeedColorBlue : R.id.widgetSpeedColorWhite);
        speedBackground.setChecked(Prefs.speedBackground(c));
        speedEnabled.setChecked(Prefs.overlayEnabled(c, WidgetsCommon.SIZE_SPEED));
        speedPinned.setChecked(Prefs.overlayPinned(c, WidgetsCommon.SIZE_SPEED));
        if (appsModeGroup != null) {
            appsModeGroup.check(Prefs.overlayAppsLayout(c) == 1
                    ? R.id.appsModeGrid : R.id.appsModeRow);
        }
        for (String key : OVERLAY_KEYS) {
            FlymeSwitchRow toggle = overlayToggles.get(key);
            SeekBar sizeSeek = overlaySizeSeeks.get(key);
            SeekBar alphaSeek = overlayAlphaSeeks.get(key);
            TextView sizeValue = overlaySizeValues.get(key);
            TextView alphaValue = overlayAlphaValues.get(key);
            if (toggle != null) toggle.setChecked(Prefs.overlayEnabled(c, key));
            int size = Prefs.overlaySizePct(c, key);
            int alpha = Prefs.overlayAlphaPct(c, key);
            if (sizeSeek != null) sizeSeek.setProgress(size - 50);
            if (alphaSeek != null) alphaSeek.setProgress(alpha);
            if (sizeValue != null) sizeValue.setText(getString(R.string.widgets_percent_fmt, size));
            if (alphaValue != null) alphaValue.setText(getString(R.string.widgets_percent_fmt, alpha));
        }
        binding = false;

        updateClockPreview();
        updateModeUi();
        updateAppsUi();
    }

    private void updateModeUi() {
        if (!isAdded() || modeGroup == null) return;
        Context c = requireContext();
        int mode = WidgetModes.current(c);
        binding = true;
        modeGroup.check(mode == Prefs.WIDGET_MODE_STOCK
                ? R.id.widgetModeStock : R.id.widgetModeThird);
        binding = false;

        boolean stock = mode == Prefs.WIDGET_MODE_STOCK;
        // Both implementations keep running in both modes. The mode selector
        // only chooses which settings are shown: overlay settings for the
        // stock launcher tab, AppWidget settings for the third-party tab.
        overlayPanel.setVisibility(stock ? View.VISIBLE : View.GONE);
        standardPanel.setVisibility(stock ? View.GONE : View.VISIBLE);
        speedEnabled.setVisibility(stock ? View.VISIBLE : View.GONE);
        speedPinned.setVisibility(stock ? View.VISIBLE : View.GONE);
        speedReset.setVisibility(stock ? View.VISIBLE : View.GONE);
        if (speedSizeRow != null) {
            speedSizeRow.setVisibility(stock ? View.VISIBLE : View.GONE);
        }
        if (speedSize != null) {
            speedSize.setVisibility(stock ? View.VISIBLE : View.GONE);
        }
        if (speedAlphaRow != null) {
            speedAlphaRow.setVisibility(stock ? View.VISIBLE : View.GONE);
        }
        if (speedAlpha != null) {
            speedAlpha.setVisibility(stock ? View.VISIBLE : View.GONE);
        }
    }

    private void updateAppsUi() {
        if (!isAdded() || appsCount == null || appsSelect == null) return;
        Context c = requireContext();
        int count = Prefs.overlayApps(c).size();
        appsCount.setText(getResources().getQuantityString(
                R.plurals.widgets_apps_selected, count, count));
        appsSelect.setText(count == 0
                ? R.string.widgets_apps_select : R.string.widgets_apps_edit);
    }

    private void openAppsPicker() {
        if (!isAdded()) return;
        Context c = requireContext();
        AppsPickerDialog.show(c, () -> {
            if (!isAdded()) return;
            updateAppsUi();
            OverlayWidgetService.restart(requireContext());
        });
    }

    private void refreshSpeed() {
        if (!isAdded()) return;
        SpeedWidgetProvider.refreshAll(requireContext());
        OverlayWidgetService.sync(requireContext());
    }

    private void updateOpacityText(int progress) {
        if (opacityValue != null) {
            opacityValue.setText(getString(R.string.widgets_percent_fmt, progress));
        }
    }

    private void updateClockPreview() {
        if (clockPreview == null || !isAdded()) return;
        Context c = requireContext();
        Typeface typeface = Prefs.clockFont(c) == 1
                ? Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                : Typeface.SERIF;
        clockPreview.setTypeface(typeface);
        int color;
        switch (Prefs.clockColor(c)) {
            case 1:
                color = Color.WHITE;
                break;
            case 2:
                color = Color.BLACK;
                break;
            default:
                color = ContextCompat.getColor(c, R.color.clock_text);
                break;
        }
        clockPreview.setTextColor(color);
        if (clockDatePreview != null) {
            clockDatePreview.setTypeface(typeface);
            clockDatePreview.setTextColor(color);
            clockDatePreview.setText(dateText());
            clockDatePreview.setVisibility(Prefs.clockDate(c) ? View.VISIBLE : View.GONE);
        }
        updatePreviewScale();
    }

    private void updatePreviewScale() {
        if (clockPreview == null) return;
        // AppWidget size is controlled by the launcher, not by this setting.
        clockPreview.setScaleX(1f);
        clockPreview.setScaleY(1f);
        if (clockDatePreview != null) {
            clockDatePreview.setScaleX(1f);
            clockDatePreview.setScaleY(1f);
        }
    }

    private String dateText() {
        try {
            String value = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"))
                    .format(new Date());
            if (value.isEmpty()) return value;
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        } catch (Throwable t) {
            return "";
        }
    }

    private int colorButton(int mode) {
        if (mode == 1) return R.id.clockColorWhite;
        if (mode == 2) return R.id.clockColorBlack;
        return R.id.clockColorAuto;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        WidgetModes.refreshAuto(requireContext());
        OverlayWidgetService.sync(requireContext());
        updateAll();
        requireView().postDelayed(() -> {
            if (isAdded()) updateModeUi();
        }, 800);
    }
}
