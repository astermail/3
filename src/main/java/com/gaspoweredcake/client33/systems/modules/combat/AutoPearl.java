package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Freecam;
import com.gaspoweredcake.client33.utils.entity.simulator.*;
import com.gaspoweredcake.client33.utils.player.*;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.systems.friends.Friends;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;
import meteordevelopment.orbit.EventHandler;
import java.util.*;

public class AutoPearl extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").description("Maximum predicted pearl destination range.").defaultValue(48).range(8, 80).build());
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ms").description("Minimum interval between pearl throws.").defaultValue(1500).range(500, 5000).build());
    private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();
    private final Set<UUID> observed = new HashSet<>();
    private Object world;
    private long next;
    private boolean pending;
    private long generation;
    private long queuedAt;
    public AutoPearl() { super(Categories.Combat, "auto-pearl", "Follows visible enemy-owned pearls toward a predicted collision, after checking your own pearl path. Requires known projectile owners.", "AutoPearl"); }
    private void clear() { generation++; observed.clear(); world = mc.level; next = 0; pending = false; }
    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void leave(GameLeftEvent event) { clear(); }
    private Vec3 impact() {
        for (int i = 0; i < 120; i++) {
            var step = simulator.tick();
            if (step.shouldStop) {
                for (var result : step.hitResults) if (result.getType() != HitResult.Type.MISS) return result.getLocation();
                return null;
            }
        }
        return null;
    }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (world != mc.level) clear();
        if (pending && CombatTelemetry.now() - queuedAt > 300) { pending = false; generation++; }
        if (mc.player == null || mc.level == null || pending || mc.gui.screen() != null || mc.player.isUsingItem()
            || PotionUse.anyBusy() || Modules.get().isActive(Freecam.class) || CombatTelemetry.now() < next) return;
        Set<UUID> present = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof ThrownEnderpearl) present.add(entity.getUUID());
        observed.retainAll(present);
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl) || observed.contains(pearl.getUUID())
                || !(pearl.getOwner() instanceof Player enemy) || enemy == mc.player || !Friends.get().shouldAttack(enemy)
                || !mc.player.hasLineOfSight(pearl)) continue;
            var found = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!found.found()) return;
            var stack = found.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(found.slot());
            if (mc.player.getCooldowns().isOnCooldown(stack)) return;
            observed.add(pearl.getUUID());
            if (!simulator.set(pearl)) return;
            Vec3 destination = impact();
            if (destination == null || mc.player.position().distanceTo(destination) > range.get()) return;
            Vec3 from = mc.player.getEyePosition().add(0, -0.1, 0), difference = destination.subtract(from), motion = mc.player.getKnownMovement();
            var aim = ThrowAim.solve(difference.x, difference.y, difference.z, 1.5, 0, 0.03, motion.x, mc.player.onGround() ? 0 : motion.y, motion.z, 120);
            if (aim.error() > 1.5 || !simulator.setThrow(mc.player, stack, aim.yaw(), aim.pitch())) return;
            Vec3 ours = impact();
            if (ours == null || ours.distanceTo(destination) > 2.5) return;
            var owner = mc.player; var level = mc.level;
            int original = owner.getInventory().getSelectedSlot();
            long session = generation;
            queuedAt = CombatTelemetry.now();
            pending = true; next = CombatTelemetry.now() + delay.get();
            Rotations.rotate(aim.yaw(), aim.pitch(), 40, () -> {
                if (session != generation) return;
                pending = false;
                if (!isActive() || mc.player != owner || mc.level != level || mc.gui.screen() != null || owner.isUsingItem()
                    || PotionUse.anyBusy() || owner.getInventory().getSelectedSlot() != original || !pearl.isAlive()
                    || !Friends.get().shouldAttack(enemy)) return;
                var current = found.isOffhand() ? owner.getOffhandItem() : owner.getInventory().getItem(found.slot());
                if (!current.is(Items.ENDER_PEARL) || owner.getCooldowns().isOnCooldown(current)) return;
                if (!found.isOffhand()) InvUtils.swap(found.slot(), false);
                mc.gameMode.useItem(owner, found.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                if (!found.isOffhand() && owner.getInventory().getSelectedSlot() == found.slot()) InvUtils.swap(original, false);
            });
            return;
        }
    }
}
