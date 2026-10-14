package com.gaspoweredcake.client33.utils.render;

public final class RadarGeometry {
    public record Point(double x, double y, boolean clipped) {}
    private RadarGeometry() {}
    public static Point project(double dx, double dz, double yaw, double range, double radius) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(yaw) || range <= 0 || radius <= 0) return new Point(0, 0, true);
        double angle = Math.toRadians(yaw);
        double x = (-dx * Math.cos(angle) - dz * Math.sin(angle)) / range * radius;
        double y = (dx * Math.sin(angle) - dz * Math.cos(angle)) / range * radius;
        double length = Math.hypot(x, y);
        boolean clipped = length > radius;
        if (clipped) { x *= radius / length; y *= radius / length; }
        return new Point(x, y, clipped);
    }
}
