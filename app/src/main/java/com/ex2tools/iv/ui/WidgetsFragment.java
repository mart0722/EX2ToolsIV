package com.ex2tools.iv.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.state.VehicleState;
import com.ex2tools.iv.widgets.ClockWidgetProvider;
import com.ex2tools.iv.widgets.OverlayWidgetService;
import com.ex2tools.iv.widgets.SpeedWidgetProvider;
import com.ex2tools.iv.widgets.WidgetModes;
import com.ex2tools.iv.widgets.WidgetIconSizes;
import com.ex2tools.iv.widgets.WidgetUpdateService;
import com.ex2tools.iv.widgets.WidgetsCommon;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WidgetsFragment extends Fragment {

    private static final String[] OVERLAY_KEYS = {
            WidgetsCommon.SIZE_CLOCK,
            WidgetsCommon.SIZE_DRIVE_REGEN,
            WidgetsCommon.SIZE_DRIVE_MODE,
            WidgetsCommon.SIZE_REGEN,
            WidgetsCommon.SIZE_FAN,
            WidgetsCommon.SIZE_TEMP,
            WidgetsCommon.SIZE_VENT,
            WidgetsCommon.SIZE_VENT_FAN,
            WidgetsCommon.SIZE_HEATING,
            WidgetsCommon.SIZE_WINDSHIELD_HEAT,
            WidgetsCommon.SIZE_BATTERY_RANGE,
            WidgetsCommon.SIZE_WIPERS,
            WidgetsCommon.SIZE_POWER_OFF,
            WidgetsCommon.SIZE_LOCK,
            WidgetsCommon.SIZE_APPS,
            WidgetsCommon.SIZE_CLIMATE
    };

    private static final int[] OVERLAY_LABELS = {
            R.string.widgets_size_clock,
            R.string.widgets_size_drive_regen,
            R.string.widgets_size_drive_mode,
            R.string.widgets_size_regen,
            R.string.widgets_size_fan,
            R.string.widgets_size_temp,
            R.string.widgets_size_vent,
            R.string.widgets_size_vent_fan,
            R.string.widgets_size_heating,
            R.string.widget_name_windshield_heat,
            R.string.widgets_size_battery_range,
            R.string.widget_name_wipers,
            R.string.widget_name_power_off,
            R.string.widgets_size_lock,
            R.string.widgets_size_apps,
            R.string.climate_title
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
    private FlymeSwitchRow speedTrip;
    private View speedTripReset;
    private FlymeSwitchRow overlayLock;
    private FlymeSegmentedGroup overlayPlacement;
    private View overlayNeighborHint;
    private FlymeSegmentedGroup climateIconStyle;
    private FlymeSwitchRow widgetCardBorder;
    private FlymeSwitchRow widgetButtonBorder;
    private FlymeSwitchRow widgetPowerOffWithoutConfirmation;
    private FlymeSwitchRow widgetAccentBoost;
    private FlymeSwitchRow climateIconLabels;
    private FlymeSwitchRow widgetShowTitles;
    private FlymeSwitchRow overlayHeatingSingleRow;
    private final Map<String, FlymeSwitchRow> overlayToggles = new LinkedHashMap<>();
    private final Map<String, FlymeSwitchRow> overlayIconLabels = new LinkedHashMap<>();
    private final Map<String, SeekBar> overlaySizeSeeks = new LinkedHashMap<>();
    private final Map<String, SeekBar> overlayAlphaSeeks = new LinkedHashMap<>();
    private final Map<String, TextView> overlaySizeValues = new LinkedHashMap<>();
    private final Map<String, TextView> overlayAlphaValues = new LinkedHashMap<>();
    private FlymeSegmentedGroup appsModeGroup;
    private TextView appsCount;
    private TextView appsSelect;
    private boolean binding;
    private int nextOverlayRow;
    private Boolean overlayRowsClimateEnabled;
    private final Runnable loadOverlayRows = this::appendOverlayRows;

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
        wireRangeReset(view, c);

        modeGroup = view.findViewById(R.id.widgetModeGroup);
        overlayPanel = view.findViewById(R.id.widgetOverlayPanel);
        standardPanel = view.findViewById(R.id.widgetStandardPanel);
        view.findViewById(R.id.widgetIndividualIconSizes).setOnClickListener(v -> showAppWidgetIconSizes());
        overlayList = view.findViewById(R.id.widgetOverlayList);
        speedEnabled = view.findViewById(R.id.widgetSpeedOverlayEnabled);
        speedPinned = view.findViewById(R.id.widgetSpeedOverlayPinned);
        speedBackground = view.findViewById(R.id.widgetSpeedBackground);
        speedModeGroup = view.findViewById(R.id.widgetSpeedModeGroup);
        speedColorGroup = view.findViewById(R.id.widgetSpeedColorGroup);
        speedSizeRow = view.findViewById(R.id.widgetSpeedSizeRow);
        speedSize = view.findViewById(R.id.widgetSpeedSizeSeek);
        speedSize.setMax(Prefs.WIDGET_SIZE_MAX_PCT);
        speedSize.setMin(Prefs.WIDGET_SIZE_MIN_PCT);
        speedSizeValue = view.findViewById(R.id.widgetSpeedSizeValue);
        speedAlpha = view.findViewById(R.id.widgetSpeedAlphaSeek);
        speedAlphaValue = view.findViewById(R.id.widgetSpeedAlphaValue);
        speedAlphaRow = view.findViewById(R.id.widgetSpeedAlphaRow);
        speedReset = view.findViewById(R.id.widgetSpeedReset);
        speedTrip = view.findViewById(R.id.widgetSpeedTrip);
        speedTripReset = view.findViewById(R.id.widgetSpeedTripReset);
        overlayLock = view.findViewById(R.id.widgetOverlayLock);
        overlayPlacement = view.findViewById(R.id.widgetOverlayPlacement);
        overlayNeighborHint = view.findViewById(R.id.widgetOverlayNeighborHint);
        climateIconStyle = view.findViewById(R.id.widgetClimateIconStyle);
        widgetCardBorder = view.findViewById(R.id.widgetCardBorder);
        widgetButtonBorder = view.findViewById(R.id.widgetButtonBorder);
        widgetPowerOffWithoutConfirmation = view.findViewById(R.id.widgetPowerOffWithoutConfirmation);
        widgetPowerOffWithoutConfirmation.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) Prefs.setWidgetPowerOffWithoutConfirmation(requireContext(), checked);
        });
        widgetAccentBoost = view.findViewById(R.id.widgetAccentBoost);
        climateIconLabels = view.findViewById(R.id.widgetClimateIconLabels);
        widgetShowTitles = view.findViewById(R.id.widgetShowTitles);
        widgetShowTitles.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context context = requireContext();
            Prefs.setWidgetTitles(context, checked);
            VehicleState.get().requestDisplayRefresh();
            WidgetUpdateService.sync(context);
        });
        setupClimateIconControls();

        setupModeControls(c);
        setupSpeedControls(c);
        setupStandardControls(c);
        climateIconStyle.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (binding || !checked) return;
            Context context = requireContext();
            Prefs.ClimateIconStyle style;
            if (checkedId == R.id.widgetClimateIconStandard) style = Prefs.ClimateIconStyle.STANDARD;
            else if (checkedId == R.id.widgetClimateIconThin) style = Prefs.ClimateIconStyle.THIN;
            else if (checkedId == R.id.widgetClimateIconExtraThin) style = Prefs.ClimateIconStyle.EXTRA_THIN;
            else if (checkedId == R.id.widgetClimateIconChromeThin) style = Prefs.ClimateIconStyle.CHROME_THIN;
            else style = Prefs.ClimateIconStyle.CHROME;
            Prefs.setClimateIconStyle(context, style);
            updateAccentControl(context);
            if (opacityPreview != null) {
                opacityPreview.setBackgroundResource(WidgetsCommon.bgDrawableResForPct(context));
            }
            ClockWidgetProvider.refreshAll(context);
            refreshSpeed();
            VehicleState.get().requestDisplayRefresh();
            WidgetUpdateService.sync(context);
        });
        widgetCardBorder.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Prefs.setWidgetCardBorder(requireContext(), checked);
            refreshWidgetBorders();
        });
        widgetButtonBorder.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Prefs.setWidgetButtonBorder(requireContext(), checked);
            refreshWidgetBorders();
        });
        widgetAccentBoost.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context context = requireContext();
            Prefs.setWidgetAccentBoost(context, checked);
            VehicleState.get().requestDisplayRefresh();
            WidgetUpdateService.sync(context);
        });
    }

    private void refreshWidgetBorders() {
        Context context = requireContext();
        if (opacityPreview != null) {
            opacityPreview.setBackgroundResource(WidgetsCommon.bgDrawableResForPct(context));
        }
        ClockWidgetProvider.refreshAll(context);
        refreshSpeed();
        VehicleState.get().requestDisplayRefresh();
        OverlayWidgetService.requestUpdate(context);
        WidgetUpdateService.sync(context);
    }

    private void showAppWidgetIconSizes() {
        Context c = requireContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        List<Integer> ids = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();
        for (AppWidgetProviderInfo provider : manager.getInstalledProvidersForPackage(
                c.getPackageName(), Process.myUserHandle())) {
            if (!WidgetIconSizes.supportsLayout(provider.initialLayout)) continue;
            int[] instances = manager.getAppWidgetIds(provider.provider);
            CharSequence name = provider.loadLabel(c.getPackageManager());
            for (int id : instances) {
                ids.add(id);
                labels.add(instances.length == 1 ? name
                        : getString(R.string.widgets_icon_size_instance, name, id));
            }
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.widgets_individual_icon_sizes)
                .setNegativeButton(android.R.string.cancel, null);
        if (ids.isEmpty()) {
            dialog.setMessage(R.string.widgets_individual_icon_sizes_empty).show();
            return;
        }
        dialog.setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
            int id = ids.get(which);
            AppWidgetProviderInfo provider = manager.getAppWidgetInfo(id);
            if (provider == null) return;
            String key = WidgetIconSizes.appWidgetKey(id);
            LinearLayout row = (LinearLayout) getLayoutInflater().inflate(
                    R.layout.item_widget_icon_size, null);
            FlymeSwitchRow singleRow = provider.initialLayout == R.layout.widget_heating_test
                    ? heatingLayoutSwitch(c) : null;
            if (singleRow != null) row.addView(singleRow);
            FlymeSwitchRow iconLabels = provider.initialLayout == R.layout.widget_wipers
                    || provider.initialLayout == R.layout.widget_power_off ? iconLabelsSwitch(c, key) : null;
            if (iconLabels != null) row.addView(iconLabels);
            int padding = getResources().getDimensionPixelSize(R.dimen.card_padding);
            row.setPadding(padding, padding, padding, 0);
            SeekBar size = bindIconSize(row, Prefs.climateIconSizePct(c, key), pct -> {});
            new MaterialAlertDialogBuilder(c)
                    .setTitle(labels.get(which))
                    .setView(row)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (sizeDialog, button) -> {
                        if (manager.getAppWidgetInfo(id) == null) return;
                        Prefs.setClimateIconSizePct(c, key, size.getProgress());
                        if (iconLabels != null && iconLabels.isChecked() != Prefs.climateIconLabels(c, key)) {
                            Prefs.setClimateIconLabels(c, key, iconLabels.isChecked());
                        }
                        if (singleRow != null) setHeatingLayout(c, singleRow.isChecked());
                        WidgetUpdateService.sync(c);
                    })
                    .show();
        }).show();
    }

    private FlymeSwitchRow heatingLayoutSwitch(Context c) {
        FlymeSwitchRow row = new FlymeSwitchRow(c);
        row.setText(getString(R.string.widgets_heating_single_row));
        row.setChecked(Prefs.heatingSingleRow(c));
        return row;
    }

    private FlymeSwitchRow iconLabelsSwitch(Context c, String key) {
        FlymeSwitchRow row = new FlymeSwitchRow(c);
        row.setText(getString(R.string.widgets_climate_icon_labels));
        row.setChecked(Prefs.climateIconLabels(c, key));
        return row;
    }

    private void setHeatingLayout(Context c, boolean singleRow) {
        if (Prefs.heatingSingleRow(c) == singleRow) return;
        Prefs.setHeatingSingleRow(c, singleRow);
        if (overlayHeatingSingleRow != null) overlayHeatingSingleRow.setChecked(singleRow);
        OverlayWidgetService.restart(c);
    }

    private SeekBar bindIconSize(View row, int pct, java.util.function.IntConsumer onChange) {
        SeekBar size = row.findViewById(R.id.widgetIconSize);
        TextView value = row.findViewById(R.id.widgetIconSizeValue);
        size.setMin(Prefs.CLIMATE_ICON_MIN_PCT);
        size.setMax(Prefs.CLIMATE_ICON_MAX_PCT);
        size.setProgress(pct);
        value.setText(getString(R.string.widgets_percent_fmt, pct));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(getString(R.string.widgets_percent_fmt, progress));
                if (fromUser) onChange.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        return size;
    }

    private void setupClimateIconControls() {
        climateIconLabels.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context c = requireContext();
            Prefs.setClimateIconLabels(c, checked);
            overlayIconLabels.forEach((key, row) -> row.setChecked(Prefs.climateIconLabels(c, key)));
            VehicleState.get().requestDisplayRefresh();
            OverlayWidgetService.requestUpdate(c);
            WidgetUpdateService.sync(c);
        });
    }

    private void updateAccentControl(Context c) {
        widgetAccentBoost.setVisibility(Prefs.climateIconStyle(c) == Prefs.ClimateIconStyle.STANDARD
                ? View.GONE : View.VISIBLE);
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

    private void updateNeighborHint(Prefs.OverlayPlacement placement) {
        overlayNeighborHint.setVisibility(placement == Prefs.OverlayPlacement.NEIGHBORS
                ? View.VISIBLE : View.GONE);
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
        speedTrip.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setSpeedShowTrip(requireContext(), checked);
            refreshSpeed();
        });
        speedTripReset.setOnClickListener(v -> {
            Context ctx = requireContext();
            FlymeDialog.showConfirm(ctx,
                    getString(R.string.widgets_trip_reset_title),
                    getString(R.string.widgets_trip_reset_message),
                    getString(R.string.confirm_yes),
                    getString(R.string.cancel),
                    () -> {
                        boolean ok = WidgetsCommon.resetTrip(ctx);
                        refreshSpeed();
                        android.widget.Toast.makeText(ctx,
                                ok ? R.string.widgets_trip_reset_done
                                        : R.string.widgets_trip_reset_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
        });
        overlayPlacement.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (binding || !checked) return;
            Context context = requireContext();
            Prefs.OverlayPlacement placement = Prefs.OverlayPlacement.FREE;
            if (checkedId == R.id.widgetOverlayPlacementCorners) placement = Prefs.OverlayPlacement.CORNERS;
            if (checkedId == R.id.widgetOverlayPlacementGrid) placement = Prefs.OverlayPlacement.GRID;
            if (checkedId == R.id.widgetOverlayPlacementNeighbor) placement = Prefs.OverlayPlacement.NEIGHBORS;
            Prefs.setOverlayPlacement(context, placement);
            updateNeighborHint(placement);
            OverlayWidgetService.restart(context);
        });
        if (overlayLock != null) {
            overlayLock.setOnCheckedChangeListener((b, checked) -> {
                if (binding) return;
                Prefs.setOverlayLock(requireContext(), checked);
                OverlayWidgetService.restart(requireContext());
            });
        }
        speedSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int pct, boolean fromUser) {
                if (speedSizeValue != null) {
                    speedSizeValue.setText(getString(R.string.widgets_percent_fmt, pct));
                }
                if (fromUser) {
                    Prefs.setWidgetSizePct(requireContext(), WidgetsCommon.SIZE_SPEED, pct);
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
                ClockWidgetProvider.refreshAll(requireContext());
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
            OverlayWidgetService.requestUpdate(requireContext());
        });

        colorGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding) return;
            int mode = checkedId == R.id.clockColorWhite ? 1
                    : checkedId == R.id.clockColorBlack ? 2 : 0;
            Prefs.setClockColor(requireContext(), mode);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(requireContext());
            OverlayWidgetService.requestUpdate(requireContext());
        });

        date.setChecked(Prefs.clockDate(c));
        date.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setClockDate(requireContext(), checked);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(requireContext());
            OverlayWidgetService.requestUpdate(requireContext());
            WidgetUpdateService.refreshOpacity(requireContext());
        });
    }

    private void ensureOverlayRows(Context c) {
        boolean climateEnabled = Prefs.climateEnabled(c);
        if (overlayRowsClimateEnabled == null || overlayRowsClimateEnabled != climateEnabled) {
            overlayRowsClimateEnabled = climateEnabled;
            nextOverlayRow = 0;
            resetOverlayRows();
        }
        if (nextOverlayRow < OVERLAY_KEYS.length) {
            overlayList.removeCallbacks(loadOverlayRows);
            overlayList.postOnAnimation(loadOverlayRows);
        }
    }

    private void resetOverlayRows() {
        overlayList.removeAllViews();
        overlayHeatingSingleRow = null;
        overlayToggles.clear();
        overlayIconLabels.clear();
        overlaySizeSeeks.clear();
        overlayAlphaSeeks.clear();
        overlaySizeValues.clear();
        overlayAlphaValues.clear();
        appsModeGroup = null;
        appsCount = null;
        appsSelect = null;
    }

    private void appendOverlayRows() {
        if (getView() == null || overlayPanel.getVisibility() != View.VISIBLE) return;
        Context c = requireContext();
        LayoutInflater inflater = LayoutInflater.from(c);
        int end = Math.min(nextOverlayRow + 3, OVERLAY_KEYS.length);
        while (nextOverlayRow < end) {
            appendOverlayRow(c, inflater, nextOverlayRow++);
        }
        if (nextOverlayRow < OVERLAY_KEYS.length) overlayList.postOnAnimation(loadOverlayRows);
        else updateAppsUi();
    }

    private void appendOverlayRow(Context c, LayoutInflater inflater, int i) {
        final String key = OVERLAY_KEYS[i];
        if (WidgetsCommon.SIZE_CLIMATE.equals(key) && !Prefs.climateEnabled(c)) {
            return;
        }
        boolean apps = WidgetsCommon.SIZE_APPS.equals(key);
        View card = inflater.inflate(apps
                ? R.layout.item_overlay_apps
                : R.layout.item_overlay_widget, overlayList, false);
        FlymeSwitchRow toggle = card.findViewById(R.id.overlayToggle);
        SeekBar sizeSeek = card.findViewById(R.id.overlaySizeSeek);
        SeekBar alphaSeek = card.findViewById(R.id.overlayAlphaSeek);
        TextView sizeValue = card.findViewById(R.id.overlaySizeValue);
        TextView alphaValue = card.findViewById(R.id.overlayAlphaValue);
        if (WidgetsCommon.isStandaloneVehicleButton(key)) {
            card.findViewById(R.id.overlayAlphaRow).setVisibility(View.GONE);
            alphaSeek.setVisibility(View.GONE);
        }
        if (WidgetsCommon.SIZE_TEMP.equals(key)) {
            LinearLayout controls = card.findViewById(R.id.overlayWidgetControls);
            FlymeSwitchRow wideTemperature = new FlymeSwitchRow(c);
            wideTemperature.setText(getString(R.string.widgets_overlay_wide_temperature));
            wideTemperature.setChecked(Prefs.overlayWideTemperature(c));
            wideTemperature.setOnCheckedChangeListener((button, checked) -> {
                if (binding) return;
                Prefs.setOverlayWideTemperature(c, checked);
                OverlayWidgetService.restart(c);
            });
            controls.addView(wideTemperature, controls.indexOfChild(sizeSeek) + 1);
        }

        if (WidgetIconSizes.supportsOverlay(key)) {
            LinearLayout controls = card.findViewById(R.id.overlayWidgetControls);
            LinearLayout iconSize = (LinearLayout) inflater.inflate(
                    R.layout.item_widget_icon_size, controls, false);
            controls.addView(iconSize, controls.indexOfChild(sizeSeek) + 1);
            if (WidgetsCommon.isStandaloneVehicleButton(key)) {
                FlymeSwitchRow iconLabels = iconLabelsSwitch(c, key);
                iconSize.addView(iconLabels);
                iconLabels.setOnCheckedChangeListener((button, checked) -> {
                    if (binding || checked == Prefs.climateIconLabels(c, key)) return;
                    Prefs.setClimateIconLabels(c, key, checked);
                    OverlayWidgetService.requestUpdate(c);
                });
                overlayIconLabels.put(key, iconLabels);
            }
            if (WidgetsCommon.SIZE_HEATING.equals(key)) {
                overlayHeatingSingleRow = heatingLayoutSwitch(c);
                controls.addView(overlayHeatingSingleRow, controls.indexOfChild(iconSize) + 1);
                overlayHeatingSingleRow.setOnCheckedChangeListener((button, checked) -> {
                    if (binding || Prefs.heatingSingleRow(c) == checked) return;
                    setHeatingLayout(c, checked);
                    WidgetUpdateService.sync(c);
                });
            }
            bindIconSize(iconSize, Prefs.climateIconSizePct(c, key), pct -> {
                Prefs.setClimateIconSizePct(c, key, pct);
                OverlayWidgetService.requestUpdate(c);
            });
        }

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

        FlymeSwitchRow pin = card.findViewById(R.id.overlayPinned);
        if (pin != null) {
            pin.setText(getString(R.string.widgets_pin_over_apps));
            pin.setChecked(Prefs.overlayPinned(c, key));
            pin.setOnCheckedChangeListener((b, checked) -> {
                if (binding) return;
                Prefs.setOverlayPinned(requireContext(), key, checked);
                OverlayWidgetService.restart(requireContext());
            });
        }

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

    private void updateAll() {
        Context c = requireContext();
        int p = Prefs.widgetBgOpacity(c);
        opacity.setProgress(p);
        updateOpacityText(p);
        if (opacityPreview != null) {
            opacityPreview.setBackgroundResource(WidgetsCommon.bgDrawableResForPct(c));
        }

        int speedPct = Prefs.widgetSizePct(c, WidgetsCommon.SIZE_SPEED);
        if (speedSize != null) speedSize.setProgress(speedPct);
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
        speedTrip.setChecked(Prefs.speedShowTrip(c));
        if (overlayLock != null) overlayLock.setChecked(Prefs.overlayLock(c));
        Prefs.OverlayPlacement placement = Prefs.overlayPlacement(c);
        overlayPlacement.check(switch (placement) {
            case FREE -> R.id.widgetOverlayPlacementFree;
            case CORNERS -> R.id.widgetOverlayPlacementCorners;
            case GRID -> R.id.widgetOverlayPlacementGrid;
            case NEIGHBORS -> R.id.widgetOverlayPlacementNeighbor;
        });
        updateNeighborHint(placement);
        climateIconStyle.check(switch (Prefs.climateIconStyle(c)) {
            case STANDARD -> R.id.widgetClimateIconStandard;
            case THIN -> R.id.widgetClimateIconThin;
            case EXTRA_THIN -> R.id.widgetClimateIconExtraThin;
            case CHROME_THIN -> R.id.widgetClimateIconChromeThin;
            case CHROME -> R.id.widgetClimateIconChrome;
        });
        updateAccentControl(c);
        widgetCardBorder.setChecked(Prefs.widgetCardBorder(c));
        widgetButtonBorder.setChecked(Prefs.widgetButtonBorder(c));
        widgetPowerOffWithoutConfirmation.setChecked(Prefs.widgetPowerOffWithoutConfirmation(c));
        widgetAccentBoost.setChecked(Prefs.widgetAccentBoost(c));
        climateIconLabels.setChecked(Prefs.climateIconLabels(c));
        widgetShowTitles.setChecked(Prefs.widgetTitles(c));
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

    private void wireRangeReset(View view, Context c) {
        TextView info = view.findViewById(R.id.widgetRangeInfo);
        View reset = view.findViewById(R.id.widgetRangeReset);
        Context app = c.getApplicationContext();
        if (info != null) {
            new Thread(() -> {
                float estimate = com.ex2tools.iv.car.RangeModel.estimateKm(app);
                float learned = com.ex2tools.iv.car.RangeModel.learnedKm(app);
                info.post(() -> {
                    if (getView() == view && info.getTag() == null) showRangeInfo(info, estimate, learned);
                });
            }, "ex2-widget-range").start();
        }
        if (reset != null) {
            reset.setOnClickListener(v -> {
                com.ex2tools.iv.car.RangeModel.reset(c);
                if (info != null) {
                    info.setTag(Boolean.TRUE);
                    showRangeInfo(info, Float.NaN, 0f);
                }
                Toast.makeText(c, R.string.widgets_range_reset_done, Toast.LENGTH_SHORT).show();
                WidgetUpdateService.start(c);
            });
        }
    }

    private void showRangeInfo(TextView info, float estimate, float learned) {
        String text = Float.isNaN(estimate)
                ? getString(R.string.widgets_range_info, String.format(Locale.US, "%.0f", learned))
                : getString(R.string.widgets_range_learned, String.format(Locale.US, "%.0f", estimate));
        info.setText(text);
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
        // The selector only changes the settings panel. Both widget types can run together.
        overlayPanel.setVisibility(stock ? View.VISIBLE : View.GONE);
        if (stock) ensureOverlayRows(c);
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
        // Настройки оверлея (цифровой/стрелочный, фон, пробег, размер) применяем
        // сразу: сервис пересоздаёт вид, иначе они ждут следующего изменения VHAL.
        OverlayWidgetService.restart(requireContext());
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
        View view = requireView();
        updateAll();
        view.postDelayed(() -> {
            if (getView() != view) return;
            OverlayWidgetService.sync(requireContext());
            updateModeUi();
        }, 100);
    }

    @Override
    public void onDestroyView() {
        overlayList.removeCallbacks(loadOverlayRows);
        overlayRowsClimateEnabled = null;
        nextOverlayRow = 0;
        super.onDestroyView();
    }
}
