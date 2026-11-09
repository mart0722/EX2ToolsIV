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
import com.ex2tools.iv.widgets.WidgetUpdateService;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WidgetsFragment extends Fragment {

    private static final String[] SIZE_KEYS = {
            WidgetsCommon.SIZE_CLOCK,
            WidgetsCommon.SIZE_DRIVE_REGEN,
            WidgetsCommon.SIZE_DRIVE_MODE,
            WidgetsCommon.SIZE_REGEN,
            WidgetsCommon.SIZE_FAN,
            WidgetsCommon.SIZE_TEMP,
            WidgetsCommon.SIZE_VENT,
            WidgetsCommon.SIZE_VENT_FAN,
            WidgetsCommon.SIZE_HEATING
    };

    private static final int[] SIZE_LABELS = {
            R.string.widgets_size_clock,
            R.string.widgets_size_drive_regen,
            R.string.widgets_size_drive_mode,
            R.string.widgets_size_regen,
            R.string.widgets_size_fan,
            R.string.widgets_size_temp,
            R.string.widgets_size_vent,
            R.string.widgets_size_vent_fan,
            R.string.widgets_size_heating
    };

    private LinearLayout sizeList;
    private final Map<String, SeekBar> sizeSeeks = new LinkedHashMap<>();
    private final Map<String, TextView> sizeValues = new LinkedHashMap<>();
    private SeekBar opacity;
    private TextView opacityValue;
    private View opacityPreview;
    private FlymeSegmentedGroup fontGroup;
    private FlymeSegmentedGroup colorGroup;
    private FlymeSwitchRow date;
    private TextClock clockPreview;
    private TextView clockDatePreview;
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
        sizeList = view.findViewById(R.id.widgetSizeList);
        opacity = view.findViewById(R.id.widgetOpacitySeek);
        opacityValue = view.findViewById(R.id.widgetOpacityValue);
        opacityPreview = view.findViewById(R.id.widgetOpacityPreview);
        fontGroup = view.findViewById(R.id.clockFontGroup);
        colorGroup = view.findViewById(R.id.clockColorGroup);
        date = view.findViewById(R.id.clockDate);
        clockPreview = view.findViewById(R.id.clockPreview);
        clockDatePreview = view.findViewById(R.id.clockDatePreview);

        buildSizeRows(c);

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

        updateAll();
    }

    private void buildSizeRows(Context c) {
        if (sizeList == null) return;
        sizeList.removeAllViews();
        sizeSeeks.clear();
        sizeValues.clear();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = 0; i < SIZE_KEYS.length; i++) {
            final String key = SIZE_KEYS[i];
            View row = inflater.inflate(R.layout.item_widget_size, sizeList, false);
            TextView name = row.findViewById(R.id.sizeName);
            TextView value = row.findViewById(R.id.sizeValue);
            SeekBar seek = row.findViewById(R.id.sizeSeek);
            int pct = Prefs.widgetSizePct(c, key);
            name.setText(SIZE_LABELS[i]);
            value.setText(getString(R.string.widgets_percent_fmt, pct));
            seek.setProgress(pct - 50);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int p = progress + 50;
                    value.setText(getString(R.string.widgets_percent_fmt, p));
                    if (fromUser) {
                        Prefs.setWidgetSizePct(requireContext(), key, p);
                        WidgetUpdateService.refreshOpacity(requireContext());
                        if (WidgetsCommon.SIZE_CLOCK.equals(key)) {
                            updatePreviewScale();
                            ClockWidgetProvider.refreshAll(requireContext());
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
            sizeSeeks.put(key, seek);
            sizeValues.put(key, value);
            sizeList.addView(row);
        }
    }

    private void updateAll() {
        Context c = requireContext();
        for (String key : SIZE_KEYS) {
            int pct = Prefs.widgetSizePct(c, key);
            SeekBar seek = sizeSeeks.get(key);
            TextView value = sizeValues.get(key);
            if (seek != null) seek.setProgress(pct - 50);
            if (value != null) value.setText(getString(R.string.widgets_percent_fmt, pct));
        }
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
        binding = false;
        updateClockPreview();
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
        float scale = Prefs.widgetSizePct(requireContext(), WidgetsCommon.SIZE_CLOCK) / 100f;
        clockPreview.setScaleX(scale);
        clockPreview.setScaleY(scale);
        if (clockDatePreview != null) {
            clockDatePreview.setScaleX(scale);
            clockDatePreview.setScaleY(scale);
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
        updateAll();
    }
}
