package com.ex2tools.iv.car;

import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import com.ex2tools.iv.LogStore;

public final class CarConnection {

    private static volatile CarConnection instance;

    private Car car;
    private CarPropertyManager cpm;

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

    public synchronized boolean ensure(Context context) {
        if (cpm != null) return true;
        try {
            if (car == null) {
                car = Car.createCar(context.getApplicationContext());
            }
            if (car == null) return false;
            Object manager = car.getCarManager("property");
            if (manager instanceof CarPropertyManager) {
                cpm = (CarPropertyManager) manager;
                LogStore.add(context, "Car property manager connected");
                return true;
            }
            LogStore.add(context, "Car property manager unavailable");
        } catch (Throwable t) {
            LogStore.add(context, "Car connect failed: " + t);
        }
        return false;
    }

    public synchronized CarPropertyManager cpm() {
        return cpm;
    }

    public boolean isConnected() {
        return cpm != null;
    }

    public int readInt(int property, int area) {
        try {
            return cpm == null ? Integer.MIN_VALUE : cpm.getIntProperty(property, area);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    public float readFloat(int property, int area) {
        if (cpm == null) return Float.NaN;
        try {
            return cpm.getFloatProperty(property, area);
        } catch (Throwable ignored) {
        }
        try {
            CarPropertyValue<?> value = cpm.getProperty(property, area);
            Object raw = value == null ? null : value.getValue();
            if (raw instanceof Number) return ((Number) raw).floatValue();
        } catch (Throwable ignored) {
        }
        return Float.NaN;
    }

    public boolean writeInt(int property, int area, int value) {
        try {
            if (cpm == null) return false;
            cpm.setIntProperty(property, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean writeFloat(int property, int area, float value) {
        try {
            if (cpm == null) return false;
            cpm.setFloatProperty(property, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public Boolean readBool(int property, int area) {
        try {
            return cpm == null ? null : cpm.getBooleanProperty(property, area);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean writeBool(int property, int area, boolean value) {
        try {
            if (cpm == null) return false;
            cpm.setBooleanProperty(property, area, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public synchronized void close() {
        try {
            if (car != null) car.disconnect();
        } catch (Throwable ignored) {
        }
        car = null;
        cpm = null;
    }
}
