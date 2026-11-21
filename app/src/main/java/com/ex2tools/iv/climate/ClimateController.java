package com.ex2tools.iv.climate;

import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.state.VehicleState;
import com.ex2tools.iv.vehicle.CapabilityRegistry;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import com.ex2tools.iv.vehicle.PropertySpec;
import com.ex2tools.iv.vehicle.VhalGateway;

/**
 * Климат по новой схеме: читает через VhalGateway, команды отдаёт
 * в CommandDispatcher. В dry-run ничего не пишет.
 */
public final class ClimateController {

    /** false — климат пишет реальные команды через CommandDispatcher. */
    public static final boolean DRY_RUN = false;

    private static final int[] VENT_ORDER = {1, 2, 3, 4, 6};
    private static final int[] TEMP_AREAS = {1, 4};
    private static final int[] VENT_AREAS = {1, 4};

    private static volatile ClimateController instance;

    private final VhalGateway gateway = VhalGateway.get();
    private final CommandDispatcher dispatcher =
            new CommandDispatcher(gateway, DRY_RUN);

    public static ClimateController get() {
        ClimateController local = instance;
        if (local == null) {
            synchronized (ClimateController.class) {
                local = instance;
                if (local == null) {
                    local = new ClimateController();
                    instance = local;
                }
            }
        }
        return local;
    }

    private ClimateController() {
    }

    // --- fan ---

