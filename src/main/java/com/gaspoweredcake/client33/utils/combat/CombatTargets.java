package com.gaspoweredcake.client33.utils.combat;

import com.gaspoweredcake.client33.systems.friends.Friends;
import com.gaspoweredcake.client33.utils.entity.fakeplayer.FakePlayerEntity;
import com.gaspoweredcake.client33.utils.player.Rotations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import static com.gaspoweredcake.client33.Client33.mc;

/** Visible player targeting shared by manual aim assistance and aura. */
public final class CombatTargets {
    private CombatTargets() {}
    public static Player select(double range, double fov) {
        if (mc.player == null || mc.level == null) return null;
        Player best = null;
        double score = Double.POSITIVE_INFINITY;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || player.isCreative()
                || !Friends.get().shouldAttack(player) || !mc.player.hasLineOfSight(player)
                || player instanceof FakePlayerEntity fake && fake.noHit) continue;
            Vec3 at = point(player);
            if (player.getBoundingBox().distanceToSqr(mc.player.getEyePosition()) > range * range) continue;
            double yaw = Math.abs(Mth.wrapDegrees(Rotations.getYaw(at) - mc.player.getYRot()));
            if (yaw > fov / 2) continue;
            double next = yaw + mc.player.distanceTo(player) * 0.1;
            if (next < score) { score = next; best = player; }
        }
        return best;
    }
    public static Vec3 point(Player player) { return player.position().add(0, player.getBbHeight() * 0.75, 0); }
    public static double step(double current, double desired, double maximum) {
        double difference = (desired - current) % 360;
        if (difference >= 180) difference -= 360;
        if (difference < -180) difference += 360;
        return current + Math.clamp(difference, -maximum, maximum);
    }
}
