package com.gaspoweredcake.client33.utils.storage;

import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Geometry and opacity shared by block and vehicle storage overlays. */
public final class StorageRenderMath {
    private StorageRenderMath() {}

    public static AABB bounds(BlockPos pos, BlockPos partner, boolean chest) {
        double minX = pos.getX(), minZ = pos.getZ();
        double maxX = minX + 1, maxZ = minZ + 1;
        if (partner != null) {
            minX = Math.min(minX, partner.getX()); minZ = Math.min(minZ, partner.getZ());
            maxX = Math.max(maxX, partner.getX() + 1); maxZ = Math.max(maxZ, partner.getZ() + 1);
        }
        double inset = chest ? 1.0 / 16 : 0;
        return new AABB(minX + inset, pos.getY(), minZ + inset, maxX - inset, pos.getY() + 1 - inset * 2, maxZ - inset);
    }

    public static double opacity(double distance, double nearFade, double limit, double farFade) {
        return opacity(distance, distance, nearFade, limit, farFade);
    }

    public static double opacity(double distance, double rangeDistance, double nearFade, double limit, double farFade) {
        if (!Double.isFinite(distance) || !Double.isFinite(rangeDistance) || distance < 0 || rangeDistance < 0 || limit > 0 && rangeDistance >= limit) return 0;
        double opacity = nearFade > 0 ? smooth(distance / nearFade) : 1;
        if (limit > 0 && farFade > 0) opacity *= smooth((limit - rangeDistance) / Math.min(farFade, limit));
        return opacity;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static Color faded(Color base, double opacity) {
        return new Color(base).a((int) Math.round(base.a * Math.clamp(opacity, 0, 1)));
    }
}
