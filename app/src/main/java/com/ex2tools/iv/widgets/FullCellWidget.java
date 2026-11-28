package com.ex2tools.iv.widgets;

import android.content.Context;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

final class FullCellWidget {
    private static final int[] AIRFLOW = {
            R.id.chip_vent_face, R.id.chip_vent_floor, R.id.chip_vent_face_floor,
            R.id.chip_vent_windshield, R.id.chip_vent_floor_windshield, R.id.chip_recirc
    };
    private static final int[] HEATING = {
            R.id.chip_wheel_heat, R.id.chip_front_rear_defrost, R.id.chip_front_max_defrost,
            R.id.chip_seat_heat_driver, R.id.chip_windshield_heat, R.id.chip_seat_heat_passenger
    };

    private FullCellWidget() {}

    static boolean showMaxAir(Context c) {
        return Prefs.overlayCells(c, WidgetsCommon.SIZE_HEATING) == Prefs.OverlayCells.DEFAULT
                ? Prefs.showHeatingMaxAir(c) : !Prefs.heatingHideMaxAir(c);
    }

    static Size size(Context c, String key, Prefs.OverlayCells format) {
        int count;
        if (WidgetsCommon.SIZE_HEATING.equals(key)) {
            count = showMaxAir(c) ? 6 : 5;
        } else {
            count = Prefs.ventDirectionButtons(c) ? 3 : 5;
            if (Prefs.showVentRecirc(c)) count++;
        }
        return format == Prefs.OverlayCells.COLUMN ? WidgetGrid.cells(1, count) : WidgetGrid.cells(count, 1);
    }

    static void arrange(FrameLayout root, String key, Prefs.OverlayCells format) {
        Context c = root.getContext();
        boolean column = format == Prefs.OverlayCells.COLUMN;
        LinearLayout strip = new LinearLayout(c);
        strip.setOrientation(column ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        strip.setBaselineAligned(false);
        float density = c.getResources().getDisplayMetrics().density;
        int gap = Math.round(5 * density);
        int iconSize = Math.round(44 * density * Prefs.glassWidgetIconSizePct(c) / 100f);
        int[] ids = WidgetsCommon.SIZE_HEATING.equals(key) ? HEATING : AIRFLOW;
        for (int id : ids) {
            LinearLayout chip = root.findViewById(id);
            ((ViewGroup) chip.getParent()).removeView(chip);
            chip.setPadding(0, 0, 0, 0);
            ImageView icon = (ImageView) chip.getChildAt(0);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                    column ? MATCH_PARENT : 0, column ? 0 : MATCH_PARENT, 1);
            cell.setMargins(gap, gap, gap, gap);
            strip.addView(chip, cell);
        }
        View background = root.findViewById(R.id.widget_bg_layer);
        root.removeAllViews();
        root.addView(background);
        root.addView(strip, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }
}
