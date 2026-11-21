package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;

import androidx.core.graphics.PathParser;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/** Shared icon artwork and state for launcher and overlay climate widgets. */
final class ClimateWidgetIcons {
    private static final int VIEWPORT = 96;
    private static final int BITMAP_SIZE = 144;
    private static final float FIGMA_TO_VIEWPORT = VIEWPORT / 44f;
    private static final int[] STEEL = {0xFF24384C, 0xFF60788F, 0xFF324A60, 0xFF30495F};
    private static final int[] TITANIUM = {0xFFA6BED5, 0xFFF7FAFF, 0xFF8AA4C0, 0xFFCAD8E9};
    private static final float[] STEEL_STOPS = {0f, .27f, .47f, 1f};
    private static final float[] TITANIUM_STOPS = {0f, .27f, .52f, 1f};
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(64);

    private enum Shape {
        SEAT(R.array.wg_chrome_seat),
        WHEEL(R.array.wg_chrome_wheel),
        FRONT_AIR(R.array.wg_chrome_front_air),
        REAR(R.array.wg_chrome_rear),
        FRONT_ELECTRIC(R.array.wg_chrome_front_heat),
        FACE(R.array.wg_chrome_face),
        FLOOR(R.array.wg_chrome_floor),
        FACE_FLOOR(R.array.wg_chrome_face_floor),
        FLOOR_GLASS(R.array.wg_chrome_floor_glass);

        final int pathResource;

        Shape(int pathResource) {
            this.pathResource = pathResource;
        }
    }

    private interface Target {
        void set(int id, int legacyResource, int legacyTint, Bitmap outline);
    }

    private final Context context;
    private final Target target;
    private final Resources resources;
    private final Prefs.ClimateIconStyle style;
    private final boolean night;
    private final int ink;
    private final int neutral;
    private final int amber;
    private final int blue;
    private final int coral;
    private final int inactiveHeat;
    private final boolean accentBoost;
    private Float airflowDelta;
    private Integer airflowAccentBackground;

