package com.ex2tools.iv.drive;

import android.content.Context;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.state.VehicleState;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.vehicle.PropertySpec;
import com.ex2tools.iv.vehicle.VhalGateway;

/**
 * Drive mode и рекуперация.
 *
 * setDrive/setRegen — одна команда по нажатию.
 * apply — «применить сейчас»: обе команды, пауза 450 мс между ними и проверка
 * фактического значения через VehicleState.
 *
 * Никакого наблюдения внутри контроллера: принудительное удержание режима
 * живёт отдельно, в service/DriveRegenWatchService, и только по включённому
 * пользователем переключателю.
 */
public final class DriveController {

    private static final int[] DRIVE_ORDER = {
            CarProps.DRIVE_ECO, CarProps.DRIVE_COMFORT, CarProps.DRIVE_SPORT
    };
    private static final int[] REGEN_ORDER = {
            CarProps.REGEN_WEAK, CarProps.REGEN_MEDIUM, CarProps.REGEN_STRONG
    };

    private static volatile DriveController instance;

    private final VhalGateway gateway = VhalGateway.get();
    private final VehicleState state = VehicleState.get();
    private final CommandDispatcher dispatcher = new CommandDispatcher(gateway, false);

    public static DriveController get() {
        DriveController local = instance;
        if (local == null) {
            synchronized (DriveController.class) {
                local = instance;
                if (local == null) {
                    local = new DriveController();
                    instance = local;
                }
            }
        }
        return local;
    }

    private DriveController() {
    }

    public CommandDispatcher.Result setDrive(int mode) {
        if (mode != CarProps.DRIVE_ECO
                && mode != CarProps.DRIVE_COMFORT
                && mode != CarProps.DRIVE_SPORT) {
            mode = CarProps.DRIVE_COMFORT;
        }
        return submitIntAny(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS, mode);
    }

    public CommandDispatcher.Result stepDrive(int direction) {
        Integer current = currentDrive();
        int base = current == null ? CarProps.DRIVE_COMFORT : current;
        return setDrive(step(DRIVE_ORDER, base, direction));
    }

    public CommandDispatcher.Result setRegen(int mode) {
        if (mode != CarProps.REGEN_WEAK
                && mode != CarProps.REGEN_MEDIUM
                && mode != CarProps.REGEN_STRONG) {
            mode = CarProps.REGEN_MEDIUM;
        }
        return submitIntAny(CarProps.REGEN_MODE, CarProps.REGEN_AREAS, mode);
    }

    public CommandDispatcher.Result stepRegen(int direction) {
        Integer current = currentRegen();
        int base = current == null ? CarProps.REGEN_MEDIUM : current;
        return setRegen(step(REGEN_ORDER, base, direction));
    }

    /** Фактический режим движения (из VehicleState, иначе прямое чтение). */
    public Integer currentDrive() {
        Integer cached = state.intAny(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
        return cached != null ? cached : readIntAny(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
    }

    /** Фактическая рекуперация. */
    public Integer currentRegen() {
        Integer cached = state.intAny(CarProps.REGEN_MODE, CarProps.REGEN_AREAS);
        return cached != null ? cached : readIntAny(CarProps.REGEN_MODE, CarProps.REGEN_AREAS);
    }

    /** Свежее чтение мимо кэша — для проверки, что запись реально применилась. */
    public Integer freshDrive() {
        return readIntAny(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
    }

    /** Свежее чтение мимо кэша. */
    public Integer freshRegen() {
        return readIntAny(CarProps.REGEN_MODE, CarProps.REGEN_AREAS);
    }

    public Outcome applySaved(Context c) {
        return apply(Prefs.drive(c), Prefs.regen(c));
    }

    /** Обе команды подряд с паузой и проверкой результата. */
    public Outcome apply(int drive, int regen) {
        CommandDispatcher.Result driveResult = setDrive(drive);
        sleep(450L);
        CommandDispatcher.Result regenResult = setRegen(regen);
        sleep(400L);

        Integer readDrive = freshDrive();
        Integer readRegen = freshRegen();
        boolean exact = readDrive != null && readRegen != null
                && readDrive == drive && readRegen == regen;
        boolean sent = driveResult.ok() && regenResult.ok();

        return new Outcome(sent, exact, drive, regen, readDrive, readRegen,
                driveResult.status, regenResult.status);
    }

    private CommandDispatcher.Result submitIntAny(int id, int[] areas, int value) {
        CommandDispatcher.Result last = null;
        for (int area : areas) {
            last = dispatcher.submit(new CommandDispatcher.Command(
                    id, area, PropertySpec.Type.INT, value));
            if (last.status == CommandDispatcher.Status.APPLIED
                    || last.status == CommandDispatcher.Status.SENT
                    || last.status == CommandDispatcher.Status.DRY_RUN) {
                return last;
            }
        }
        return last;
    }

    private Integer readIntAny(int id, int[] areas) {
        for (int area : areas) {
            int value = gateway.readInt(id, area);
            if (value != Integer.MIN_VALUE) return value;
        }
        return null;
    }

    public static String labelDrive(Context c, int value) {
        return CarProps.driveLabel(c, value);
    }

    public static String labelRegen(Context c, int value) {
        return CarProps.regenLabel(c, value);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int step(int[] order, int current, int direction) {
        int index = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                index = i;
                break;
            }
        }
        if (index < 0) index = order.length / 2;
        return order[Math.max(0, Math.min(order.length - 1, index + direction))];
    }

    /** Результат применения: что отправили, что прочитали, совпало ли. */
    public static final class Outcome {
        public final boolean sent;
        public final boolean exact;
        public final int drive;
        public final int regen;
        public final Integer readDrive;
        public final Integer readRegen;
        public final CommandDispatcher.Status driveStatus;
        public final CommandDispatcher.Status regenStatus;

        Outcome(boolean sent, boolean exact, int drive, int regen,
                Integer readDrive, Integer readRegen,
                CommandDispatcher.Status driveStatus,
                CommandDispatcher.Status regenStatus) {
            this.sent = sent;
            this.exact = exact;
            this.drive = drive;
            this.regen = regen;
            this.readDrive = readDrive;
            this.readRegen = readRegen;
            this.driveStatus = driveStatus;
            this.regenStatus = regenStatus;
        }

        public String describe() {
            return "drive=" + drive + "(" + driveStatus + ")"
                    + " regen=" + regen + "(" + regenStatus + ")"
                    + " read=" + readDrive + "/" + readRegen
                    + " exact=" + exact;
        }
    }
}
