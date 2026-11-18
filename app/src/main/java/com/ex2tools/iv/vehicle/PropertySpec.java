package com.ex2tools.iv.vehicle;

/** Описание одного свойства VHAL: идентификатор, зоны, тип и допустимый диапазон. */
public final class PropertySpec {

    public enum Type {
        INT,
        FLOAT,
        BOOLEAN,
        UNKNOWN
    }

    public final int id;
    public final String key;
    public final Type type;
    public final int[] areas;
    public final float min;
    public final float max;

    public PropertySpec(int id, String key, Type type, int[] areas, float min, float max) {
        this.id = id;
        this.key = key;
        this.type = type;
        this.areas = areas == null || areas.length == 0 ? new int[]{0} : areas.clone();
        this.min = min;
        this.max = max;
    }

    public boolean covers(int area) {
        for (int value : areas) {
            if (value == area) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("0x%08X %s %s", id, key, type);
    }
}
