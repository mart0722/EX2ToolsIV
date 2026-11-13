package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ex2tools.iv.R;
import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.ChargeStatusProbe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only CarPropertyManager explorer. */
public final class CarApiDialog {

    private static final int MAX_OUTPUT = 200_000;

    private static final List<Prop> FALLBACK = Arrays.asList(
            new Prop(570491136, "DRIVE_MODE"),
            new Prop(537003264, "REGEN_MODE"),
            new Prop(289408001, "GEAR"),
            new Prop(557885165, "SOC"),
            new Prop(289407752, "RANGE"),
            new Prop(557885176, "AVG_CONSUMPTION"),
            new Prop(557875206, "AUX_12V"),
            new Prop(289407492, "ODOMETER_VENDOR"),
            new Prop(291504644, "ODOMETER"),
            new Prop(291504647, "SPEED"),
            new Prop(291504648, "SPEED_DISPLAY"),
            new Prop(624955446, "HVAC_TEMP_ADJUST"),
            new Prop(557885148, "HVAC_TEMP_SET"),
            new Prop(354419971, "HVAC_TEMP_SET_AOSP"),
            new Prop(358614274, "HVAC_TEMP_CURRENT"),
            new Prop(358614275, "HVAC_TEMP_AOSP"),
            new Prop(356517120, "HVAC_FAN_SPEED"),
            new Prop(356517121, "HVAC_FAN_DIRECTION"),
            new Prop(358613145, "HVAC_AC_ON"),
            new Prop(557846566, "HVAC_RECIRC_ON"),
            new Prop(354419988, "HVAC_REAR_DEFROSTER_ON"),
            new Prop(354419975, "HVAC_FRONT_MAX_DEFROST_ON"),
            new Prop(270535680, "HVAC_WINDSHIELD_HEAT"),
            new Prop(289408269, "STEERING_WHEEL_HEAT"),
            new Prop(356517131, "SEAT_HEAT"),
            new Prop(557884279, "AC_AMBIENT_TEMP"),
            new Prop(291505923, "ENV_OUTSIDE_TEMPERATURE"),
            new Prop(658548345, "DRIVE_MILEAGE"),
            new Prop(557884281, "AC_INSIDE_TEMP"),
            new Prop(557884439, "AC_FAN_LEVEL"),
            new Prop(557884965, "AC_POWER"),
            new Prop(559982313, "ED_DRIVING_ENERGY_FLOW"),
            new Prop(559982315, "ED_BATTERY_ENERGY_FLOW"),
            new Prop(559982316, "ED_OTHER_ENERGY_FLOW"),
            new Prop(322964416, "WINDOW_POSITION"),
            new Prop(538247424, "DAY_NIGHT_SETTING"),
            new Prop(554733617, "VIN")
    );

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<Integer, String> ADAPT_FUNCTIONS = new HashMap<>();
    private static volatile Map<Integer, String> adaptNames = new HashMap<>();
    private static volatile boolean adaptAvailable;

