package com.ex2tools.iv.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;

import androidx.core.graphics.PathParser;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

/** Shared icon artwork and state for launcher and overlay climate widgets. */
final class ClimateWidgetIcons {
    static final int ACTION_ICON_DP = 38;
    private static final int VIEWPORT = 96;
    private static final int MAX_BITMAP_SIZE = 144;
    private static final float FIGMA_TO_VIEWPORT = VIEWPORT / 44f;
    private static final int SEAT_GAP_LEFT = 36;
    private static final int SEAT_GAP_RIGHT = 50;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(64);

    private static final int[] COOL_GRADIENTS = {R.drawable.bg_wg_climate_air,
            R.drawable.bg_wg_climate_air_medium, R.drawable.bg_wg_climate_air_high};
    private static final int[] WARM_GRADIENTS = {R.drawable.bg_wg_climate_warm_air,
            R.drawable.bg_wg_climate_warm_air_medium, R.drawable.bg_wg_climate_warm_air_high};
    private static final int[] COOL_ACCENTS = {R.drawable.bg_wg_glass_accent_air,
            R.drawable.bg_wg_glass_accent_air_medium, R.drawable.bg_wg_glass_accent_air_high};
    private static final int[] WARM_ACCENTS = {R.drawable.bg_wg_glass_accent_warm_air,
            R.drawable.bg_wg_glass_accent_warm_air_medium, R.drawable.bg_wg_glass_accent_warm_air_high};

    private enum Shape {
        SEAT(R.array.wg_chrome_seat),
        WHEEL(R.array.wg_chrome_wheel),
        FRONT_AIR(R.array.wg_chrome_front_air),
        REAR(R.array.wg_chrome_rear),
        FRONT_ELECTRIC(R.array.wg_chrome_front_heat),
        FACE(R.array.wg_chrome_face),
        FLOOR(R.array.wg_chrome_floor),
        FACE_FLOOR(R.array.wg_chrome_face_floor),
        WIPERS(R.array.wg_icon_wipers),
        POWER_OFF(R.array.wg_icon_power_off),
        REGEN(R.array.wg_icon_regen),
        FLOOR_GLASS(R.array.wg_chrome_floor_glass);

        final int pathResource;

        Shape(int pathResource) {
            this.pathResource = pathResource;
        }
    }

    private enum SeatMark {
        DRIVER("M18.5 39.1V33.7H20.2Q21.6 33.7 21.6 35Q21.6 36.3 20.2 36.3H18.5"
                + "M20.2 36.3Q22 36.3 22 37.7Q22 39.1 20.2 39.1H18.5"),
        PASSENGER("M18.4 39.1V33.7H22V39.1");

        final Path path;

        SeatMark(String pathData) {
            path = PathParser.createPathFromPathData(pathData);
        }
    }

    private interface Target {
        void set(int id, Bitmap bitmap);
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
    private final float iconScale;
    private final boolean showLabels;
    private Float airflowDelta;
    private Integer airflowGradient;

