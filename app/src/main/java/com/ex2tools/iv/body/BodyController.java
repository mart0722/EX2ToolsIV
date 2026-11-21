package com.ex2tools.iv.body;

import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.vehicle.PropertySpec;
import com.ex2tools.iv.vehicle.VhalGateway;

/** Окна, speed auto-lock и day/night — тоже через общий диспетчер. */
public final class BodyController {

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

    private CommandDispatcher.Result submitInt(int id, int area, int value) {
        return dispatcher.submit(new CommandDispatcher.Command(
                id, area, PropertySpec.Type.INT, value));
    }
}
