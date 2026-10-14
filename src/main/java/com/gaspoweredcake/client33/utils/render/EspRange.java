package com.gaspoweredcake.client33.utils.render;

/** Full-height overlays use a horizontal radius across the dimension's loaded height. */
public final class EspRange {
    private EspRange() {}
    public static double squared(double dx, double dy, double dz, boolean fullHeight) {
        return dx * dx + dz * dz + (fullHeight ? 0 : dy * dy);
    }
}
