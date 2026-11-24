package com.ex2tools.iv.body;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.vehicle.PropertySpec;
import com.ex2tools.iv.vehicle.VhalGateway;

/** Окна, speed auto-lock и day/night — тоже через общий диспетчер. */
public final class BodyController {

    public enum PowerOffResult { SENT, NOT_PARKED, UNAVAILABLE }

    enum WipersOnLockResult {
        SENT, PARK_NOT_CONFIRMED, SPEED_UNAVAILABLE, VEHICLE_MOVING,
        POSITION_UNAVAILABLE, ALREADY_RAISED, IGNITION_UNAVAILABLE, IGNITION_NOT_POWERED,
        SIGNAL_EXPIRED, WRITE_REJECTED
    }

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
        CarPropertyValue<?> value = availableProperty(CarProps.WIPER_SERVICE_POSITION);
        return value == null ? null : decodeWiperServicePosition(value.getValue());
    }

    public static Boolean decodeWiperServicePosition(Object value) {
        if (value instanceof Boolean active) return active;
        if (!(value instanceof Integer position)) return null;
        return switch (position.intValue()) {
            case 1 -> true;
            case 0, 2 -> false;
            default -> null;
        };
    }

    public boolean toggleWiperServicePosition() {
        if (!Boolean.TRUE.equals(parkSelected())) {
            Log.i("BodyController", "Wiper service blocked: Park not confirmed");
            return false;
        }

        Boolean active = wiperServicePosition();
        if (active == null) {
            Log.i("BodyController", "Wiper service blocked: position unavailable");
            return false;
        }
        return setWiperServicePosition(!active);
    }

    WipersOnLockResult raiseWipersOnLock(long deadlineNs) {
        if (!Boolean.TRUE.equals(parkSelected())) return WipersOnLockResult.PARK_NOT_CONFIRMED;
        Number speed = availableValue(CarProps.PERF_VEHICLE_SPEED);
        if (speed == null || !Float.isFinite(speed.floatValue())) return WipersOnLockResult.SPEED_UNAVAILABLE;
        if (speed.floatValue() != 0f) return WipersOnLockResult.VEHICLE_MOVING;
        Boolean raised = wiperServicePosition();
        if (raised == null) return WipersOnLockResult.POSITION_UNAVAILABLE;
        if (raised) return WipersOnLockResult.ALREADY_RAISED;
        Boolean powered = Telemetry.decodeIgnitionOn(availableProperty(CarProps.IGNITION_STATE_VENDOR));
        if (powered == null) return WipersOnLockResult.IGNITION_UNAVAILABLE;
        if (!powered) return WipersOnLockResult.IGNITION_NOT_POWERED;
        if (SystemClock.elapsedRealtimeNanos() > deadlineNs) return WipersOnLockResult.SIGNAL_EXPIRED;
        return setWiperServicePosition(true) ? WipersOnLockResult.SENT : WipersOnLockResult.WRITE_REJECTED;
    }

    private boolean setWiperServicePosition(boolean active) {
        return dispatcher.submit(new CommandDispatcher.Command(
                CarProps.WIPER_SERVICE_FUNCTION, 0, PropertySpec.Type.BOOLEAN, active), false).ok();
    }

    public PowerOffResult requestParkedPowerOff(Context context) {
        if (!gateway.ensure(context) || availableProperty(CarProps.VEHICLE_POWER_OFF_REQUEST) == null) {
            return PowerOffResult.UNAVAILABLE;
        }
        Boolean parked = parkSelected();
        Number speed = availableValue(CarProps.PERF_VEHICLE_SPEED);
        if (parked == null || speed == null || !Float.isFinite(speed.floatValue())) {
            return PowerOffResult.UNAVAILABLE;
        }
        if (!parked || speed.floatValue() != 0f) {
            return PowerOffResult.NOT_PARKED;
        }

        // Flyme's Boolean function sends the power-off request the stock dialog uses.
        CommandDispatcher.Result result = dispatcher.submit(new CommandDispatcher.Command(
                CarProps.VEHICLE_POWER_OFF_FUNCTION, 0, PropertySpec.Type.BOOLEAN, true), false);
        return result.ok() ? PowerOffResult.SENT : PowerOffResult.UNAVAILABLE;
    }

    private Boolean parkSelected() {
        // The EX2 adapter maps its GEAR_SELECTION value 1 to Android's GEAR_PARK.
        CarPropertyValue<?> vendor = availableProperty(CarProps.GEAR_SELECTION_VENDOR);
        if (vendor != null && vendor.getValue() instanceof Integer gear) {
            int park = vendor.getPropertyId() == CarProps.GEAR
                    ? CarProps.GEAR_PARK : CarProps.GEAR_SELECTION_VENDOR_PARK;
            return gear == park;
        }
        Number standard = availableValue(CarProps.GEAR);
        return standard == null ? null : standard.intValue() == CarProps.GEAR_PARK;
    }

    private Number availableValue(int property) {
        CarPropertyValue<?> value = availableProperty(property);
        return value != null && value.getValue() instanceof Number number ? number : null;
    }

    private CarPropertyValue<?> availableProperty(int property) {
        CarPropertyValue<?> value = gateway.readValue(property, 0);
        return value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE ? value : null;
    }

    private CommandDispatcher.Result submitInt(int id, int area, int value) {
        return dispatcher.submit(new CommandDispatcher.Command(
                id, area, PropertySpec.Type.INT, value));
    }
}