    static {
        for (Prop p : FALLBACK) NAMES.put(p.id, p.name);

        ADAPT_FUNCTIONS.put(605028608, "CHARGING_SWITCH");
        ADAPT_FUNCTIONS.put(605225472, "CHARGING_PLUG_STATE");
        ADAPT_FUNCTIONS.put(605225216, "CHARGING_PLUG_TYPE");
        ADAPT_FUNCTIONS.put(606100480, "CHARGING_DISCHARGING_STATE");
        ADAPT_FUNCTIONS.put(605028864, "CHARGING_SOC_LIMIT");
        ADAPT_FUNCTIONS.put(605029888, "CHARGING_CURRENT_LIMIT");
        ADAPT_FUNCTIONS.put(606105856, "DC_CHARGING_POWER");
        ADAPT_FUNCTIONS.put(605291264, "CHARGING_ESTIMATED_TIME");
        ADAPT_FUNCTIONS.put(605291776, "CHARGING_ENERGY");
        ADAPT_FUNCTIONS.put(605291008, "CHARGING_WORK_CURRENT");
        ADAPT_FUNCTIONS.put(605290752, "CHARGING_WORK_VOLTAGE");
        ADAPT_FUNCTIONS.put(605291520, "CHARGING_SPEED");
        ADAPT_FUNCTIONS.put(606098688, "CHARGING_WORK_TIME");
        ADAPT_FUNCTIONS.put(605095936, "PRE_CHARGING_IMMEDIATELY");
        ADAPT_FUNCTIONS.put(605031936, "EXTERNAL_CHARGING_LIGHT");
        ADAPT_FUNCTIONS.put(605159680, "DISCHARGING_V2V");
        ADAPT_FUNCTIONS.put(605159936, "DISCHARGING_V2L");
        ADAPT_FUNCTIONS.put(605160192, "DISCHARGING_SOC");
        ADAPT_FUNCTIONS.put(605356544, "DISCHARGING_WORK_CURRENT");
        ADAPT_FUNCTIONS.put(605356288, "DISCHARGING_WORK_VOLTAGE");
        ADAPT_FUNCTIONS.put(606099200, "BATTERY_DISCHARGING_POWER");
        ADAPT_FUNCTIONS.put(605357056, "DISCHARGING_ENERGY");
        ADAPT_FUNCTIONS.put(605357312, "DISCHARGING_WORK_TOTAL");
        ADAPT_FUNCTIONS.put(606100736, "DISTANCE_PROTECTION");
        ADAPT_FUNCTIONS.put(606099456, "DISTANCE_PROTECTION_SWITCH");
        ADAPT_FUNCTIONS.put(605030912, "MAINTAIN_BATTERY_TEMP");
        ADAPT_FUNCTIONS.put(605031424, "EXTENDED_BATTERY_LIFE");
        ADAPT_FUNCTIONS.put(605031168, "ADJUST_MAX_CURRENT");

        ADAPT_FUNCTIONS.put(269747712, "HVAC_AAC_LEVEL");
        ADAPT_FUNCTIONS.put(269749248, "HVAC_AUTO_CONTROL");
        ADAPT_FUNCTIONS.put(268896000, "HVAC_AUTO_BLOWING_MODE");
        ADAPT_FUNCTIONS.put(268894465, "BLOWING_FACE");
        ADAPT_FUNCTIONS.put(268894466, "BLOWING_LEG");
        ADAPT_FUNCTIONS.put(268894467, "BLOWING_FACE_LEG");
        ADAPT_FUNCTIONS.put(268894468, "BLOWING_FRONT_WINDOW");
        ADAPT_FUNCTIONS.put(268894469, "BLOWING_FACE_FRONT_WINDOW");
        ADAPT_FUNCTIONS.put(268894470, "BLOWING_LEG_FRONT_WINDOW");
        ADAPT_FUNCTIONS.put(268894471, "BLOWING_ALL");
        ADAPT_FUNCTIONS.put(268632321, "CIRCULATION_INNER");
        ADAPT_FUNCTIONS.put(268632322, "CIRCULATION_OUTSIDE");
        ADAPT_FUNCTIONS.put(268632323, "CIRCULATION_AUTO");
        ADAPT_FUNCTIONS.put(269485568, "HVAC_AUTO_CZIS");
        ADAPT_FUNCTIONS.put(268960256, "HVAC_AQS_SWITCH");
        ADAPT_FUNCTIONS.put(270533632, "HVAC_AQS_INDICATION");
        ADAPT_FUNCTIONS.put(268960512, "HVAC_AUTO_DEHUMIDIFICATION");
        ADAPT_FUNCTIONS.put(269288192, "HVAC_AUTO_DRY");
        ADAPT_FUNCTIONS.put(270535168, "HVAC_AUTO_DEFROST");
        ADAPT_FUNCTIONS.put(268698880, "HVAC_AUTO_DEFROST_FRONT");
        ADAPT_FUNCTIONS.put(268698624, "HVAC_AUTO_DEFROST_REAR");
        ADAPT_FUNCTIONS.put(269418752, "HVAC_AUTO_CLOSE_WINDOW_REMIND");
        ADAPT_FUNCTIONS.put(269485312, "HVAC_AUTOMATIC_VENTILATION_DRY");
        ADAPT_FUNCTIONS.put(268961025, "HVAC_AUTO_DEODORIZE_SWITCH");
        ADAPT_FUNCTIONS.put(269747456, "HVAC_AUTO_AAC_SWITCH");
        ADAPT_FUNCTIONS.put(269615872, "HVAC_AUTO_CIRC");
        ADAPT_FUNCTIONS.put(269758976, "HVAC_ANTIFREEZE_REMINDER");
        ADAPT_FUNCTIONS.put(269759744, "HVAC_AC_FORBIDDEN");
        ADAPT_FUNCTIONS.put(269760000, "HVAC_AUTO_ON_KEY");
        ADAPT_FUNCTIONS.put(269767424, "HVAC_FAN_SPEED_ON_STALK");
    }

