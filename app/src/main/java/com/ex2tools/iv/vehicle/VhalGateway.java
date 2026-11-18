package com.ex2tools.iv.vehicle;

import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.util.Log;

import com.ex2tools.iv.LogStore;

/** Единственная точка чтения VHAL. Записи здесь нет по построению. */
public final class VhalGateway {

    private static volatile VhalGateway instance;

    private Car car;
    private CarPropertyManager cpm;
    private Context appContext;
    private CapabilityRegistry capabilities;

    public static VhalGateway get() {
        VhalGateway local = instance;
        if (local == null) {
            synchronized (VhalGateway.class) {
                local = instance;
                if (local == null) {
                    local = new VhalGateway();
                    instance = local;
                }
            }
        }
        return local;
    }

    public synchronized boolean ensure(Context context) {
        if (cpm != null) return true;
        try {
            appContext = context.getApplicationContext();
            if (car == null) {
                car = Car.createCar(appContext);
            }
            if (car == null) return false;
            Object manager = car.getCarManager("property");
            if (manager instanceof CarPropertyManager) {
                cpm = (CarPropertyManager) manager;
                capabilities = new CapabilityRegistry(cpm);
                LogStore.add(appContext, "VhalGateway connected: "
                        + capabilities.count() + " properties");
                return true;
            }
            LogStore.add(appContext, "VhalGateway: property manager unavailable");
        } catch (Throwable t) {
            LogStore.add(appContext, "VhalGateway connect failed: " + t);
        }
        return false;
    }

    public CarPropertyManager cpm() {
        return cpm;
    }

    public CapabilityRegistry capabilities() {
        return capabilities;
    }

    public Context appContext() {
        return appContext;
    }

    public boolean isConnected() {
        return cpm != null;
    }

    public boolean writeInt(int id, int area, int value) {
        try {
            if (cpm == null) return false;
            cpm.setIntProperty(id, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean writeFloat(int id, int area, float value) {
        try {
            if (cpm == null) return false;
            cpm.setFloatProperty(id, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean writeBool(int id, int area, boolean value) {
        try {
            if (cpm == null) return false;
            cpm.setBooleanProperty(id, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public int readInt(int id, int area) {
        try {
            return cpm == null ? Integer.MIN_VALUE : cpm.getIntProperty(id, area);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    public float readFloat(int id, int area) {
        if (cpm == null) return Float.NaN;
        try {
            return cpm.getFloatProperty(id, area);
        } catch (Throwable ignored) {
        }
        try {
            CarPropertyValue<?> value = cpm.getProperty(id, area);
            Object raw = value == null ? null : value.getValue();
            if (raw instanceof Number) return ((Number) raw).floatValue();
        } catch (Throwable ignored) {
        }
        return Float.NaN;
    }

    public Boolean readBool(int id, int area) {
        try {
            return cpm == null ? null : cpm.getBooleanProperty(id, area);
        } catch (Throwable t) {
            return null;
        }
    }

    public CarPropertyValue<?> readValue(int id, int area) {
        try {
            return cpm == null ? null : cpm.getProperty(id, area);
        } catch (Throwable t) {
            return null;
        }
    }

    public String describe() {
        CapabilityRegistry caps = capabilities;
        return "gateway=" + (cpm != null ? "connected" : "offline")
                + " properties=" + (caps == null ? 0 : caps.count());
    }

    public synchronized void close() {
        try {
            if (car != null) car.disconnect();
        } catch (Throwable ignored) {
        }
        car = null;
        cpm = null;
        capabilities = null;
        Log.i("VhalGateway", "closed");
    }
}
