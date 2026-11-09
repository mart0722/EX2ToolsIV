package android.car;

import android.content.Context;

/**
 * Compile-only stub for the Automotive Car API.
 * The real class is provided by the head unit firmware at runtime.
 */
public final class Car {
    private Car() {
    }

    public static Car createCar(Context context) {
        throw new UnsupportedOperationException("stub");
    }

    public Object getCarManager(String serviceName) {
        throw new UnsupportedOperationException("stub");
    }

    public void disconnect() {
        throw new UnsupportedOperationException("stub");
    }
}