    private CarApiDialog() {
    }

    public static String buildReport(Context c) {
        return build(c);
    }

    public static void show(Context c) {
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_car_api);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.72f);
            int height = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setLayout(width, height);
        }

        TextView output = dialog.findViewById(R.id.carApiOutput);
        ScrollView scroll = dialog.findViewById(R.id.carApiScroll);
        TextView refresh = dialog.findViewById(R.id.carApiRefresh);
        TextView close = dialog.findViewById(R.id.carApiClose);
        FlymePress.bind(refresh);
        FlymePress.bind(close);
        close.setOnClickListener(v -> dialog.dismiss());
        refresh.setOnClickListener(v -> load(c, dialog, output, refresh, scroll));

        dialog.show();
        load(c, dialog, output, refresh, scroll);
    }

    private static void load(Context c, Dialog dialog, TextView output,
                             TextView refresh, ScrollView scroll) {
        if (output == null) return;
        output.setText(R.string.car_api_loading);
        if (refresh != null) refresh.setEnabled(false);
        new Thread(() -> {
            String text = build(c);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!dialog.isShowing()) return;
                if (output != null) output.setText(text);
                if (refresh != null) refresh.setEnabled(true);
                if (scroll != null) scroll.scrollTo(0, 0);
            });
        }, "car-api").start();
    }

    private static String build(Context c) {
        CarConnection connection = CarConnection.get();
        if (!connection.ensure(c)) {
            return c.getString(R.string.car_api_error, "CarPropertyManager недоступен");
        }
        CarPropertyManager cpm = connection.cpm();
        if (cpm == null) {
            return c.getString(R.string.car_api_error, "CarPropertyManager = null");
        }

        StringBuilder sb = new StringBuilder();
        adaptNames = buildAdaptNames(c);
        sb.append("AdaptAPI: ").append(adaptAvailable ? "available" : "not available");
        if (adaptAvailable) sb.append(" (").append(adaptNames.size()).append(" names)");
        sb.append("\n\n");
        sb.append("ChargeStatus: ").append(ChargeStatusProbe.read(c)).append("\n\n");
        boolean listed = false;
        try {
            Method method = cpm.getClass().getMethod("getPropertyList");
            method.setAccessible(true);
            Object result = method.invoke(cpm);
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                if (!list.isEmpty()) {
                    listed = true;
                    for (Object config : list) {
                        appendConfig(sb, cpm, config);
                        if (sb.length() >= MAX_OUTPUT) {
                            sb.append("\n[output truncated]\n");
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            sb.append("getPropertyList: ").append(t).append("\n\n");
        }

        if (!listed) {
            sb.append("Список свойств недоступен, известные свойства:\n\n");
            for (Prop prop : FALLBACK) {
                appendKnown(sb, cpm, prop);
                if (sb.length() >= MAX_OUTPUT) {
                    sb.append("\n[output truncated]\n");
                    break;
                }
            }
        }
        if (sb.length() == 0) return c.getString(R.string.car_api_empty);
        return sb.toString();
    }

    private static void appendConfig(StringBuilder sb, CarPropertyManager cpm, Object config) {
        int id = asInt(call(config, "getPropertyId"), 0);
        int type = asInt(call(config, "getPropertyType"), 0);
        int access = asInt(call(config, "getAccess"), 0);
        int areaType = asInt(call(config, "getAreaType"), 0);
        Object min = call(config, "getMinValue");
        Object max = call(config, "getMaxValue");
        List<Integer> areas = asAreas(call(config, "getAreas"));

        sb.append(String.format(Locale.US, "0x%08X  %d  %s\n", id, id, name(id)));
        sb.append("  type=").append(typeName(type))
                .append("  access=").append(accessName(access))
                .append("  areaType=").append(areaType).append('\n');
        if (min != null || max != null) {
            sb.append("  min=").append(min).append("  max=").append(max).append('\n');
        }
        if (areas.isEmpty()) areas = new ArrayList<>(Arrays.asList(0, 1));
        sb.append("  areas=").append(areas).append('\n');
        for (int area : areas) {
            sb.append("  value[").append(area).append("]=")
                    .append(readValue(cpm, id, area)).append('\n');
        }
        sb.append('\n');
    }

    private static void appendKnown(StringBuilder sb, CarPropertyManager cpm, Prop prop) {
        sb.append(String.format(Locale.US, "0x%08X  %d  %s\n", prop.id, prop.id, prop.name));
        sb.append("  value[0]=").append(readValue(cpm, prop.id, 0)).append('\n');
        sb.append("  value[1]=").append(readValue(cpm, prop.id, 1)).append('\n');
        sb.append('\n');
    }

    private static String readValue(CarPropertyManager cpm, int id, int area) {
        try {
            CarPropertyValue<?> value = cpm.getProperty(id, area);
            if (value == null) return "null";
            Object v = value.getValue();
            return v == null ? "null" : String.valueOf(v);
        } catch (Throwable t) {
            return "—";
        }
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static List<Integer> asAreas(Object value) {
        List<Integer> out = new ArrayList<>();
        if (value instanceof int[]) {
            for (int v : (int[]) value) out.add(v);
        } else if (value instanceof List) {
            for (Object v : (List<?>) value) {
                if (v instanceof Number) out.add(((Number) v).intValue());
            }
        }
        return out;
    }

    private static String typeName(int type) {
        int base = type & 0x00F00000;
        switch (base) {
            case 0x00100000:
                return "string";
            case 0x00200000:
                return "bool";
            case 0x00400000:
                return "int32";
            case 0x00500000:
                return "int64";
            case 0x00600000:
                return "float";
            default:
                return String.format(Locale.US, "0x%X", type);
        }
    }

    private static String accessName(int access) {
        if (access == 1) return "R";
        if (access == 2) return "W";
        if (access == 3) return "R/W";
        return String.valueOf(access);
    }

    private static String name(int id) {
        String value = NAMES.get(id);
        if (value != null) return value;
        value = adaptNames.get(id);
        if (value != null) return value;
        value = standardName(id);
        return value == null ? "unknown" : value;
    }

    private static Map<Integer, String> buildAdaptNames(Context c) {
        Map<Integer, String> out = new HashMap<>();
        adaptAvailable = false;
        try {
            Class<?> carClass = Class.forName("com.ecarx.xui.adaptapi.car.Car");
            Method create = carClass.getMethod("createWrapper", Context.class);
            Object wrapper = create.invoke(null, c);
            if (wrapper == null) return out;
            adaptAvailable = true;
            Method get = wrapper.getClass().getMethod(
                    "getWrappedPropertyId", int.class, int.class);
            for (Map.Entry<Integer, String> entry : ADAPT_FUNCTIONS.entrySet()) {
                try {
                    Object property = get.invoke(wrapper, 2, entry.getKey());
                    if (property == null) continue;
                    Method idMethod = property.getClass().getMethod("getPropertyId");
                    Object id = idMethod.invoke(property);
                    if (id instanceof Integer) {
                        out.put((Integer) id, entry.getValue());
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String standardName(int id) {
        try {
            Class<?> cls = Class.forName("android.car.VehiclePropertyIds");
            Method method = cls.getMethod("toString", int.class);
            Object value = method.invoke(null, id);
            if (value instanceof String) {
                String text = (String) value;
                if (!text.isEmpty() && !text.startsWith("0x")) return text;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final class Prop {
        final int id;
        final String name;

        Prop(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
