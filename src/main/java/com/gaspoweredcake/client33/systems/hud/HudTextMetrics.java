package com.gaspoweredcake.client33.systems.hud;

/** Rasterize above display resolution, then normalize once; never square the requested scale. */
public final class HudTextMetrics {
    private HudTextMetrics() {}
    public static int rasterHeight(double scale) {
        return (int) Math.clamp(Math.ceil(18 * scale * 1.5), 18, 96);
    }
    public static double drawScale(double requestedScale, int rasterHeight) {
        return 18 * requestedScale / rasterHeight;
    }
}
