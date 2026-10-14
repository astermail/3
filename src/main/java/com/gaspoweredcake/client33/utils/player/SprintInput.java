package com.gaspoweredcake.client33.utils.player;

/** 26.2 movement vectors normalize diagonals; forward input need not exceed 0.8. */
public final class SprintInput {
    private SprintInput() {}
    public static boolean moving(float forward, float strafe, boolean allDirections, boolean stationary) {
        if (!Float.isFinite(forward) || !Float.isFinite(strafe)) return false;
        if (!allDirections) return forward > 1.0E-5F;
        return stationary || Math.abs(forward) > 1.0E-5F || Math.abs(strafe) > 1.0E-5F;
    }
}
