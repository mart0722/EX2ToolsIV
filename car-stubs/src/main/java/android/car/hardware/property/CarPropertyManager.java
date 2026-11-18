package android.car.hardware.property;

import android.car.hardware.CarPropertyValue;

/** Compile-only stub for the Automotive Car API. */
public final class CarPropertyManager {
    public CarPropertyManager() {
    }

    public int getIntProperty(int propertyId, int area) {
        throw new UnsupportedOperationException("stub");
    }

    public void setIntProperty(int propertyId, int area, int value) {
        throw new UnsupportedOperationException("stub");
    }

    public float getFloatProperty(int propertyId, int area) {
        throw new UnsupportedOperationException("stub");
    }

    public void setFloatProperty(int propertyId, int area, float value) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean getBooleanProperty(int propertyId, int area) {
        throw new UnsupportedOperationException("stub");
    }

    public void setBooleanProperty(int propertyId, int area, boolean value) {
        throw new UnsupportedOperationException("stub");
    }

    public CarPropertyValue getProperty(int propertyId, int area) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean registerCallback(CarPropertyEventCallback callback, int propertyId, float rateHz) {
        throw new UnsupportedOperationException("stub");
    }

    public void unregisterCallback(CarPropertyEventCallback callback) {
        throw new UnsupportedOperationException("stub");
    }

    public interface CarPropertyEventCallback {
        void onChangeEvent(CarPropertyValue value);

        void onErrorEvent(int propertyId, int zone);
    }
}