    private ClimateWidgetIcons(Context c, float iconScale, boolean showLabels, Target target) {
        context = c;
        this.target = target;
        resources = c.getResources();
        style = Prefs.climateIconStyle(c);
        this.iconScale = iconScale;
        this.showLabels = showLabels;
        accentBoost = Prefs.widgetAccentBoost(c);
        night = (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        ink = c.getColor(R.color.wg_text);
        neutral = style.isFlyme() ? (night ? 0xFFFFFFFF : 0xFF000000)
                : c.getColor(R.color.wg_climate_neutral);
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

    private int airflowBackground() {
        if (airflowGradient == null) {
            Integer fan = WidgetsCommon.readFan();
            float fanFraction = fan == null ? 0f : Math.max(0, Math.min(7, fan)) / 7f;
            float temperatureFraction = Math.min(1f, Math.abs(airflowDelta()) / 8f);
            float strength = .60f * fanFraction + .65f * temperatureFraction;
            int level = strength >= .74f ? 2 : strength >= .34f ? 1 : 0;
            int[] gradients = showsWarmAirflow()
                    ? (accentBoost ? WARM_ACCENTS : WARM_GRADIENTS)
                    : (accentBoost ? COOL_ACCENTS : COOL_GRADIENTS);
            airflowGradient = gradients[level];
        }
        return airflowGradient;
    }

    int ventModeBackground(boolean transparent, boolean active) {
        if (style.isFlyme()) return buttonBackground(false, active, false, true);
        return usesOutline() && !transparent && active
                ? WidgetAppearance.buttonBackground(context, airflowBackground())
                : android.R.color.transparent;
    }

    static ClimateWidgetIcons forRemote(Context widgetContext, Context theme, RemoteViews views) {
        return new ClimateWidgetIcons(theme, WidgetIconSizes.appWidgetIconScale(widgetContext),
                WidgetIconSizes.appWidgetLabels(widgetContext), views::setImageViewBitmap);
    }

    static ClimateWidgetIcons forOverlay(Context c, View root, String key) {
        int pct = Prefs.glassWidgetIconSizePct(c);
        return new ClimateWidgetIcons(c, pct / 100f,
                Prefs.climateIconLabels(c, key), (id, bitmap) -> {
            ImageView image = root.findViewById(id);
            if (image != null) image.setImageBitmap(bitmap);
        });
    }

    boolean showsLabels() {
        return showLabels;
    }

    void ventButtons(int mode, boolean directions) {
        vent(R.id.img_vent_face, 1, directions ? (mode & 1) != 0 : mode == 1);
        vent(R.id.img_vent_floor, 2, directions ? (mode & 2) != 0 : mode == 2);
        vent(R.id.img_vent_windshield, 4, directions ? (mode & 4) != 0 : mode == 4);
        if (!directions) {
            vent(R.id.img_vent_face_floor, 3, mode == 3);
            vent(R.id.img_vent_floor_windshield, 6, mode == 6);
        }
        recirculation(R.id.img_recirc);
    }

    void outsideTemperature() {
        if (usesOutline()) {
            chromeGlyph(R.id.img_outside_temp, R.drawable.wg_ic_temp, ACTION_ICON_DP);
        } else {
            set(R.id.img_outside_temp, R.drawable.wg_ic_temp, false, null, 0);
        }
    }

    void recirculation(int id) {
        if (usesOutline()) {
            chromeGlyph(id, R.drawable.wg_ic_recirc, id == R.id.img_recirc_v ? 22 : ACTION_ICON_DP);
        } else {
            set(id, R.drawable.wg_ic_recirc, false, null, 0);
        }
    }

    void driveAndRegen() {
        chromeGlyph(R.id.img_drive_eco, R.drawable.wg_ic_drive_eco, ACTION_ICON_DP);
        chromeGlyph(R.id.img_drive_comfort, R.drawable.wg_ic_drive_comfort, ACTION_ICON_DP);
        chromeGlyph(R.id.img_drive_sport, R.drawable.wg_ic_drive_sport, ACTION_ICON_DP);
        regen(R.id.img_regen_low, 1);
        regen(R.id.img_regen_mid, 2);
        regen(R.id.img_regen_high, 3);
    }

    void regen(int id, int level) {
        target.set(id, bitmap(Shape.REGEN, level, pixels(ACTION_ICON_DP), null, false));
    }

    void chromeGlyph(int id, int resource, int baseDp) {
        target.set(id, drawableBitmap(resource, false, pixels(baseDp), false, true));
    }

    private int pixels(int baseDp) {
        return Math.max(1, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                baseDp * iconScale, resources.getDisplayMetrics())));
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

    void heating(Boolean wheel, Boolean rear, Boolean windshield,
                 Integer driver, Integer passenger) {
        wheelHeat(wheel);
        set(R.id.img_front_rear_defrost, night ? R.drawable.wg_ic_rear_heat_dark
                : R.drawable.wg_ic_rear_heat_light, false, Shape.REAR, on(rear) ? 1 : 0);
        windshieldHeat(windshield);
        set(R.id.img_seat_heat_driver, R.drawable.wg_ic_seat, true, Shape.SEAT, seatLevel(driver));
        set(R.id.img_seat_heat_passenger, R.drawable.wg_ic_seat, true, Shape.SEAT, seatLevel(passenger));
    }

    void wheelHeat(Boolean wheel) {
        set(R.id.img_wheel_heat, night ? R.drawable.wg_ic_wheel_heat_dark
                : R.drawable.wg_ic_wheel_heat_light, false, Shape.WHEEL, on(wheel) ? 3 : 0);
    }

    void windshieldHeat(Boolean windshield) {
        set(R.id.img_windshield_heat, night ? R.drawable.wg_ic_windshield_heat_dark
                : R.drawable.wg_ic_windshield_heat_light, false, Shape.FRONT_ELECTRIC, on(windshield) ? 1 : 0);
    }

    void frontMaxDefrost(boolean active) {
        set(R.id.img_front_max_defrost, R.drawable.wg_ic_heat_windshield, true,
                Shape.FRONT_AIR, active ? 1 : 0);
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
        if (style.isFlyme()) {
            if (active) return airflow
                    ? WidgetAppearance.flymeSurface(context, R.drawable.bg_wg_outline_air, R.drawable.bg_wg_flyme_air)
                    : WidgetAppearance.flymeSurface(context, R.drawable.bg_wg_outline_heat, R.drawable.bg_wg_flyme_heat);
            return pending ? WidgetAppearance.flymeSurface(context, R.drawable.bg_wg_outline_pending, R.drawable.bg_wg_flyme_pending)
                    : WidgetAppearance.flymeSurface(context, R.drawable.bg_wg_outline_edge, R.drawable.bg_wg_flyme_edge);
        }
        int surface = buttonSurface(transparent, active, pending, airflow);
        // Keep the pending indicator visible when there is no caption to show it.
        return pending && !active && !showLabels
                ? surface : WidgetAppearance.buttonBackground(context, surface);
    }

    int standaloneButtonBackground(int chip, boolean active, boolean pending) {
        if (style.isFlyme()) {
            if (style == Prefs.ClimateIconStyle.FLYME_FILLED && !active && !pending) return 0;
            return buttonBackground(false, active, pending, chip == R.id.chip_recirc);
        }
        return active || pending ? buttonBackground(false, active, pending, false) : 0;
    }

    private int buttonSurface(boolean transparent, boolean active, boolean pending, boolean airflow) {
        if (!usesOutline()) {
            if (active) return airflow
                    ? (accentBoost ? R.drawable.bg_wg_chip_on_boost : R.drawable.bg_wg_chip_on)
                    : (accentBoost ? R.drawable.bg_wg_chip_on_orange_boost
                            : R.drawable.bg_wg_chip_on_orange);
            return pending ? R.drawable.bg_wg_chip_pending : R.drawable.bg_wg_chip;
        }
        if (pending && !active) return R.drawable.bg_wg_climate_pending;
        if (transparent) return android.R.color.transparent;
        if (!active) return R.drawable.bg_wg_climate_glass;
        if (!airflow) return accentBoost
                ? R.drawable.bg_wg_glass_accent_heat : R.drawable.bg_wg_climate_heat;
        return airflowBackground();
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
            case FLYME_FILLED, OUTLINE, THIN -> new TypefaceSpan("sans-serif");
            case CHROME -> new TypefaceSpan("sans-serif-medium");
        };
        caption.setSpan(font, 0, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return caption;
    }

    int buttonTextColor(boolean active, boolean airflow) {
        if (style == Prefs.ClimateIconStyle.FLYME_FILLED) return neutral;
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

    void vehicleControl(boolean powerOff) {
        if (powerOff) {
            set(R.id.img_power_off, R.drawable.wg_ic_power_off, true, Shape.POWER_OFF, 0);
        } else {
            set(R.id.img_wipers, R.drawable.wg_ic_wipers, true, Shape.WIPERS, 0);
        }
    }

    private void set(int id, int resource, boolean tinted, Shape shape, int level) {
        int baseSize = ACTION_ICON_DP;
        if (id == R.id.img_vent_mode && !usesOutline()) baseSize = 64;
        else if (id == R.id.img_recirc_v) baseSize = 22;
        else if (style == Prefs.ClimateIconStyle.STANDARD && !showLabels) baseSize = 46;
        int size = pixels(baseSize);
        boolean mirrored = id == R.id.img_seat_heat_passenger;
        SeatMark mark = null;
        if (shape == Shape.SEAT && !showLabels) {
            mark = mirrored ? SeatMark.PASSENGER : SeatMark.DRIVER;
        }
        Bitmap icon = (usesOutline() && shape != null) || mark != null
                ? bitmap(shape, level, size, mark, mirrored)
                : drawableBitmap(resource, tinted, size, mirrored, false);
        target.set(id, icon);
    }

    private Bitmap drawableBitmap(int resource, boolean tinted, int size, boolean mirrored,
                                  boolean chrome) {
        String key = "drawable:" + resource + ':' + size + ':' + night + ':' + (tinted ? ink : 0)
                + ':' + (chrome ? style : "")
                + ':' + resources.getDisplayMetrics().densityDpi + ':' + mirrored;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        Drawable drawable = context.getDrawable(resource).mutate();
        if (tinted) drawable.setTint(ink);
        Bitmap icon = newIcon(size);
        float scale = icon.getWidth()
                / (float) Math.max(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        int width = Math.round(drawable.getIntrinsicWidth() * scale);
        int height = Math.round(drawable.getIntrinsicHeight() * scale);
        int left = (icon.getWidth() - width) / 2;
        int top = (icon.getHeight() - height) / 2;
        drawable.setBounds(left, top, left + width, top + height);
        Canvas canvas = new Canvas(icon);
        if (mirrored) mirrorHorizontally(canvas, icon.getWidth());
        drawable.draw(canvas);
        if (chrome) {
            Paint metal = new Paint(Paint.ANTI_ALIAS_FLAG);
            metal.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            if (style == Prefs.ClimateIconStyle.THIN || style.isFlyme()) metal.setColor(neutral);
            else metal.setShader(WidgetAppearance.chromeGradient(night, top, top + height));
            canvas.drawRect(0, 0, icon.getWidth(), icon.getHeight(), metal);
        }
        CACHE.put(key, icon);
        return icon;
    }

    // Keep the requested intrinsic size while limiting the total bitmap payload sent to the host.
    private Bitmap newIcon(int size) {
        int pixels = Math.min(size, MAX_BITMAP_SIZE);
        Bitmap icon = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
        int density = resources.getDisplayMetrics().densityDpi;
        icon.setDensity(Math.max(1, Math.round(density * (pixels / (float) size))));
        return icon;
    }

    private static void mirrorHorizontally(Canvas canvas, int width) {
        canvas.translate(width, 0);
        canvas.scale(-1, 1);
    }

    private Bitmap bitmap(Shape shape, int level, int size, SeatMark mark, boolean mirrored) {
        int accent = level == 0 ? neutral : switch (shape) {
            case FRONT_AIR, FACE, FLOOR, FACE_FLOOR, FLOOR_GLASS -> airflowColor();
            case REGEN -> blue;
            default -> amber;
        };
        String key = shape.name() + ':' + style + ':' + level + ':' + night + ':' + accent + ':' + size
                + ':' + resources.getDisplayMetrics().densityDpi + ':' + mark + ':' + mirrored;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        String[] data = resources.getStringArray(shape.pathResource);
        Bitmap icon = newIcon(size);
        Canvas canvas = new Canvas(icon);
        float scale = icon.getWidth() / (float) VIEWPORT;
        canvas.scale(scale, scale);
        canvas.save();
        if (mirrored) mirrorHorizontally(canvas, VIEWPORT);
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
                    : layer > 0 && (multiLevelHeat || shape == Shape.REGEN) ? inactiveHeat : neutral);
            if (style.isFlyme()) {
                paint.setColor(neutral);
                if (layer > 0 && !active && (multiLevelHeat || shape == Shape.REGEN)) paint.setAlpha(85);
            }
            float width;
            if (extraThin) width = 0.25f;
            else if (thinChrome) width = 0.75f;
            else if (waves) width = thick ? 1.2f : 1.06f;
            else if (active) width = thick ? 1.89f : 1.25f;
            else if (multiLevelHeat && layer > 0) width = thick ? 1.02f : 0.56f;
            else width = thick ? 1.64f : 1f;
            if (mark != null && thinChrome && layer > 0) width = 0.55f;
            paint.setStrokeWidth(width * FIGMA_TO_VIEWPORT);

            boolean seatGap = mark != null && layer == 0;
            if (!style.isFlyme() && (thick || thinChrome || extraThin || seatGap) && layer == 0) {
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                paint.setShader(WidgetAppearance.chromeGradient(night, bounds.top, bounds.bottom));
            }
            if (seatGap) {
                canvas.save();
                canvas.clipOutRect(SEAT_GAP_LEFT, 73, SEAT_GAP_RIGHT, VIEWPORT);
            }
            if (!waves && shape != Shape.WIPERS && shape != Shape.POWER_OFF
                    && !(shape == Shape.REGEN && layer == 0)) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
            if (seatGap) canvas.restore();
        }
        canvas.restore();
        if (mark != null) {
            if (mirrored) {
                canvas.translate(VIEWPORT - SEAT_GAP_LEFT - SEAT_GAP_RIGHT, 0);
            }
            canvas.scale(FIGMA_TO_VIEWPORT, FIGMA_TO_VIEWPORT);
            canvas.translate(-0.55f, 0);
            paint.setShader(null);
            paint.setColor(night ? 0xFFF2F6FF : 0xFF102038);
            paint.setStrokeWidth(thick ? 1.64f * 0.56f : 0.65f);
            canvas.drawPath(mark.path, paint);
        }

        CACHE.put(key, icon);
        return icon;
    }
}
