package com.gaspoweredcake.client33.utils.render;

import com.gaspoweredcake.client33.utils.render.color.Color;

/** Continuous cyan/violet/rose cycle shared by the GUI and combat atmosphere. */
public final class EuphoricPalette {
    private static final int[][] STOPS = {{77, 224, 255}, {156, 118, 255}, {255, 103, 195}};
    private EuphoricPalette() {}
    public static Color sample(double phase, int alpha) {
        if (!Double.isFinite(phase)) phase = 0;
        double position = (phase - Math.floor(phase)) * STOPS.length;
        int index = (int) position;
        double mix = position - index;
        mix = mix * mix * (3 - 2 * mix);
        int[] a = STOPS[index], b = STOPS[(index + 1) % STOPS.length];
        return new Color((int) Math.round(a[0] + (b[0] - a[0]) * mix), (int) Math.round(a[1] + (b[1] - a[1]) * mix),
            (int) Math.round(a[2] + (b[2] - a[2]) * mix), Math.clamp(alpha, 0, 255));
    }
}
