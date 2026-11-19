package com.ex2tools.iv.climate;

/** Only the five modes already supported by this vehicle are selectable. */
public final class VentSelection {
    private VentSelection() {}

    public static int toggle(int current, int direction) {
        if (direction != 1 && direction != 2 && direction != 4) {
            throw new IllegalArgumentException("Invalid vent direction: " + direction);
        }
        if (current != 1 && current != 2 && current != 3 && current != 4 && current != 6) {
            return direction;
        }
        int next = current ^ direction;
        // Never send all-off or an unsupported combination (face+glass / all three).
        return next == 1 || next == 2 || next == 3 || next == 4 || next == 6
                ? next : direction;
    }
}
