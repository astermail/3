package com.gaspoweredcake.client33.utils.render;

import org.joml.Vector3d;

/** Perspective division with a finite boundary at the camera plane; coordinates remain in screen pixels. */
public final class ScreenProjection {
    private ScreenProjection() {}
    public static boolean project(double x, double y, double z, double w, double width, double height,
                                  boolean allowBehind, Vector3d result) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)
            || Math.abs(w) <= 0.00001 || (!allowBehind && w < 0)
            || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return false;
        double inverse = 0.5 / w;
        double screenX = (x * inverse + 0.5) * width;
        double screenY = (0.5 - y * inverse) * height;
        if (w < 0) { screenX = width - screenX; screenY = height - screenY; }
        double depth = allowBehind ? inverse : z * inverse + 0.5;
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY) || !Double.isFinite(depth)) return false;
        result.set(screenX, screenY, depth);
        return true;
    }
}
