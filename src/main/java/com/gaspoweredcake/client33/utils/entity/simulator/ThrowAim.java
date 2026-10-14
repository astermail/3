package com.gaspoweredcake.client33.utils.entity.simulator;

/** Airborne throwable model used to choose an aim; collision is verified by ProjectileEntitySimulator. */
public final class ThrowAim {
    public record Aim(double yaw, double pitch, double error) {}
    private ThrowAim() {}
    public static Aim solve(double dx, double dy, double dz, double speed, double roll, double gravity,
                            double vx, double vy, double vz, int ticks) {
        if (!Double.isFinite(dx + dy + dz + vx + vy + vz) || speed <= 0 || ticks < 1) return new Aim(0, 0, Double.POSITIVE_INFINITY);
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        Aim best = new Aim(yaw, 0, Double.POSITIVE_INFINITY);
        for (double offset = -8; offset <= 8; offset += 2) for (double pitch = -80; pitch <= 80; pitch += 2) {
            double error = error(dx, dy, dz, yaw + offset, pitch, speed, roll, gravity, vx, vy, vz, ticks);
            if (error < best.error) best = new Aim(yaw + offset, pitch, error);
        }
        return best;
    }
    private static double error(double dx, double dy, double dz, double yaw, double pitch, double speed, double roll,
                                double gravity, double vx, double vy, double vz, int ticks) {
        double p = Math.toRadians(pitch), a = Math.toRadians(yaw);
        double x = -Math.sin(a) * Math.cos(p), y = -Math.sin(Math.toRadians(pitch + roll)), z = Math.cos(a) * Math.cos(p);
        double length = Math.sqrt(x*x + y*y + z*z);
        double ux = x / length * speed + vx, uy = y / length * speed + vy, uz = z / length * speed + vz;
        double px = 0, py = 0, pz = 0, best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < ticks; i++) {
            uy -= gravity; ux *= 0.99; uy *= 0.99; uz *= 0.99;
            px += ux; py += uy; pz += uz;
            double distance = Math.sqrt((px-dx)*(px-dx) + (py-dy)*(py-dy) + (pz-dz)*(pz-dz));
            best = Math.min(best, distance);
        }
        return best;
    }
}