    private ClimateWidgetIcons(Context c, Target target) {
        context = c;
        this.target = target;
        resources = c.getResources();
        style = Prefs.climateIconStyle(c);
        accentBoost = usesOutline() && Prefs.widgetAccentBoost(c);
        night = (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        ink = c.getColor(R.color.wg_text);
        neutral = c.getColor(R.color.wg_climate_neutral);
        amber = c.getColor(R.color.wg_climate_amber);
        blue = c.getColor(R.color.wg_climate_blue);
        coral = c.getColor(R.color.wg_climate_coral);
        inactiveHeat = c.getColor(R.color.wg_climate_inactive);
    }

    private float airflowDelta() {
        if (airflowDelta == null) airflowDelta = WidgetsCommon.airflowSetpointDelta(context);
        return airflowDelta;
    }

    private boolean showsWarmAirflow() {
        return airflowDelta() > 0f;
    }

    private int boostedAirflowBackground() {
        if (airflowAccentBackground == null) {
            Integer fan = WidgetsCommon.readFan();
            float fanFraction = fan == null ? 0f : Math.max(0, Math.min(7, fan)) / 7f;
            float temperatureFraction = Math.min(1f, Math.abs(airflowDelta()) / 8f);
            float strength = .60f * fanFraction + .65f * temperatureFraction;
            boolean warm = showsWarmAirflow();
            if (strength >= .74f) {
                airflowAccentBackground = warm ? R.drawable.bg_wg_glass_accent_warm_air_high
                        : R.drawable.bg_wg_glass_accent_air_high;
            } else if (strength >= .34f) {
                airflowAccentBackground = warm ? R.drawable.bg_wg_glass_accent_warm_air_medium
                        : R.drawable.bg_wg_glass_accent_air_medium;
            } else {
                airflowAccentBackground = warm ? R.drawable.bg_wg_glass_accent_warm_air
                        : R.drawable.bg_wg_glass_accent_air;
            }
        }
        return airflowAccentBackground;
    }

    int ventModeBackground(boolean transparent, boolean active) {
        return accentBoost && !transparent && active
                ? boostedAirflowBackground() : android.R.color.transparent;
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
        return usesOutline() ? R.string.wg_heat_front_max_outline : R.string.wg_heat_front_max;
    }

    int electricWindshieldLabel() {
        return usesOutline() ? R.string.wg_heat_windshield_outline : R.string.wg_heat_windshield;
    }

    boolean usesOutline() {
        return style != Prefs.ClimateIconStyle.STANDARD;
    }

    int buttonBackground(boolean transparent, boolean active, boolean pending, boolean airflow) {
        if (!usesOutline()) {
            if (active) return airflow ? R.drawable.bg_wg_chip_on : R.drawable.bg_wg_chip_on_orange;
            return pending ? R.drawable.bg_wg_chip_pending : R.drawable.bg_wg_chip;
        }
        if (pending && !active) return R.drawable.bg_wg_climate_pending;
        if (transparent) return android.R.color.transparent;
        if (!active) return R.drawable.bg_wg_climate_glass;
        if (!airflow) return accentBoost
                ? R.drawable.bg_wg_glass_accent_heat : R.drawable.bg_wg_climate_heat;
        if (accentBoost) return boostedAirflowBackground();
        return showsWarmAirflow() ? R.drawable.bg_wg_climate_warm_air : R.drawable.bg_wg_climate_air;
    }

    CharSequence ventCaption(int textId) {
        int label = R.string.wg_recirc;
        if (textId == R.id.txt_vent_face) label = R.string.wg_vent_face;
        else if (textId == R.id.txt_vent_floor) label = R.string.wg_vent_floor;
        else if (textId == R.id.txt_vent_face_floor) label = R.string.wg_vent_face_floor;
        else if (textId == R.id.txt_vent_windshield) label = R.string.wg_vent_windshield;
        else if (textId == R.id.txt_vent_floor_windshield) label = R.string.wg_vent_floor_windshield;
        return caption(context.getString(label));
    }

    CharSequence ventModeCaption(int mode) {
        String text = context.getString(WidgetsCommon.ventLabel(mode));
        return usesOutline() ? caption(text) : text;
    }

    CharSequence caption(String text) {
        SpannableString caption = new SpannableString(text);
        // Framework spans survive RemoteViews parceling to the launcher.
        Object font = switch (style) {
            case STANDARD -> new StyleSpan(Typeface.BOLD);
            case EXTRA_THIN, CHROME_THIN -> new TypefaceSpan("sans-serif-light");
            case THIN -> new TypefaceSpan("sans-serif");
            case CHROME -> new TypefaceSpan("sans-serif-medium");
        };
        caption.setSpan(font, 0, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return caption;
    }

    int buttonTextColor(boolean active, boolean airflow) {
        if (!active || !usesOutline()) return ink;
        return airflow ? airflowColor() : amber;
    }

    private int airflowColor() {
        return showsWarmAirflow() ? coral : blue;
    }

    private static boolean on(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static int seatLevel(Integer level) {
        return level == null ? 0 : Math.max(0, Math.min(3, level));
    }

    private void set(int id, int resource, boolean tinted, Shape shape, int level) {
        target.set(id, resource, tinted ? ink : Color.TRANSPARENT,
                usesOutline() ? bitmap(shape, level) : null);
    }

    private Bitmap bitmap(Shape shape, int level) {
        int accent = level == 0 ? neutral : switch (shape) {
            case FRONT_AIR, FACE, FLOOR, FACE_FLOOR, FLOOR_GLASS -> airflowColor();
            default -> amber;
        };
        String key = shape.name() + ':' + style + ':' + level + ':' + night + ':' + accent;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        String[] data = resources.getStringArray(shape.pathResource);
        Bitmap icon = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888);
        icon.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(icon);
        canvas.scale(BITMAP_SIZE / (float) VIEWPORT, BITMAP_SIZE / (float) VIEWPORT);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        boolean thick = style == Prefs.ClimateIconStyle.CHROME;
        boolean thinChrome = style == Prefs.ClimateIconStyle.CHROME_THIN;
        boolean extraThin = style == Prefs.ClimateIconStyle.EXTRA_THIN;
        boolean multiLevelHeat = shape == Shape.SEAT || shape == Shape.WHEEL;
        for (int layer = 0; layer < data.length; layer++) {
            Path path = PathParser.createPathFromPathData(data[layer]);
            path.setFillType(Path.FillType.EVEN_ODD);
            boolean waves = shape == Shape.FLOOR_GLASS && layer == 2;
            boolean active = layer > 0 && layer <= level;
            paint.setShader(null);
            paint.setColor(active || (waves && level > 0) ? accent
                    : multiLevelHeat && layer > 0 ? inactiveHeat : neutral);
            float width;
            if (extraThin) width = 0.25f;
            else if (thinChrome) width = 0.75f;
            else if (waves) width = thick ? 1.2f : 1.06f;
            else if (active) width = thick ? 1.89f : 1.25f;
            else if (multiLevelHeat && layer > 0) width = thick ? 1.02f : 0.56f;
            else width = thick ? 1.64f : 1f;
            paint.setStrokeWidth(width * FIGMA_TO_VIEWPORT);

            if ((thick || thinChrome || extraThin) && layer == 0) {
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                        night ? TITANIUM : STEEL, night ? TITANIUM_STOPS : STEEL_STOPS,
                        Shader.TileMode.CLAMP));
            }
            if (!waves) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }

        CACHE.put(key, icon);
        return icon;
    }
}
