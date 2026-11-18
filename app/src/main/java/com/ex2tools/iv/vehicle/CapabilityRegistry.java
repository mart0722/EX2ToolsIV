package com.ex2tools.iv.vehicle;

import android.car.hardware.property.CarPropertyManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Что реально поддерживается на этой машине: id, тип, доступ, зоны. */
public final class CapabilityRegistry {

    public static final class Config {
        public final int id;
        public final int type;
        public final int access;
        public final List<Integer> areas;
        public final Object min;
        public final Object max;

        Config(int id, int type, int access, List<Integer> areas, Object min, Object max) {
            this.id = id;
            this.type = type;
            this.access = access;
            this.areas = areas == null ? new ArrayList<>() : areas;
            this.min = min;
            this.max = max;
        }

        public boolean readable() {
            return (access & 1) != 0;
        }

        public boolean writable() {
            return (access & 2) != 0;
        }
    }

    private final Map<Integer, Config> configs = new LinkedHashMap<>();

    public CapabilityRegistry(CarPropertyManager cpm) {
        load(cpm);
    }

    private void load(CarPropertyManager cpm) {
        if (cpm != null) {
            try {
                Method method = cpm.getClass().getMethod("getPropertyList");
                method.setAccessible(true);
                Object result = method.invoke(cpm);
                if (result instanceof List) {
                    for (Object raw : (List<?>) result) {
                        int id = asInt(call(raw, "getPropertyId"));
                        if (id == 0) continue;
                        configs.put(id, new Config(
                                id,
                                asInt(call(raw, "getPropertyType")),
                                asInt(call(raw, "getAccess")),
                                asAreas(call(raw, "getAreas")),
                                call(raw, "getMinValue"),
                                call(raw, "getMaxValue")));
                    }
                }
            } catch (Throwable ignored) {
                // Some builds hide getPropertyList. Fall back to known specs.
            }
        }

        for (PropertySpec spec : VehicleProperties.all()) {
            Config existing = configs.get(spec.id);
            if (existing == null) {
                configs.put(spec.id, new Config(spec.id, 0, 3, ints(spec.areas),
                        spec.min, spec.max));
            } else if (existing.areas.isEmpty()) {
                existing.areas.addAll(ints(spec.areas));
            }
        }
    }

    public boolean has(int id) {
        return configs.containsKey(id);
    }

    public Config get(int id) {
        return configs.get(id);
    }

    public Collection<Config> all() {
        return configs.values();
    }

    public int count() {
        return configs.size();
    }

    public int[] areas(int id) {
        Config config = configs.get(id);
        if (config == null || config.areas.isEmpty()) return new int[]{0};
        int[] out = new int[config.areas.size()];
        for (int i = 0; i < out.length; i++) out[i] = config.areas.get(i);
        return out;
    }

    public boolean readable(int id) {
        Config config = configs.get(id);
        return config == null || config.readable();
    }

    private static Object call(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static List<Integer> asAreas(Object value) {
        List<Integer> out = new ArrayList<>();
        if (value instanceof int[]) {
            for (int area : (int[]) value) out.add(area);
        } else if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) out.add(asInt(item));
        }
        return out;
    }

    private static List<Integer> ints(int[] values) {
        List<Integer> out = new ArrayList<>();
        if (values != null) {
            for (int value : values) out.add(value);
        }
        return out;
    }
}
