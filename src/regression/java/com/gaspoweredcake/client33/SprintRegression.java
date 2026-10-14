package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.player.SprintInput;

public final class SprintRegression {
    public static void run() {
        check(SprintInput.moving(1, 0, false, false), "Forward requests strict sprint");
        check(SprintInput.moving(0.70710677F, 0.70710677F, false, false), "Normalized diagonal forward movement still requests sprint");
        check(SprintInput.moving(0.3F, 0.3F, false, false), "Movement is not rejected by the obsolete 0.8 threshold");
        check(!SprintInput.moving(0, 1, false, false) && !SprintInput.moving(-1, 0, false, false), "Strict does not request sideways or backwards sprint");
        check(SprintInput.moving(0, 1, true, false) && SprintInput.moving(-1, 0, true, false), "Rage permits directional requests");
        check(!SprintInput.moving(0, 0, false, true) && !SprintInput.moving(0, 0, true, false), "Stationary sprint is opt-in and Rage-only");
        check(SprintInput.moving(0, 0, true, true), "Explicit stationary Rage mode remains available");
        check(!SprintInput.moving(Float.NaN, 0, true, true) && !SprintInput.moving(1, Float.POSITIVE_INFINITY, false, false), "Invalid movement never requests sprint");
        System.out.println("Sprint 26.2 input regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
