package com.ex2tools.iv.vehicle;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.ex2tools.iv.LogStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слой команд. В dry-run только проверяет и логирует; при apply пишет
 * через VhalGateway, читает обратно и сверяет результат.
 */
public final class CommandDispatcher {

    public enum Status {
        DRY_RUN,
        SENT,
        APPLIED,
        REJECTED,
        UNSUPPORTED
    }

    public static final class Command {
        public final int id;
        public final int area;
        public final PropertySpec.Type type;
        public final Object value;

        public Command(int id, int area, PropertySpec.Type type, Object value) {
            this.id = id;
            this.area = area;
            this.type = type;
            this.value = value;
        }
    }

    public static final class Result {
        public final Status status;
        public final String message;

        Result(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public boolean ok() {
            return status == Status.APPLIED || status == Status.SENT
                    || status == Status.DRY_RUN;
        }
    }

    private final VhalGateway gateway;
    private final boolean readOnly;
    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();

    public CommandDispatcher(VhalGateway gateway, boolean readOnly) {
        this.gateway = gateway;
        this.readOnly = readOnly;
    }

    public Result submit(Command command) {
        return submit(command, true);
    }

    /** Send without polling read-back for interactive controls; state events confirm later. */
    public Result submit(Command command, boolean awaitConfirmation) {
        if (command == null) {
            return new Result(Status.REJECTED, "empty command");
        }

        CapabilityRegistry caps = gateway.capabilities();
        if (caps != null && !caps.has(command.id)) {
            String unsupported = "unsupported " + VehicleProperties.key(command.id);
            log(unsupported);
            return new Result(Status.UNSUPPORTED, unsupported);
        }

        String text = VehicleProperties.key(command.id)
                + " area=" + command.area + " value=" + command.value;

        if (readOnly) {
            log("dry-run: " + text);
            return new Result(Status.DRY_RUN, "read-only: " + text);
        }

        long now = SystemClock.elapsedRealtime();
        String key = command.id + ":" + command.area;
        Long last = lastWrite.get(key);
        if (last != null && now - last < 120L) {
            return new Result(Status.REJECTED, "rate limit: " + text);
        }
        lastWrite.put(key, now);

        boolean written;
        switch (command.type) {
            case INT:
                written = gateway.writeInt(command.id, command.area, asInt(command.value));
                break;
            case FLOAT:
                written = gateway.writeFloat(command.id, command.area, asFloat(command.value));
                break;
            case BOOLEAN:
                written = gateway.writeBool(command.id, command.area, asBool(command.value));
                break;
            default:
                return new Result(Status.REJECTED, "unsupported type: " + text);
        }

        if (!written) {
            log("rejected write: " + text);
            return new Result(Status.REJECTED, "write failed: " + text);
        }

        if (!awaitConfirmation) {
            log("sent: " + text);
            return new Result(Status.SENT, "sent: " + text);
        }

        Object readBack = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            readBack = readBack(command);
            if (matches(command, readBack)) {
                log("applied: " + text + " read=" + readBack);
                return new Result(Status.APPLIED, "applied: " + text);
            }
        }

        log("sent: " + text + " read-back=" + readBack + " (delayed)");
        return new Result(Status.SENT, "sent: " + text);
    }

    private Object readBack(Command command) {
        switch (command.type) {
            case INT: {
                int value = gateway.readInt(command.id, command.area);
                if (value != Integer.MIN_VALUE) return value;
                return gateway.readFloat(command.id, command.area);
            }
            case FLOAT: {
                float value = gateway.readFloat(command.id, command.area);
                if (!Float.isNaN(value)) return value;
                return gateway.readInt(command.id, command.area);
            }
            case BOOLEAN: {
                Boolean value = gateway.readBool(command.id, command.area);
                if (value != null) return value;
                int raw = gateway.readInt(command.id, command.area);
                return raw == Integer.MIN_VALUE ? null : raw != 0;
            }
            default:
                return null;
        }
    }

    private boolean matches(Command command, Object readBack) {
        if (readBack == null) return false;
        switch (command.type) {
            case INT:
                return readBack instanceof Number
                        && ((Number) readBack).intValue() == asInt(command.value);
            case FLOAT: {
                if (!(readBack instanceof Number)) return false;
                float diff = Math.abs(((Number) readBack).floatValue() - asFloat(command.value));
                return diff < 0.15f;
            }
            case BOOLEAN:
                if (readBack instanceof Boolean) {
                    return ((Boolean) readBack) == asBool(command.value);
                }
                return readBack instanceof Number
                        && ((((Number) readBack).intValue() != 0) == asBool(command.value));
            default:
                return false;
        }
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static float asFloat(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    private static boolean asBool(Object value) {
        return value instanceof Boolean ? (Boolean) value : asInt(value) != 0;
    }

    private void log(String message) {
        Log.i("CommandDispatcher", message);
        Context context = gateway.appContext();
        if (context != null) {
            LogStore.add(context, message);
        }
    }
}