    public CommandDispatcher.Result setFan(int level) {
        return submitIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS,
                Math.max(0, Math.min(7, level)));
    }

    public CommandDispatcher.Result adjustFan(int delta) {
        Integer current = readIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS);
        int value = current == null ? 3 : current;
        return setFan(value + delta);
    }

    // --- temperature ---

    public CommandDispatcher.Result setTemp(float value) {
        return setTemp(value, true);
    }

    private CommandDispatcher.Result setTemp(float value, boolean awaitConfirmation) {
        int celsius = Math.max(17, Math.min(32, Math.round(value)));
        CommandDispatcher.Result last = null;
        for (int area : TEMP_AREAS) {
            CommandDispatcher.Result result = dispatcher.submit(new CommandDispatcher.Command(
                    CarProps.HVAC_TEMP_ADJUST, area, PropertySpec.Type.INT, celsius), awaitConfirmation);
            if (last == null || result.ok()) last = result;
        }
        if (last != null && last.ok()) {
            pendingTempAt = android.os.SystemClock.elapsedRealtime();
            pendingTemp = celsius;
            android.content.Context context = gateway.appContext();
            if (context != null) com.ex2tools.iv.Prefs.setLastTempSet(context, celsius);
        }
        if (awaitConfirmation) logTempState("write " + celsius);
        return last;
    }

    /**
     * Запись уставки без отметки «пользователь задал температуру».
     * Нужна климат-контролю: он двигает уставку относительно базовой,
     * не подменяя желаемую температуру пользователя.
     */
    public CommandDispatcher.Result setTempSilent(float value) {
        int celsius = Math.max(17, Math.min(32, Math.round(value)));
        CommandDispatcher.Result last = null;
        for (int area : TEMP_AREAS) {
            CommandDispatcher.Result set = dispatcher.submit(new CommandDispatcher.Command(
                    CarProps.HVAC_TEMP_SET, area, PropertySpec.Type.INT, celsius), false);
            dispatcher.submit(new CommandDispatcher.Command(
                    CarProps.HVAC_TEMP_ADJUST, area, PropertySpec.Type.INT, celsius), false);
            last = set;
        }
        logTempState("silent " + celsius);
        return last;
    }

    /** Текущая уставка климата, прочитанная из VHAL. */
    public Float currentTarget() {
        return readTargetTemp(state());
    }

    public CommandDispatcher.Result adjustTemp(float delta) {
        return adjustTemp(delta, true);
    }

    public synchronized CommandDispatcher.Result adjustTempInteractive(float delta) {
        return adjustTemp(delta, false);
    }

    private synchronized CommandDispatcher.Result adjustTemp(float delta, boolean awaitConfirmation) {
        Float current = pendingTemp();
        if (current == null) current = readTargetTemp(state());
        if (current == null) return CommandDispatcher.Result.unavailable("Climate setpoint unavailable");
        return setTemp(Math.round(current) + delta, awaitConfirmation);
    }

    private Integer interactiveFan;
    private long interactiveFanAt;

    public synchronized CommandDispatcher.Result adjustFanInteractive(int delta) {
        long now = android.os.SystemClock.elapsedRealtime();
        Integer current = interactiveFan != null && now - interactiveFanAt < 1000L
                ? interactiveFan : readIntAny(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS);
        int target = Math.max(0, Math.min(7, (current == null ? 3 : current) + delta));
        CommandDispatcher.Result result = null;
        for (int area : state().areas(CarProps.HVAC_FAN_SPEED, CarProps.FAN_AREAS)) {
            result = dispatcher.submit(new CommandDispatcher.Command(
                    CarProps.HVAC_FAN_SPEED, area, PropertySpec.Type.INT, target), false);
            if (result.ok()) break;
        }
        if (result != null && result.ok()) {
            interactiveFan = target;
            interactiveFanAt = android.os.SystemClock.elapsedRealtime();
        } else interactiveFan = null;
        return result;
    }

    /**
     * Значение, которое пользователь выставил только что. ECU применяет
     * уставку с задержкой, поэтому несколько секунд показываем свою цифру.
     */
    public static Float pendingTemp() {
        if (Float.isNaN(pendingTemp)) return null;
        if (android.os.SystemClock.elapsedRealtime() - pendingTempAt > 4_000L) {
            return null;
        }
        return pendingTemp;
    }

    private static volatile float pendingTemp = Float.NaN;
    private static volatile long pendingTempAt;

    /** Уставка, которую ECU выставляет при пробуждении, пока HMI не вернёт своё. */
    public static final int DEFAULT_TEMP = 20;

    private static final long FIRST_READ_AT = android.os.SystemClock.elapsedRealtime();
    private static volatile boolean observedRealTemp;

    /**
     * Пока HMI не восстановил сохранённую уставку, VHAL отдаёт 20.
     * В это короткое окно показываем нашу последнюю известную уставку.
     */
    public static boolean shouldUseCachedTemp() {
        if (observedRealTemp) return false;
        return android.os.SystemClock.elapsedRealtime() - FIRST_READ_AT < 12_000L;
    }

    public static void noteObservedTemp(float value) {
        if (Math.round(value) != DEFAULT_TEMP) observedRealTemp = true;
    }

    /** Диагностика: что реально лежит в свойствах температуры после записи. */
    private void logTempState(String why) {
        android.content.Context context = gateway.appContext();
        if (context == null) return;
        int[] ids = {CarProps.HVAC_TEMP_ADJUST, CarProps.HVAC_TEMP_AOSP};
        String[] names = {"adjust", "aosp"};
        StringBuilder sb = new StringBuilder("temp diag ").append(why);
        for (int i = 0; i < ids.length; i++) {
            for (int area : new int[]{1, 4}) {
                float value = gateway.readFloat(ids[i], area);
                sb.append(' ').append(names[i]).append('[').append(area).append("]=")
                        .append(Float.isNaN(value) ? "—" : String.valueOf(value));
            }
        }
        com.ex2tools.iv.LogStore.add(context, sb.toString());
    }

    // --- vent ---

    public synchronized CommandDispatcher.Result toggleVent(int direction) {
        Integer current = readIntAny(CarProps.HVAC_FAN_DIRECTION, CarProps.FAN_DIRECTION_AREAS);
        long now = android.os.SystemClock.elapsedRealtime();
        int base = pendingVent != null && now - pendingVentAt < 2000L
                ? pendingVent : (current == null ? 0 : current);
        return setVent(VentSelection.toggle(base, direction));
    }

    private Integer pendingVent;
    private long pendingVentAt;

    public synchronized CommandDispatcher.Result setVent(int value) {
        int mode = validVent(value) ? value : 1;
        CommandDispatcher.Result last = null;
        for (int area : VENT_AREAS) {
            last = dispatcher.submit(new CommandDispatcher.Command(
                    CarProps.HVAC_FAN_DIRECTION, area, PropertySpec.Type.INT, mode), false);
        }
        if (last != null && last.ok()) {
            pendingVent = mode;
            pendingVentAt = android.os.SystemClock.elapsedRealtime();
        } else pendingVent = null;
        return last;
    }

    public CommandDispatcher.Result stepVent(int direction) {
        Integer current = readIntAny(CarProps.HVAC_FAN_DIRECTION,
                CarProps.FAN_DIRECTION_AREAS);
        int base = current == null ? 1 : current;
        int index = indexOf(VENT_ORDER, base);
        if (index < 0) index = 0;
        int next = VENT_ORDER[Math.max(0, Math.min(VENT_ORDER.length - 1, index + direction))];
        return setVent(next);
    }

    private static boolean validVent(int value) {
        for (int mode : VENT_ORDER) {
            if (mode == value) return true;
        }
        return false;
    }

    // --- ac / recirc ---

    public CommandDispatcher.Result setAc(boolean value) {
        int type = capabilityType(CarProps.HVAC_AC_ON);
        boolean useBool = type == 0 || (type & 0x00F00000) == 0x00200000;
        CommandDispatcher.Result last = tryAc(value, useBool);
        if (!DRY_RUN && last != null && !last.ok()) {
            CommandDispatcher.Result alt = tryAc(value, !useBool);
            if (alt != null) last = alt;
        }
        if (!DRY_RUN && (last == null || !last.ok())) {
            last = tryAcId(CarProps.HVAC_AC_ON_AOSP, value, true);
        }
        if (!DRY_RUN && (last == null || !last.ok())) {
            last = tryAcId(CarProps.AC_POWER, value, false);
        }
        return last;
    }

    private CommandDispatcher.Result tryAcId(int id, boolean value, boolean asBool) {
        int[] areas = state().areas(id, new int[]{117});
        CommandDispatcher.Result last = null;
        for (int area : areas) {
            last = asBool
                    ? dispatcher.submit(new CommandDispatcher.Command(
                            id, area, PropertySpec.Type.BOOLEAN, value))
                    : dispatcher.submit(new CommandDispatcher.Command(
                            id, area, PropertySpec.Type.INT, value ? 1 : 0));
            if (last.status == CommandDispatcher.Status.APPLIED
                    || last.status == CommandDispatcher.Status.SENT) return last;
        }
        return last;
    }

    private CommandDispatcher.Result tryAc(boolean value, boolean asBool) {
        CommandDispatcher.Result last = null;
        for (int area : CarProps.AC_AREAS) {
            last = asBool
                    ? dispatcher.submit(new CommandDispatcher.Command(
                            CarProps.HVAC_AC_ON, area, PropertySpec.Type.BOOLEAN, value))
                    : dispatcher.submit(new CommandDispatcher.Command(
                            CarProps.HVAC_AC_ON, area, PropertySpec.Type.INT, value ? 1 : 0));
            if (DRY_RUN
                    || last.status == CommandDispatcher.Status.APPLIED
                    || last.status == CommandDispatcher.Status.SENT) break;
        }
        return last;
    }

    public CommandDispatcher.Result toggleAc() {
        Boolean current = readAc();
        return setAc(current == null || !current);
    }

    public CommandDispatcher.Result setRecirc(boolean value) {
        // 0x21401026 - Integer, поэтому пишем INT; bool-вариант только как fallback.
        CommandDispatcher.Result last = submitIntAny(CarProps.HVAC_RECIRC_ON,
                new int[]{0}, value ? 1 : 0);
        if (!DRY_RUN && (last == null || !last.ok())) {
            CommandDispatcher.Result alt = submitBoolAny(CarProps.HVAC_RECIRC_ON_AOSP,
                    new int[]{117}, value);
            if (alt != null) last = alt;
        }
        return last;
    }

    public CommandDispatcher.Result toggleRecirc() {
        Boolean current = readRecirc();
        return setRecirc(current == null || !current);
    }

    // --- defrost / heat ---

    public CommandDispatcher.Result setRearDefrost(boolean value) {
        return submitBoolAny(CarProps.HVAC_REAR_DEFROSTER_ON, new int[]{2}, value);
    }

    public CommandDispatcher.Result toggleRearDefrost() {
        Boolean current = readRearDefrost();
        return setRearDefrost(current == null || !current);
    }

    public CommandDispatcher.Result setFrontMaxDefrost(boolean value) {
        return submitBoolAny(CarProps.HVAC_FRONT_MAX_DEFROST_ON, new int[]{1}, value);
    }

    public CommandDispatcher.Result toggleFrontMaxDefrost() {
        Boolean current = readFrontMaxDefrost();
        return setFrontMaxDefrost(current == null || !current);
    }

    public CommandDispatcher.Result setWindshieldHeat(boolean value) {
        return submitBoolAny(CarProps.HVAC_WINDSHIELD_HEAT, new int[]{117}, value);
    }

    public CommandDispatcher.Result toggleWindshieldHeat() {
        Boolean current = readWindshieldHeat();
        return setWindshieldHeat(current == null || !current);
    }

    // --- comfort ---

    public CommandDispatcher.Result setWheelHeatLevel(int level) {
        return submitIntAny(CarProps.STEERING_WHEEL_HEAT, CarProps.WHEEL_HEAT_AREAS,
                Math.max(0, Math.min(3, level)));
    }

    public CommandDispatcher.Result setWheelHeat(boolean value) {
        return setWheelHeatLevel(value ? 1 : 0);
    }

    public CommandDispatcher.Result toggleWheelHeat() {
        Integer current = readIntAny(CarProps.STEERING_WHEEL_HEAT,
                CarProps.WHEEL_HEAT_AREAS);
        return setWheelHeat(current == null || current == 0);
    }

    public CommandDispatcher.Result setSeatHeat(int area, int level) {
        return dispatcher.submit(new CommandDispatcher.Command(
                CarProps.SEAT_HEAT, area, PropertySpec.Type.INT,
                Math.max(0, Math.min(3, level))), false);
    }

    public CommandDispatcher.Result cycleSeat(int area) {
        // Читаем именно эту зону, иначе уровень водителя «переезжает» на пассажира.
        Integer current = readSeatExact(area);
        int value = current == null ? 0 : Math.max(0, Math.min(3, current));
        return setSeatHeat(area, (value + 1) % 4);
    }

    private Integer readSeatExact(int area) {
        VehicleState vs = state();
        Integer cached = vs.intValue(CarProps.SEAT_HEAT, area);
        if (cached != null) return cached;
        int raw = gateway.readInt(CarProps.SEAT_HEAT, area);
        return raw == Integer.MIN_VALUE ? null : raw;
    }

    // --- helpers ---

    private CommandDispatcher.Result submitBool(int id, int area, boolean value) {
        return dispatcher.submit(new CommandDispatcher.Command(
                id, area, PropertySpec.Type.BOOLEAN, value));
    }

    /**
     * Пробует зоны из реестра по порядку, затем запасную зону.
     * Нужен там, где старая зона (117) не совпадает с реальной (max defrost).
     */
    private CommandDispatcher.Result submitBoolAny(int id, int[] fallback, boolean value) {
        int[] areas = state().areas(id, fallback);
        CommandDispatcher.Result last = null;
        for (int area : areas) {
            last = submitBool(id, area, value);
            if (DRY_RUN
                    || last.status == CommandDispatcher.Status.APPLIED
                    || last.status == CommandDispatcher.Status.SENT) return last;
        }
        if (fallback.length > 0 && !contains(areas, fallback[0])) {
            last = submitBool(id, fallback[0], value);
        }
        return last;
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) return true;
        }
        return false;
    }

    private CommandDispatcher.Result submitIntAny(int id, int[] areas, int value) {
        CommandDispatcher.Result last = null;
        for (int area : areas) {
            last = dispatcher.submit(new CommandDispatcher.Command(
                    id, area, PropertySpec.Type.INT, value), false);
            if (DRY_RUN
                    || last.status == CommandDispatcher.Status.APPLIED
                    || last.status == CommandDispatcher.Status.SENT) return last;
        }
        return last;
    }

    private Integer readIntAny(int id, int[] fallback) {
        VehicleState vs = state();
        return vs.intAny(id, vs.areas(id, fallback));
    }

    private Boolean readBoolAny(int id, int[] fallback) {
        VehicleState vs = state();
        return vs.boolAny(id, vs.areas(id, fallback));
    }

    private VehicleState state() {
        VehicleState state = VehicleState.get();
        if (!state.isReady()) {
            android.content.Context context = gateway.appContext();
            if (context != null) state.ensure(context);
        }
        return state;
    }

    private Boolean readAc() {
        Boolean value = readBoolAny(CarProps.HVAC_AC_ON, CarProps.AC_AREAS);
        if (value != null) return value;
        Integer raw = readIntAny(CarProps.HVAC_AC_ON, CarProps.AC_AREAS);
        return raw == null ? null : raw != 0;
    }

    private Boolean readRecirc() {
        return readBoolAny(CarProps.HVAC_RECIRC_ON, new int[]{117});
    }

    private Boolean readRearDefrost() {
        return readBoolAny(CarProps.HVAC_REAR_DEFROSTER_ON, new int[]{2});
    }

    private Boolean readFrontMaxDefrost() {
        return readBoolAny(CarProps.HVAC_FRONT_MAX_DEFROST_ON, new int[]{1});
    }

    private Boolean readWindshieldHeat() {
        return readBoolAny(CarProps.HVAC_WINDSHIELD_HEAT, new int[]{117});
    }

    public static Float readTargetTemp(VehicleState state) {
        int id = CarProps.HVAC_TEMP_AOSP;
        for (int area : state.areas(id, CarProps.HVAC_AREAS)) {
            Float value = state.floatValue(id, area);
            if (value != null && value >= 16f && value <= 32f) return value;
        }
        return null;
    }

    private int capabilityType(int id) {
        CapabilityRegistry caps = gateway.capabilities();
        if (caps == null) return 0;
        CapabilityRegistry.Config config = caps.get(id);
        return config == null ? 0 : config.type;
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) return i;
        }
        return -1;
    }
}
