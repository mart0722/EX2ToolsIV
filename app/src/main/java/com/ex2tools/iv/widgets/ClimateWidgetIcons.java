package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/** Shared icon artwork and state for launcher and overlay climate widgets. */
final class ClimateWidgetIcons {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(64);

    private enum Shape {
        SEAT(R.drawable.wg_ref_climate_seat),
        WHEEL(R.drawable.wg_ref_climate_wheel),
        FRONT_AIR(R.drawable.wg_ref_climate_front_air),
        REAR(R.drawable.wg_ref_climate_rear),
        FRONT_ELECTRIC(R.drawable.wg_ref_climate_front_heat),
        FACE(R.drawable.wg_ref_climate_face),
        FLOOR(R.drawable.wg_ref_climate_floor),
        FACE_FLOOR(R.drawable.wg_ref_climate_face_floor),
        FLOOR_GLASS(R.drawable.wg_ref_climate_floor_glass);

        final int maskResource;

        Shape(int maskResource) {
            this.maskResource = maskResource;
        }
    }

    private interface Target {
        void set(int id, int legacyResource, int legacyTint, Bitmap outline);
    }

    private final Target target;
    private final Resources resources;
    private final boolean outline;
    private final boolean night;
    private final int ink;
    private final int amber;

    private ClimateWidgetIcons(Context c, Target target) {
        this.target = target;
        resources = c.getResources();
        outline = Prefs.climateOutlineIcons(c);
        night = (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        ink = c.getColor(R.color.wg_text);
        amber = c.getColor(R.color.wg_climate_amber);
    }

    static ClimateWidgetIcons forRemote(Context c, RemoteViews views) {
        return new ClimateWidgetIcons(c, (id, resource, tint, bitmap) -> {
            if (bitmap != null) views.setImageViewBitmap(id, bitmap);
            else views.setImageViewResource(id, resource);
            // ImageView's transparent SRC_ATOP filter lets the two-color artwork
            // show through the original layout tint, including on a reused view.
            views.setInt(id, "setColorFilter", bitmap != null ? Color.TRANSPARENT : tint);
        });
    }

    static ClimateWidgetIcons forOverlay(Context c, View root) {
        return new ClimateWidgetIcons(c, (id, resource, tint, bitmap) -> {
            ImageView image = root.findViewById(id);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                image.setColorFilter(Color.TRANSPARENT);
            } else {
                image.setImageResource(resource);
                image.clearColorFilter();
            }
        });
    }

    void ventButtons(int mode, boolean directions) {
        vent(R.id.img_vent_face, 1, directions ? (mode & 1) != 0 : mode == 1);
        vent(R.id.img_vent_floor, 2, directions ? (mode & 2) != 0 : mode == 2);
        vent(R.id.img_vent_windshield, 4, directions ? (mode & 4) != 0 : mode == 4);
        vent(R.id.img_vent_face_floor, 3, mode == 3);
        vent(R.id.img_vent_floor_windshield, 6, mode == 6);
    }

    void vent(int id, int mode, boolean active) {
        Shape shape;
        switch (mode) {
            case 2: shape = Shape.FLOOR; break;
            case 3: shape = Shape.FACE_FLOOR; break;
            case 4: shape = Shape.FRONT_AIR; break;
            case 6: shape = Shape.FLOOR_GLASS; break;
            default: shape = Shape.FACE; break;
        }
        set(id, WidgetsCommon.ventIcon(mode), true, shape, active ? 1 : 0);
    }

    void heating(Boolean wheel, Boolean rear, Boolean front, Boolean windshield,
                 Integer driver, Integer passenger) {
        set(R.id.img_wheel_heat, night ? R.drawable.wg_ic_wheel_heat_dark
                : R.drawable.wg_ic_wheel_heat_light, false, Shape.WHEEL, on(wheel) ? 3 : 0);
        set(R.id.img_front_rear_defrost, night ? R.drawable.wg_ic_rear_heat_dark
                : R.drawable.wg_ic_rear_heat_light, false, Shape.REAR, on(rear) ? 1 : 0);
        set(R.id.img_windshield_heat, night ? R.drawable.wg_ic_windshield_heat_dark
                : R.drawable.wg_ic_windshield_heat_light, false, Shape.FRONT_ELECTRIC, on(windshield) ? 1 : 0);
        set(R.id.img_front_max_defrost, R.drawable.wg_ic_heat_windshield, true,
                Shape.FRONT_AIR, on(front) ? 1 : 0);
        set(R.id.img_seat_heat_driver, R.drawable.wg_ic_seat, true, Shape.SEAT, seatLevel(driver));
        set(R.id.img_seat_heat_passenger, R.drawable.wg_ic_seat, true, Shape.SEAT, seatLevel(passenger));
    }

    int frontAirLabel() {
        return outline ? R.string.wg_heat_front_max_outline : R.string.wg_heat_front_max;
    }

    int electricWindshieldLabel() {
        return outline ? R.string.wg_heat_windshield_outline : R.string.wg_heat_windshield;
    }

    boolean usesOutline() {
        return outline;
    }

    private static boolean on(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static int seatLevel(Integer level) {
        return level == null ? 0 : Math.max(0, Math.min(3, level));
    }

    private void set(int id, int resource, boolean tinted, Shape shape, int level) {
        target.set(id, resource, tinted ? ink : Color.TRANSPARENT,
                outline ? bitmap(shape, level) : null);
    }

    private Bitmap bitmap(Shape shape, int level) {
        String key = shape.name() + ':' + level + ':' + ink + ':' + amber;
        Bitmap icon = CACHE.get(key);
        if (icon != null) return icon;
        Bitmap masks = BitmapFactory.decodeResource(resources, shape.maskResource);
        int size = masks.getHeight();
        int layers = masks.getWidth() / size;
        icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        icon.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect destination = new Rect(0, 0, size, size);
        Rect source = new Rect();
        // Horizontal mask strip: silhouette first, then one cell per indicator.
        // Seats and wheel have three individual cells; the others have one.
        for (int layer = 0; layer < layers; layer++) {
            boolean active = layer > 0 && layer <= level;
            paint.setColorFilter(new PorterDuffColorFilter(active ? amber : ink, PorterDuff.Mode.SRC_IN));
            paint.setAlpha(layers > 2 && layer > 0 && !active ? 100 : 255);
            source.set(layer * size, 0, (layer + 1) * size, size);
            canvas.drawBitmap(masks, source, destination, paint);
        }
        CACHE.put(key, icon);
        return icon;
    }
}
