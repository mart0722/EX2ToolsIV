package com.ex2tools.iv.vehicle;

import com.ex2tools.iv.car.CarProps;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Наши описания свойств. ID — интерфейс машины, имена и структура — наши. */
public final class VehicleProperties {

    private static final Map<Integer, PropertySpec> SPECS = new LinkedHashMap<>();

    static {
        add(CarProps.SOC, "vehicle.soc", PropertySpec.Type.FLOAT, new int[]{0}, 0f, 100f);
        add(CarProps.RANGE, "vehicle.range", PropertySpec.Type.FLOAT, new int[]{0}, 0f, 2000f);
        add(CarProps.ODOMETER_VENDOR, "vehicle.odometer_vendor", PropertySpec.Type.FLOAT,
                new int[]{0}, 0f, 2000000f);
        add(CarProps.ODOMETER, "vehicle.odometer", PropertySpec.Type.FLOAT,
                new int[]{0}, 0f, 2000000f);
        add(CarProps.DRIVE_MILEAGE, "vehicle.mileage", PropertySpec.Type.FLOAT,
                new int[]{0}, 0f, 2000000f);
        add(CarProps.AVG_CONSUMPTION, "vehicle.avg_consumption", PropertySpec.Type.INT,
                new int[]{0}, 0f, 5000f);
        add(CarProps.AUX_12V, "vehicle.aux_12v", PropertySpec.Type.INT, new int[]{0}, 0f, 200f);
        add(CarProps.GEAR, "vehicle.gear", PropertySpec.Type.INT, new int[]{0}, 0f, 15f);
        add(CarProps.PERF_VEHICLE_SPEED, "vehicle.speed", PropertySpec.Type.FLOAT,
                new int[]{0}, 0f, 250f);
        add(CarProps.ESC_SPEED, "vehicle.esc_speed", PropertySpec.Type.INT,
                new int[]{0}, 0f, 250f);

        add(CarProps.DRIVE_MODE, "drive.mode", PropertySpec.Type.INT,
                CarProps.DRIVE_AREAS, 0f, 10f);
        add(CarProps.REGEN_MODE, "drive.regen", PropertySpec.Type.INT,
                CarProps.REGEN_AREAS, 0f, 10f);

        add(CarProps.HVAC_TEMP_CURRENT, "climate.cabin_temp", PropertySpec.Type.FLOAT,
                CarProps.CABIN_AREAS, -45f, 65f);
        add(CarProps.HVAC_TEMP_AOSP, "climate.target_temp_aosp", PropertySpec.Type.FLOAT,
                CarProps.HVAC_AREAS, 16f, 32f);
        add(CarProps.HVAC_TEMP_ADJUST, "climate.temp_adjust", PropertySpec.Type.FLOAT,
                CarProps.HVAC_AREAS, 5f, 40f);
        add(CarProps.HVAC_FAN_SPEED, "climate.fan", PropertySpec.Type.INT,
                CarProps.FAN_AREAS, 0f, 7f);
        add(CarProps.HVAC_FAN_DIRECTION, "climate.vent", PropertySpec.Type.INT,
                CarProps.FAN_DIRECTION_AREAS, 0f, 10f);
        add(CarProps.HVAC_AC_ON, "climate.ac", PropertySpec.Type.BOOLEAN,
                CarProps.AC_AREAS, 0f, 1f);
        add(CarProps.HVAC_RECIRC_ON, "climate.recirc", PropertySpec.Type.BOOLEAN,
                CarProps.AC_AREAS, 0f, 1f);
        add(CarProps.HVAC_REAR_DEFROSTER_ON, "climate.rear_defrost", PropertySpec.Type.BOOLEAN,
                new int[]{2}, 0f, 1f);
        add(CarProps.HVAC_FRONT_MAX_DEFROST_ON, "climate.max_defrost", PropertySpec.Type.BOOLEAN,
                new int[]{117}, 0f, 1f);
        add(CarProps.HVAC_WINDSHIELD_HEAT, "climate.windshield_heat", PropertySpec.Type.BOOLEAN,
                new int[]{117, 0, 1}, 0f, 1f);
        add(CarProps.STEERING_WHEEL_HEAT, "comfort.wheel_heat", PropertySpec.Type.BOOLEAN,
                CarProps.WHEEL_HEAT_AREAS, 0f, 1f);
        add(CarProps.SEAT_HEAT, "comfort.seat_heat", PropertySpec.Type.INT,
                CarProps.SEAT_AREAS, -2f, 2f);

        add(CarProps.AC_AMBIENT_TEMP, "climate.ambient_temp", PropertySpec.Type.INT,
                new int[]{0}, 0f, 200f);
        add(CarProps.ENV_OUTSIDE_TEMPERATURE, "vehicle.outside_temp", PropertySpec.Type.FLOAT,
                new int[]{0}, -60f, 90f);

        add(CarProps.WIPER_SERVICE_POSITION, "vehicle.wiper_service_position", PropertySpec.Type.INT,
                new int[]{0}, 0f, 2f);
        add(CarProps.VEHICLE_POWER_OFF_REQUEST, "vehicle.power_off_request", PropertySpec.Type.INT,
                new int[]{0}, 0f, 2f);
        add(CarProps.SPEED_AUTO_LOCK, "vehicle.speed_auto_lock", PropertySpec.Type.INT,
                new int[]{0}, 0f, 1f);
        add(CarProps.WINDOW_POSITION, "vehicle.window_position", PropertySpec.Type.INT,
                new int[]{0}, 0f, 100f);
        add(CarProps.DAY_NIGHT_SETTING, "vehicle.day_night", PropertySpec.Type.INT,
                new int[]{0}, 0f, 3f);
    }

    private static void add(int id, String key, PropertySpec.Type type,
                            int[] areas, float min, float max) {
        SPECS.put(id, new PropertySpec(id, key, type, areas, min, max));
    }

    public static PropertySpec get(int id) {
        return SPECS.get(id);
    }

    public static Collection<PropertySpec> all() {
        return SPECS.values();
    }

    public static String key(int id) {
        PropertySpec spec = SPECS.get(id);
        return spec == null ? String.format("0x%08X", id) : spec.key;
    }

    private VehicleProperties() {
    }
}
