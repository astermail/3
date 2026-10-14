package com.gaspoweredcake.client33.gui.utils;

import com.gaspoweredcake.client33.utils.render.color.Color;

/** Nonoverlapping rounded surface geometry shared by the GUI renderer and headless checks. */
public final class RoundedRect {
    private RoundedRect() {}
    public interface Sink {
        void quad(double x, double y, double width, double height, Color color);
        void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color);
    }
    public static void draw(Sink sink, double x, double y, double width, double height, double radius, Color color) {
        if (width <= 0 || height <= 0) return;
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) { sink.quad(x, y, width, height, color); return; }
        sink.quad(x + r, y, width - 2 * r, height, color);
        sink.quad(x, y + r, r, height - 2 * r, color);
        sink.quad(x + width - r, y + r, r, height - 2 * r, color);
        int segments = 12;
        for (int corner = 0; corner < 4; corner++) {
            double cx = x + (corner == 0 || corner == 3 ? r : width - r);
            double cy = y + (corner < 2 ? r : height - r);
            double start = Math.PI + corner * Math.PI / 2;
            for (int i = 0; i < segments; i++) {
                double a = start + i * Math.PI / (2 * segments), end = start + (i + 1) * Math.PI / (2 * segments);
                sink.triangle(cx, cy, cx + Math.cos(a) * r, cy + Math.sin(a) * r,
                    cx + Math.cos(end) * r, cy + Math.sin(end) * r, color);
            }
        }
    }
}
