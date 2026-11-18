package com.ex2tools.iv.car;

import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import com.ex2tools.iv.ReadOnly;
import com.ex2tools.iv.vehicle.VhalGateway;

/**
 * Переходная обёртка. Все чтения идут через VhalGateway; записи запрещены
 * в read-only сборке. Новый код должен использовать VhalGateway напрямую.
 */
public final class CarConnection {

    private static volatile CarConnection instance;

    private CarConnection() {
    }

    public static CarConnection get() {
        CarConnection local = instance;
        if (local == null) {
            synchronized (CarConnection.class) {
                local = instance;
                if (local == null) {
                    local = new CarConnection();
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean ensure(Context context) {
        return VhalGateway.get().ensure(context);
    }

    public CarPropertyManager cpm() {
        return VhalGateway.get().cpm();
    }

    public boolean isConnected() {
        return VhalGateway.get().isConnected();
    }

    public int readInt(int property, int area) {
        return VhalGateway.get().readInt(property, area);
    }

    public float readFloat(int property, int area) {
        return VhalGateway.get().readFloat(property, area);
    }

    public Boolean readBool(int property, int area) {
        return VhalGateway.get().readBool(property, area);
    }

    private static volatile boolean readOnlyLogged;

    private static void logReadOnly(int property, int area) {
        if (readOnlyLogged) return;
        readOnlyLogged = true;
        android.util.Log.i("EX2ReadOnly",
                "write blocked: 0x" + Integer.toHexString(property) + " area " + area);
    }

    public boolean writeInt(int property, int area, int value) {
        if (ReadOnly.ENABLED) {
            logReadOnly(property, area);
            return false;
        }
        return false;
    }

    public boolean writeFloat(int property, int area, float value) {
        if (ReadOnly.ENABLED) {
            logReadOnly(property, area);
            return false;
        }
        return false;
    }

    public boolean writeBool(int property, int area, boolean value) {
        if (ReadOnly.ENABLED) {
            logReadOnly(property, area);
            return false;
        }
        return false;
    }

    public void close() {
        VhalGateway.get().close();
    }
}
