package com.gaspoweredcake.client33.utils.movement;

import net.minecraft.world.phys.Vec3;

/** Flight control math in blocks/tick, independent of input devices and packet formats. */
public final class FlightMotion {
    private FlightMotion() {}

    public static Vec3 target(float yaw, int forward, int strafe, int vertical, double horizontalSpeed, double verticalSpeed) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians) * forward - Math.cos(radians) * strafe;
        double z = Math.cos(radians) * forward - Math.sin(radians) * strafe;
        double length = Math.hypot(x, z);
        if (length > 1) { x /= length; z /= length; }
        return new Vec3(x * horizontalSpeed, vertical * verticalSpeed, z * horizontalSpeed);
    }

    public static Vec3 approach(Vec3 previous, Vec3 target, double acceleration) {
        if (acceleration <= 0) return target;
        double dx = target.x - previous.x, dz = target.z - previous.z;
        double distance = Math.hypot(dx, dz);
        double ratio = distance > acceleration ? acceleration / distance : 1;
        double dy = Math.clamp(target.y - previous.y, -acceleration, acceleration);
        return new Vec3(previous.x + dx * ratio, previous.y + dy, previous.z + dz * ratio);
    }
}
