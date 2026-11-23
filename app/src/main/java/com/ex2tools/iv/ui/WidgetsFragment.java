package com.ex2tools.iv.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.RangeModel;
import com.ex2tools.iv.state.VehicleState;
import com.ex2tools.iv.widgets.ClockWidgetProvider;
import com.ex2tools.iv.widgets.OverlayWidgetService;
import com.ex2tools.iv.widgets.SpeedWidgetProvider;
import com.ex2tools.iv.widgets.WidgetModes;
import com.ex2tools.iv.widgets.WidgetCatalog;
import com.ex2tools.iv.widgets.WidgetIconSizes;
import com.ex2tools.iv.widgets.WidgetUpdateService;
import com.ex2tools.iv.widgets.WidgetsCommon;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public final class WidgetsFragment extends Fragment {

    private static final String[] OVERLAY_KEYS = {
            WidgetsCommon.SIZE_SPEED,
            WidgetsCommon.SIZE_CLOCK,
            WidgetsCommon.SIZE_OUTSIDE_TEMP,
            WidgetsCommon.SIZE_DRIVE_REGEN,
            WidgetsCommon.SIZE_DRIVE_MODE,
            WidgetsCommon.SIZE_REGEN,
            WidgetsCommon.SIZE_FAN,
            WidgetsCommon.SIZE_TEMP,
            WidgetsCommon.SIZE_VENT,
            WidgetsCommon.SIZE_RECIRC,
            WidgetsCommon.SIZE_HEATING,
            WidgetsCommon.SIZE_WINDSHIELD_HEAT,
            WidgetsCommon.SIZE_WHEEL_HEAT,
            WidgetsCommon.SIZE_BATTERY_RANGE,
            WidgetsCommon.SIZE_REAL_RANGE,
            WidgetsCommon.SIZE_WIPERS,
            WidgetsCommon.SIZE_POWER_OFF,
            WidgetsCommon.SIZE_LOCK,
            WidgetsCommon.SIZE_CLIMATE
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
    private LinearLayout standardList;
    private View standardEmpty;
    private View clockSettings;
    private View speedSettings;
    private View ventSettings;
    private View rangeSettings;
    private View powerSettings;
    private int rangeRefresh;
    private FlymeSwitchRow speedBackground;
    private FlymeSegmentedGroup speedModeGroup;
    private FlymeSegmentedGroup speedColorGroup;
    private View speedReset;
    private FlymeSwitchRow speedTrip;
    private View speedTripReset;
    private FlymeSwitchRow overlayLock;
    private FlymeSegmentedGroup overlayPlacement;
    private View overlayNeighborHint;
    private FlymeSegmentedGroup climateIconStyle;
    private FlymeSegmentedGroup ventMode;
    private FlymeSwitchRow ventShowRecirc;
    private View glassScaleControls;
    private final Handler scaleHandler = new Handler(Looper.getMainLooper());
    private final Runnable applyGlassScale = this::refreshGlassScale;
    private FlymeSwitchRow widgetCardBorder;
    private FlymeSwitchRow widgetButtonBorder;
    private FlymeSwitchRow widgetPowerOffWithoutConfirmation;
    private FlymeSwitchRow widgetAccentBoost;
    private FlymeSwitchRow climateIconLabels;
    private FlymeSwitchRow widgetShowTitles;
    private final List<OverlayCard> overlayCards = new ArrayList<>();
    private final Map<String, FlymeSwitchRow> individualIconLabels = new LinkedHashMap<>();
    private final Map<String, Integer> selectedAppWidgets = new LinkedHashMap<>();
    private OverlayCard appsOverlayCard;
    private TextView appsCount;
    private TextView appsSelect;
    private boolean binding;
    private int nextOverlayRow;
    private Boolean overlayRowsClimateEnabled;
    private Boolean showingOverlay;
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
        clockSettings = view.findViewById(R.id.widgetSettingsClock);
        speedSettings = view.findViewById(R.id.widgetSettingsSpeed);
        ventSettings = view.findViewById(R.id.widgetSettingsVent);
        rangeSettings = view.findViewById(R.id.widgetSettingsRange);
        powerSettings = view.findViewById(R.id.widgetSettingsPower);
        wireRangeReset(c);

        modeGroup = view.findViewById(R.id.widgetModeGroup);
        overlayPanel = view.findViewById(R.id.widgetOverlayPanel);
        standardPanel = view.findViewById(R.id.widgetStandardPanel);
        overlayList = view.findViewById(R.id.widgetOverlayList);
        standardList = view.findViewById(R.id.widgetStandardList);
        standardEmpty = view.findViewById(R.id.widgetStandardEmpty);
        speedBackground = view.findViewById(R.id.widgetSpeedBackground);
        speedModeGroup = view.findViewById(R.id.widgetSpeedModeGroup);
        speedColorGroup = view.findViewById(R.id.widgetSpeedColorGroup);
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
            OverlayWidgetService.requestUpdate(context);
            WidgetUpdateService.sync(context);
        });
        setupClimateIconControls();
        setupGlassScale(view, c);
        ventMode = view.findViewById(R.id.widgetVentMode);
        ventMode.addOnButtonCheckedListener((group, id, checked) -> {
            if (binding || !checked) return;
            Prefs.setVentDirectionButtons(c, id == R.id.widgetVentThree);
            OverlayWidgetService.restart(c);
            WidgetUpdateService.sync(c);
        });

        ventShowRecirc = view.findViewById(R.id.widgetVentShowRecirc);
        ventShowRecirc.setOnCheckedChangeListener((button, checked) -> {
            if (binding || Prefs.showVentRecirc(c) == checked) return;
            Prefs.setShowVentRecirc(c, checked);
            OverlayWidgetService.restart(c);
            WidgetUpdateService.sync(c);
        });

        setupModeControls();
        view.findViewById(R.id.widgetStockCardsHide)
                .setOnClickListener(v -> setStockCardsHidden(c, true));
        view.findViewById(R.id.widgetStockCardsShow)
                .setOnClickListener(v -> setStockCardsHidden(c, false));
        setupSpeedControls();
        setupPlacementControls();
        setupStandardControls(c);
        climateIconStyle.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (binding || !checked) return;
            Context context = requireContext();
            Prefs.ClimateIconStyle style;
            if (checkedId == R.id.widgetClimateIconStandard) style = Prefs.ClimateIconStyle.STANDARD;
            else if (checkedId == R.id.widgetClimateIconThin) style = Prefs.ClimateIconStyle.THIN;
            else if (checkedId == R.id.widgetClimateIconChromeThin) style = Prefs.ClimateIconStyle.CHROME_THIN;
            else style = Prefs.ClimateIconStyle.STANDARD;
            Prefs.setClimateIconStyle(context, style);
            updateAppearanceControls(context);
            OverlayWidgetService.restart(context);
            if (Boolean.FALSE.equals(showingOverlay)) renderStandardWidgets(context);
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
            OverlayWidgetService.requestUpdate(context);
            WidgetUpdateService.sync(context);
        });
        appsOverlayCard = new OverlayCard(c, WidgetsCommon.SIZE_APPS,
                view.findViewById(R.id.widgetAppsOverlay));
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

    private record AppWidgetSetting(int id, int layout, CharSequence name) {}

    private void renderStandardWidgets(Context c) {
        standardList.removeAllViews();
        individualIconLabels.clear();
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        Map<String, List<AppWidgetSetting>> widgets = new LinkedHashMap<>();
        for (AppWidgetProviderInfo provider : manager.getInstalledProvidersForPackage(
                c.getPackageName(), Process.myUserHandle())) {
            int layout = provider.initialLayout;
            String key = standardSettingsKey(layout);
            if (key == null || (sharedSettings(key) == null
                    && !hasAppWidgetOptions(c, layout))) continue;
            int[] ids = manager.getAppWidgetIds(provider.provider);
            CharSequence name = provider.loadLabel(c.getPackageManager());
            for (int id : ids) {
                CharSequence label = ids.length == 1 ? name
                        : getString(R.string.widgets_icon_size_instance, name, id);
                widgets.computeIfAbsent(key, unused -> new ArrayList<>())
                        .add(new AppWidgetSetting(id, layout, label));
            }
        }
        for (String key : OVERLAY_KEYS) {
            List<AppWidgetSetting> instances = widgets.get(key);
            if (instances != null) addStandardWidget(c, key, instances);
        }
        standardEmpty.setVisibility(widgets.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addStandardWidget(Context c, String key, List<AppWidgetSetting> instances) {
        View card = getLayoutInflater().inflate(R.layout.item_overlay_widget, standardList, false);
        card.findViewById(R.id.overlayToggle).setVisibility(View.GONE);
        TextView title = card.findViewById(R.id.standardWidgetTitle);
        title.setText(widgetLabel(key));
        title.setVisibility(View.VISIBLE);

        LinearLayout body = card.findViewById(R.id.widgetSettingsBody);
        body.setVisibility(View.VISIBLE);
        addSharedSettings(key, body, true);
        if (instances.stream().anyMatch(instance -> hasAppWidgetOptions(c, instance.layout()))) {
            if (instances.size() == 1) {
                addAppWidgetOptions(c, body, instances.get(0));
            } else {
                addAppWidgetSelector(c, body, key, instances);
            }
        }
        if (WidgetsCommon.SIZE_HEATING.equals(key)) addHeatingSettings(c, body);
        standardList.addView(card);
    }

    private void addAppWidgetSelector(Context c, LinearLayout body, String key,
                                      List<AppWidgetSetting> instances) {
        Button selector = new Button(c);
        LinearLayout options = new LinearLayout(c);
        options.setOrientation(LinearLayout.VERTICAL);
        body.addView(selector);
        body.addView(options);
        CharSequence[] names = new CharSequence[instances.size()];
        int selected = 0;
        for (int i = 0; i < instances.size(); i++) {
            AppWidgetSetting instance = instances.get(i);
            names[i] = instance.name();
            if (instance.id() == selectedAppWidgets.getOrDefault(key, -1)) selected = i;
        }
        IntConsumer select = index -> {
            AppWidgetSetting instance = instances.get(index);
            selectedAppWidgets.put(key, instance.id());
            selector.setText(instance.name());
            options.removeAllViews();
            addAppWidgetOptions(c, options, instance);
        };
        select.accept(selected);
        selector.setOnClickListener(v -> {
            int checked = 0;
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).id() == selectedAppWidgets.getOrDefault(key, -1)) checked = i;
            }
            new MaterialAlertDialogBuilder(c)
                    .setTitle(widgetLabel(key))
                    .setSingleChoiceItems(names, checked, (dialog, index) -> {
                        select.accept(index);
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private String standardSettingsKey(int layout) {
        if (layout == R.layout.widget_clock_classic || layout == R.layout.widget_clock_classic_date
                || layout == R.layout.widget_clock_large) return WidgetsCommon.SIZE_CLOCK;
        if (layout == R.layout.widget_hvac_direction_vertical
                || layout == R.layout.widget_hvac_direction_fan) return WidgetsCommon.SIZE_VENT;
        for (String key : OVERLAY_KEYS) {
            if (WidgetCatalog.layout(key) == layout) return key;
        }
        return null;
    }

    private static boolean hasAppWidgetOptions(Context c, int layout) {
        return hasIndividualLabels(layout)
                || (!usesGlass(c) && WidgetIconSizes.supportsLayout(layout));
    }

    private void addAppWidgetOptions(Context c, LinearLayout body, AppWidgetSetting widget) {
        String key = WidgetIconSizes.appWidgetKey(widget.id());
        if (hasIndividualLabels(widget.layout())) {
            addIconLabels(c, body, key, () -> WidgetUpdateService.sync(c));
        }
        if (!usesGlass(c) && WidgetIconSizes.supportsLayout(widget.layout())) {
            addPercentRow(body, R.string.widgets_climate_icon_size, Prefs.CLIMATE_ICON_MIN_PCT,
                    Prefs.CLIMATE_ICON_MAX_PCT, Prefs.climateIconSizePct(c, key), true,
                    pct -> Prefs.setClimateIconSizePct(c, key, pct), () -> WidgetUpdateService.sync(c));
        }
    }

    private static boolean hasIndividualLabels(int layout) {
        return layout == R.layout.widget_wipers || layout == R.layout.widget_power_off
                || layout == R.layout.widget_wheel_heat || layout == R.layout.widget_recirc;
    }

    private void addIconLabels(Context c, LinearLayout body, String key, Runnable onChange) {
        FlymeSwitchRow row = new FlymeSwitchRow(c);
        row.setText(getString(R.string.widgets_climate_icon_labels));
        row.setChecked(Prefs.climateIconLabels(c, key));
        row.setOnCheckedChangeListener((button, checked) -> {
            if (binding || checked == Prefs.climateIconLabels(c, key)) return;
            Prefs.setClimateIconLabels(c, key, checked);
            onChange.run();
        });
        individualIconLabels.put(key, row);
        body.addView(row);
    }

    private void addHeatingSettings(Context c, LinearLayout body) {
        FlymeSwitchRow singleRow = new FlymeSwitchRow(c);
        singleRow.setText(getString(R.string.widgets_heating_single_row));
        singleRow.setChecked(Prefs.heatingSingleRow(c));
        FlymeSwitchRow hideMaxAir = new FlymeSwitchRow(c);
        hideMaxAir.setText(getString(R.string.widgets_heating_hide_max_air));
        hideMaxAir.setChecked(Prefs.heatingHideMaxAir(c));
        hideMaxAir.setVisibility(singleRow.isChecked() ? View.VISIBLE : View.GONE);
        Runnable apply = () -> {
            if (binding) return;
            Prefs.setHeatingLayout(c, singleRow.isChecked(), hideMaxAir.isChecked());
            hideMaxAir.setVisibility(singleRow.isChecked() ? View.VISIBLE : View.GONE);
            OverlayWidgetService.restart(c);
            WidgetUpdateService.sync(c);
        };
        singleRow.setOnCheckedChangeListener((button, checked) -> apply.run());
        hideMaxAir.setOnCheckedChangeListener((button, checked) -> apply.run());
        body.addView(singleRow);
        body.addView(hideMaxAir);
    }

    private View sharedSettings(String key) {
        return switch (key) {
            case WidgetsCommon.SIZE_CLOCK -> clockSettings;
            case WidgetsCommon.SIZE_SPEED -> speedSettings;
            case WidgetsCommon.SIZE_VENT -> ventSettings;
            case WidgetsCommon.SIZE_BATTERY_RANGE -> rangeSettings;
            case WidgetsCommon.SIZE_POWER_OFF -> powerSettings;
            default -> null;
        };
    }

    private void addSharedSettings(String key, LinearLayout body, boolean standard) {
        View settings = sharedSettings(key);
        if (settings == null) return;
        if (settings.getParent() instanceof ViewGroup oldParent) oldParent.removeView(settings);
        body.addView(settings);
        if (settings == speedSettings) speedReset.setVisibility(standard ? View.GONE : View.VISIBLE);
        if (settings == rangeSettings) refreshRangeInfo();
    }

    private View addPercentRow(LinearLayout body, int label, int min, int max, int pct,
                               boolean scale, IntConsumer save, Runnable onFinish) {
        View row = getLayoutInflater().inflate(R.layout.item_widget_icon_size, body, false);
        ((TextView) row.findViewById(R.id.widgetIconSizeLabel)).setText(label);
        row.findViewById(R.id.widgetIconSize).setContentDescription(getString(label));
        bindPercent(row, min, max, pct, scale, save, onFinish);
        body.addView(row);
        return row;
    }

    private void bindPercent(View row, int min, int max, int pct,
                             boolean scale, IntConsumer save, Runnable onFinish) {
        SeekBar seek = row.findViewById(R.id.widgetIconSize);
        TextView value = row.findViewById(R.id.widgetIconSizeValue);
        View minus = row.findViewById(R.id.widgetScaleMinus);
        View plus = row.findViewById(R.id.widgetScalePlus);
        row.findViewById(R.id.widgetScaleSteps).setVisibility(scale ? View.VISIBLE : View.GONE);
        seek.setMax(max);
        seek.setMin(min);
        seek.setProgress(pct);
        IntConsumer showValue = progress -> {
            value.setText(getString(R.string.widgets_percent_fmt, progress));
            minus.setEnabled(progress > min);
            plus.setEnabled(progress < max);
        };
        showValue.accept(seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean dragging;

            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                showValue.accept(progress);
                if (fromUser) {
                    save.accept(progress);
                    if (!dragging) onFinish.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                onFinish.run();
            }
        });
        IntConsumer step = delta -> {
            int current = seek.getProgress();
            int progress = Math.max(min, Math.min(max, current + delta));
            if (progress == current) return;
            seek.setProgress(progress);
            save.accept(progress);
            onFinish.run();
        };
        minus.setOnClickListener(v -> step.accept(-5));
        plus.setOnClickListener(v -> step.accept(5));
    }

    private void setupClimateIconControls() {
        climateIconLabels.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            Context c = requireContext();
            Prefs.setClimateIconLabels(c, checked);
            binding = true;
            individualIconLabels.forEach((key, row) ->
                    row.setChecked(Prefs.climateIconLabels(c, key)));
            binding = false;
            VehicleState.get().requestDisplayRefresh();
            OverlayWidgetService.requestUpdate(c);
            WidgetUpdateService.sync(c);
        });
    }

    private static boolean usesGlass(Context c) {
        return Prefs.climateIconStyle(c) != Prefs.ClimateIconStyle.STANDARD;
    }

    private void updateAppearanceControls(Context c) {
        boolean glass = usesGlass(c);
        TextView speedColorDefault = speedColorGroup.findViewById(R.id.widgetSpeedColorWhite);
        speedColorDefault.setText(Prefs.climateIconStyle(c).isChrome()
                ? R.string.widgets_speed_color_chrome : R.string.widgets_speed_color_white);
        widgetAccentBoost.setVisibility(View.VISIBLE);
        glassScaleControls.setVisibility(glass ? View.VISIBLE : View.GONE);
        if (appsOverlayCard != null) appsOverlayCard.updateAppearance();
        for (OverlayCard card : overlayCards) card.updateAppearance();
    }

    private void setupGlassScale(View view, Context c) {
        glassScaleControls = view.findViewById(R.id.widgetGlassScaleControls);
        bindGlassScale(view.findViewById(R.id.widgetGlassSize), R.string.widgets_glass_scale,
                Prefs.WIDGET_SIZE_MIN_PCT, Prefs.WIDGET_SIZE_MAX_PCT,
                Prefs.glassWidgetSizePct(c), pct -> Prefs.setGlassWidgetSizePct(c, pct));
        bindGlassScale(view.findViewById(R.id.widgetGlassIconSize), R.string.widgets_glass_icon_scale,
                Prefs.CLIMATE_ICON_MIN_PCT, Prefs.CLIMATE_ICON_MAX_PCT,
                Prefs.glassWidgetIconSizePct(c), pct -> Prefs.setGlassWidgetIconSizePct(c, pct));
    }

    private void bindGlassScale(View row, int label, int min, int max, int current,
                                IntConsumer save) {
        TextView title = row.findViewById(R.id.widgetIconSizeLabel);
        title.setText(label);
        SeekBar size = row.findViewById(R.id.widgetIconSize);
        size.setContentDescription(getString(label));
        bindPercent(row, min, max, current, true, pct -> {
            save.accept(pct);
            scaleHandler.removeCallbacks(applyGlassScale);
            scaleHandler.postDelayed(applyGlassScale, 200);
        }, () -> {});
    }

    private void refreshGlassScale() {
        if (!isAdded()) return;
        Context c = requireContext();
        refreshSpeed();
        ClockWidgetProvider.refreshAll(c);
        WidgetUpdateService.sync(c);
    }

    private void setupModeControls() {
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
            updateModeUi(false);
        });
    }

    private void setStockCardsHidden(Context context, boolean hidden) {
        Intent intent = new Intent("com.flyme.auto.launcher.action.TOGGLE_AICY_WIDGET")
                .setPackage(WidgetModes.STOCK_LAUNCHER)
                .putExtra("is_hide", hidden);
        try {
            context.getPackageManager().getPackageInfo(WidgetModes.STOCK_LAUNCHER, 0);
            context.sendOrderedBroadcast(intent, null);
            Toast.makeText(context, R.string.widgets_stock_cards_sent, Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, R.string.widgets_stock_cards_launcher_missing,
                    Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            LogStore.add(context, "stock launcher cards command failed: " + e);
            Toast.makeText(context, R.string.widgets_stock_cards_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNeighborHint(Prefs.OverlayPlacement placement) {
        overlayNeighborHint.setVisibility(placement == Prefs.OverlayPlacement.NEIGHBORS
                ? View.VISIBLE : View.GONE);
    }

    private void setupSpeedControls() {
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
        speedReset.setOnClickListener(v -> {
            Context c = requireContext();
            Prefs.setOverlayPos(c, WidgetsCommon.SIZE_SPEED, 40, 160);
            OverlayWidgetService.restart(c);
        });
    }

    private void setupPlacementControls() {
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
            Context context = requireContext();
            Prefs.setClockDate(context, checked);
            updateClockPreview();
            ClockWidgetProvider.refreshAll(context);
            if (usesGlass(context)) OverlayWidgetService.restart(context);
            else OverlayWidgetService.requestUpdate(context);
            WidgetUpdateService.refreshOpacity(context);
        });
    }

    private void ensureOverlayRows(Context c) {
        boolean climateEnabled = Prefs.climateEnabled(c);
        if (overlayRowsClimateEnabled == null || overlayRowsClimateEnabled != climateEnabled) {
            resetOverlayRows();
            overlayRowsClimateEnabled = climateEnabled;
        }
        if (nextOverlayRow < OVERLAY_KEYS.length) {
            overlayList.removeCallbacks(loadOverlayRows);
            overlayList.postOnAnimation(loadOverlayRows);
        }
    }

    private void resetOverlayRows() {
        overlayList.removeCallbacks(loadOverlayRows);
        overlayList.removeAllViews();
        overlayRowsClimateEnabled = null;
        nextOverlayRow = 0;
        overlayCards.clear();
        individualIconLabels.clear();
    }

    private void appendOverlayRows() {
        if (getView() == null || !Boolean.TRUE.equals(showingOverlay)) return;
        Context c = requireContext();
        int end = Math.min(nextOverlayRow + 3, OVERLAY_KEYS.length);
        while (nextOverlayRow < end) {
            String key = OVERLAY_KEYS[nextOverlayRow++];
            if (!WidgetsCommon.SIZE_CLIMATE.equals(key) || Prefs.climateEnabled(c)) {
                overlayCards.add(new OverlayCard(c, key, overlayList));
            }
        }
        if (nextOverlayRow < OVERLAY_KEYS.length) overlayList.postOnAnimation(loadOverlayRows);
    }

    private static int widgetLabel(String key) {
        return WidgetsCommon.SIZE_OUTSIDE_TEMP.equals(key)
                ? R.string.widgets_variant_outside_temp : WidgetCatalog.get(key).labelRes;
    }

    private record OverlayVariants(String alternate, int primaryLabel, int alternateLabel) {
        static OverlayVariants forWidget(String key) {
            return switch (key) {
                case WidgetsCommon.SIZE_CLOCK -> new OverlayVariants(WidgetsCommon.SIZE_CLOCK_LARGE,
                        R.string.widgets_variant_clock_regular, R.string.widgets_variant_clock_large);
                case WidgetsCommon.SIZE_OUTSIDE_TEMP -> new OverlayVariants(WidgetsCommon.SIZE_OUTSIDE_TEMP_WIDE,
                        R.string.widgets_variant_square, R.string.widgets_variant_wide);
                case WidgetsCommon.SIZE_VENT -> new OverlayVariants(WidgetsCommon.SIZE_VENT_FAN,
                        R.string.widgets_variant_airflow, R.string.widgets_variant_airflow_fan);
                default -> null;
            };
        }
    }

    private final class OverlayCard {
        private final Context context;
        private final String key;
        private final OverlayVariants variants;
        private final FlymeSwitchRow toggle;
        private final LinearLayout body;
        private final LinearLayout options;
        private FlymeSegmentedGroup format;
        private String configuredKey;
        private View sizeRow;
        private View alphaRow;
        private View iconRow;

        OverlayCard(Context context, String key, LinearLayout list) {
            this.context = context;
            this.key = key;
            variants = OverlayVariants.forWidget(key);
            LayoutInflater inflater = getLayoutInflater();
            View card = inflater.inflate(R.layout.item_overlay_widget, list, false);
            toggle = card.findViewById(R.id.overlayToggle);
            toggle.setText(getString(widgetLabel(key)));
            body = card.findViewById(R.id.widgetSettingsBody);
            if (variants != null) {
                format = (FlymeSegmentedGroup) inflater.inflate(
                        R.layout.item_overlay_widget_variants, body, false);
                ((TextView) format.findViewById(R.id.overlayVariantDefault))
                        .setText(variants.primaryLabel());
                ((TextView) format.findViewById(R.id.overlayVariantAlternate))
                        .setText(variants.alternateLabel());
                format.addOnButtonCheckedListener((group, id, checked) -> {
                    if (binding || !checked || !toggle.isChecked()) return;
                    Prefs.setOverlayVariant(context, key, variants.alternate(),
                            id == R.id.overlayVariantAlternate, true);
                    refresh();
                    OverlayWidgetService.restart(context);
                });
                body.addView(format);
            }
            options = new LinearLayout(context);
            options.setOrientation(LinearLayout.VERTICAL);
            body.addView(options);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (binding) return;
                if (variants == null) {
                    Prefs.setOverlayEnabled(context, key, checked);
                } else {
                    Prefs.setOverlayVariant(context, key, variants.alternate(), alternateSelected(), checked);
                }
                refresh();
                OverlayWidgetService.sync(context);
                if (checked && (WidgetsCommon.SIZE_APPS.equals(key)
                        || WidgetsCommon.SIZE_REAL_RANGE.equals(key)) && requestOverlayPermission()) {
                    return;
                }
                if (checked && WidgetsCommon.SIZE_APPS.equals(key) && Prefs.overlayApps(context).isEmpty()) {
                    toggle.post(WidgetsFragment.this::openAppsPicker);
                }
            });
            list.addView(card);
            refresh();
        }

        private boolean alternateSelected() {
            return variants != null && Prefs.overlayAlternateSelected(context, key, variants.alternate());
        }

        void refresh() {
            boolean enabled = Prefs.overlayEnabled(context, key)
                    || (variants != null && Prefs.overlayEnabled(context, variants.alternate()));
            boolean alternate = alternateSelected();
            boolean wasExpanded = body.getVisibility() == View.VISIBLE;
            boolean wasBinding = binding;
            binding = true;
            toggle.setChecked(enabled);
            if (format != null) {
                format.check(alternate ? R.id.overlayVariantAlternate : R.id.overlayVariantDefault);
            }
            binding = wasBinding;
            body.setVisibility(enabled ? View.VISIBLE : View.GONE);
            if (!enabled) return;

            String selectedKey = alternate ? variants.alternate() : key;
            if (!selectedKey.equals(configuredKey)) {
                configure(selectedKey);
            } else if (!wasExpanded && WidgetsCommon.SIZE_BATTERY_RANGE.equals(key)) {
                refreshRangeInfo();
            }
        }

        private void configure(String selectedKey) {
            configuredKey = selectedKey;
            options.removeAllViews();
            sizeRow = null;
            alphaRow = null;
            iconRow = null;
            addSharedSettings(key, options, false);

            boolean apps = WidgetsCommon.SIZE_APPS.equals(key);
            boolean speed = WidgetsCommon.SIZE_SPEED.equals(key);
            FlymeSwitchRow pin = new FlymeSwitchRow(context);
            pin.setText(getString(R.string.widgets_pin_over_apps));
            pin.setChecked(Prefs.overlayPinned(context, selectedKey));
            pin.setOnCheckedChangeListener((button, checked) -> {
                if (binding) return;
                Prefs.setOverlayPinned(context, selectedKey, checked);
                if (apps && checked && requestOverlayPermission()) return;
                OverlayWidgetService.restart(context);
            });
            options.addView(pin);
            if (apps) addAppsSettings(context, options);

            Runnable restart = () -> OverlayWidgetService.restart(context);
            sizeRow = addPercentRow(options,
                    speed ? R.string.widgets_speed_size : R.string.widgets_overlay_size,
                    Prefs.WIDGET_SIZE_MIN_PCT, speed ? Prefs.WIDGET_SIZE_MAX_PCT : 200,
                    speed ? Prefs.widgetSizePct(context, selectedKey)
                            : Prefs.overlaySizePct(context, selectedKey),
                    true, pct -> {
                        if (speed) Prefs.setWidgetSizePct(context, selectedKey, pct);
                        else Prefs.setOverlaySizePct(context, selectedKey, pct);
                    }, restart);
            if (!WidgetsCommon.isStandaloneVehicleButton(selectedKey)) {
                alphaRow = addPercentRow(options, R.string.widgets_overlay_alpha, 0, 100,
                        Prefs.overlayAlphaPct(context, selectedKey), false,
                        pct -> Prefs.setOverlayAlphaPct(context, selectedKey, pct), restart);
            }

            if (WidgetsCommon.SIZE_TEMP.equals(key)) {
                FlymeSwitchRow wide = new FlymeSwitchRow(context);
                wide.setText(getString(R.string.widgets_overlay_wide_temperature));
                wide.setChecked(Prefs.overlayWideTemperature(context));
                wide.setOnCheckedChangeListener((button, checked) -> {
                    if (binding) return;
                    Prefs.setOverlayWideTemperature(context, checked);
                    restart.run();
                });
                options.addView(wide);
            }
            if (WidgetIconSizes.supportsOverlay(selectedKey)) {
                iconRow = addPercentRow(options, R.string.widgets_climate_icon_size,
                        Prefs.CLIMATE_ICON_MIN_PCT, Prefs.CLIMATE_ICON_MAX_PCT,
                        Prefs.climateIconSizePct(context, selectedKey), true,
                        pct -> Prefs.setClimateIconSizePct(context, selectedKey, pct),
                        () -> OverlayWidgetService.requestUpdate(context));
                if (WidgetsCommon.isStandaloneVehicleButton(selectedKey)) {
                    addIconLabels(context, options, selectedKey,
                            () -> OverlayWidgetService.requestUpdate(context));
                }
            }
            if (WidgetsCommon.SIZE_HEATING.equals(key)) addHeatingSettings(context, options);
            updateAppearance();
        }

        void updateAppearance() {
            boolean glass = usesGlass(context);
            if (sizeRow != null) sizeRow.setVisibility(glass ? View.GONE : View.VISIBLE);
            if (iconRow != null) iconRow.setVisibility(glass ? View.GONE : View.VISIBLE);
            if (alphaRow != null && WidgetsCommon.SIZE_WINDSHIELD_HEAT.equals(key)) {
                alphaRow.setVisibility(glass ? View.GONE : View.VISIBLE);
            }
        }
    }

    private boolean requestOverlayPermission() {
        Context c = requireContext();
        if (Settings.canDrawOverlays(c)) return false;
        Toast.makeText(c, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
        ((MainActivity) requireActivity()).openOverlaySettings();
        return true;
    }

    private void addAppsSettings(Context c, LinearLayout body) {
        View settings = getLayoutInflater().inflate(R.layout.widget_settings_apps, body, false);
        FlymeSegmentedGroup appsModeGroup = settings.findViewById(R.id.appsModeGroup);
        appsCount = settings.findViewById(R.id.appsCount);
        appsSelect = settings.findViewById(R.id.appsSelect);
        appsModeGroup.check(Prefs.overlayAppsLayout(c) == 1 ? R.id.appsModeGrid : R.id.appsModeRow);
        appsModeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (binding || !checked) return;
            Prefs.setOverlayAppsLayout(c, id == R.id.appsModeGrid ? 1 : 0);
            OverlayWidgetService.restart(c);
        });
        appsSelect.setOnClickListener(v -> openAppsPicker());
        body.addView(settings);
        updateAppsUi();
    }

    private void updateAll() {
        Context c = requireContext();
        int p = Prefs.widgetBgOpacity(c);
        opacity.setProgress(p);
        updateOpacityText(p);
        if (opacityPreview != null) {
            opacityPreview.setBackgroundResource(WidgetsCommon.bgDrawableResForPct(c));
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
            case EXTRA_THIN -> R.id.widgetClimateIconThin;
            case CHROME_THIN -> R.id.widgetClimateIconChromeThin;
            case CHROME -> R.id.widgetClimateIconStandard;
        });
        updateAppearanceControls(c);
        ventMode.check(Prefs.ventDirectionButtons(c) ? R.id.widgetVentThree : R.id.widgetVentFive);
        ventShowRecirc.setChecked(Prefs.showVentRecirc(c));
        widgetCardBorder.setChecked(Prefs.widgetCardBorder(c));
        widgetButtonBorder.setChecked(Prefs.widgetButtonBorder(c));
        widgetPowerOffWithoutConfirmation.setChecked(Prefs.widgetPowerOffWithoutConfirmation(c));
        widgetAccentBoost.setChecked(Prefs.widgetAccentBoost(c));
        climateIconLabels.setChecked(Prefs.climateIconLabels(c));
        widgetShowTitles.setChecked(Prefs.widgetTitles(c));
        binding = false;

        updateClockPreview();
        updateModeUi(true);
        if (Boolean.TRUE.equals(showingOverlay) && rangeSettings.isShown()) refreshRangeInfo();
    }

    private void wireRangeReset(Context c) {
        rangeSettings.findViewById(R.id.widgetRangeReset).setOnClickListener(v -> {
            rangeRefresh++;
            RangeModel.reset(c);
            showRangeInfo(rangeSettings.findViewById(R.id.widgetRangeInfo), Float.NaN, 0f);
            Toast.makeText(c, R.string.widgets_range_reset_done, Toast.LENGTH_SHORT).show();
            WidgetUpdateService.sync(c);
            OverlayWidgetService.requestUpdate(c);
        });
    }

    private void refreshRangeInfo() {
        Context app = requireContext().getApplicationContext();
        View view = getView();
        TextView info = rangeSettings.findViewById(R.id.widgetRangeInfo);
        int refresh = ++rangeRefresh;
        new Thread(() -> {
            float estimate = RangeModel.estimateKm(app);
            float learned = RangeModel.learnedKm(app);
            scaleHandler.post(() -> {
                if (getView() == view && refresh == rangeRefresh) showRangeInfo(info, estimate, learned);
            });
        }, "ex2-widget-range").start();
    }

    private void showRangeInfo(TextView info, float estimate, float learned) {
        String text = Float.isNaN(estimate)
                ? getString(R.string.widgets_range_info, String.format(Locale.US, "%.0f", learned))
                : getString(R.string.widgets_range_learned, String.format(Locale.US, "%.0f", estimate));
        info.setText(text);
        info.setVisibility(View.VISIBLE);
    }

    private void updateModeUi(boolean refreshStandard) {
        if (!isAdded() || modeGroup == null) return;
        Context c = requireContext();
        int mode = WidgetModes.current(c);
        binding = true;
        modeGroup.check(mode == Prefs.WIDGET_MODE_STOCK
                ? R.id.widgetModeStock : R.id.widgetModeThird);
        binding = false;

        boolean stock = mode == Prefs.WIDGET_MODE_STOCK;
        boolean modeChanged = showingOverlay == null || showingOverlay != stock;
        showingOverlay = stock;
        overlayPanel.setVisibility(stock ? View.VISIBLE : View.GONE);
        standardPanel.setVisibility(stock ? View.GONE : View.VISIBLE);
        if (modeChanged) {
            resetOverlayRows();
            standardList.removeAllViews();
        }
        appsOverlayCard.refresh();
        updateAppsUi();
        if (stock) {
            ensureOverlayRows(c);
            for (OverlayCard card : overlayCards) card.refresh();
        } else if (modeChanged || refreshStandard) {
            renderStandardWidgets(c);
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
        if (!Prefs.overlayEnabled(c, WidgetsCommon.SIZE_APPS)) return;
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

    @Override
    public void onResume() {
        super.onResume();
        View view = requireView();
        updateAll();
        view.postDelayed(() -> {
            if (getView() != view) return;
            OverlayWidgetService.sync(requireContext());
            updateModeUi(false);
        }, 100);
    }

    @Override
    public void onDestroyView() {
        resetOverlayRows();
        standardList.removeAllViews();
        individualIconLabels.clear();
        appsOverlayCard = null;
        appsCount = null;
        appsSelect = null;
        showingOverlay = null;
        rangeRefresh++;
        super.onDestroyView();
    }
}
