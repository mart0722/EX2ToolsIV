package com.ex2tools.iv.body;

import android.car.hardware.CarPropertyValue;
import android.content.Context;

import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.vehicle.PropertySpec;
import com.ex2tools.iv.vehicle.VhalGateway;

/** Окна, speed auto-lock и day/night — тоже через общий диспетчер. */
public final class BodyController {

    public enum PowerOffResult { SENT, NOT_PARKED, UNAVAILABLE }

    private static volatile BodyController instance;

    private final VhalGateway gateway = VhalGateway.get();
    private final CommandDispatcher dispatcher = new CommandDispatcher(gateway, false);

    public static BodyController get() {
        BodyController local = instance;
        if (local == null) {
            synchronized (BodyController.class) {
                local = instance;
                if (local == null) {
                    local = new BodyController();
                    instance = local;
                }
            }
        }
        return local;
    }

    private BodyController() {
    }

    public CommandDispatcher.Result setWindow(int position, int[] areas) {
        int value = Math.max(0, Math.min(88, position));
        CommandDispatcher.Result last = null;
        if (areas != null) {
            for (int area : areas) {
                last = submitInt(CarProps.WINDOW_POSITION, area, value);
                if (last != null && last.ok()) return last;
            }
            // На части прошивок WINDOW_POSITION нет, а команда лежит в WINDOW_MOVE.
            for (int area : areas) {
                last = submitInt(CarProps.WINDOW_MOVE, area, value);
                if (last != null && last.ok()) return last;
            }
        }
        return last;
    }

    public CommandDispatcher.Result setSpeedAutoLock(boolean enabled) {
        return submitInt(CarProps.SPEED_AUTO_LOCK, 0, enabled ? 2 : 1);
    }

    public CommandDispatcher.Result setDayNight(boolean night) {
        int value = night ? CarProps.DAY_NIGHT_NIGHT : CarProps.DAY_NIGHT_DAY;
        return submitInt(CarProps.DAY_NIGHT_SETTING, 0, value);
    }

    private Boolean wiperServicePosition() {
        CarPropertyValue<?> value =
                gateway.readValue(CarProps.WIPER_SERVICE_POSITION, 0);
        if (value == null || value.getStatus() != 0 || !(value.getValue() instanceof Integer)) {
            return null;
        }
        int position = (Integer) value.getValue();
        return switch (position) {
            case 1 -> true;
            case 0, 2 -> false;
            default -> null;
        };
    }

    public boolean toggleWiperServicePosition() {
        CarPropertyValue<?> gear = gateway.readValue(CarProps.GEAR, 0);
        if (gear == null || gear.getStatus() != 0
                || !Integer.valueOf(CarProps.GEAR_PARK).equals(gear.getValue())) return false;

        Boolean active = wiperServicePosition();
        return active != null && submitInt(CarProps.WIPER_SERVICE_POSITION, 0, active ? 0 : 1).ok();
    }

    public PowerOffResult requestParkedPowerOff(Context context) {
        if (!gateway.ensure(context) || availableValue(CarProps.VEHICLE_POWER_OFF_REQUEST) == null) {
            return PowerOffResult.UNAVAILABLE;
        }
        Number gear = availableValue(CarProps.GEAR);
        Number speed = availableValue(CarProps.PERF_VEHICLE_SPEED);
        if (gear == null || speed == null || !Float.isFinite(speed.floatValue())) {
            return PowerOffResult.UNAVAILABLE;
        }
        if (gear.intValue() != CarProps.GEAR_PARK || speed.floatValue() != 0f) {
            return PowerOffResult.NOT_PARKED;
        }

        // Штатный Flyme Auto передаёт 1 в MMI_POWER_ON_OFF_REQ при подтверждении.
        CommandDispatcher.Result result = dispatcher.submit(new CommandDispatcher.Command(
                CarProps.VEHICLE_POWER_OFF_REQUEST, 0, PropertySpec.Type.INT, 1), false);
        return result.ok() ? PowerOffResult.SENT : PowerOffResult.UNAVAILABLE;
    }

    private Number availableValue(int property) {
        CarPropertyValue<?> value = gateway.readValue(property, 0);
        if (value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                && value.getValue() instanceof Number number) {
            return number;
        }
        return null;
    }

    private CommandDispatcher.Result submitInt(int id, int area, int value) {
        return dispatcher.submit(new CommandDispatcher.Command(
                id, area, PropertySpec.Type.INT, value));
    }
}
