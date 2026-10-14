package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.combat.CombatTargets;
import com.gaspoweredcake.client33.utils.player.*;
import meteordevelopment.orbit.EventHandler;

public class ThrowDebuff extends Module {
    private final Setting<Double> range = settings.getDefaultGroup().add(new DoubleSetting.Builder().name("range").description("Visible target distance.").defaultValue(5.5).range(4, 8).build());
    private final PotionUse use = new PotionUse();
    private boolean started;
    public ThrowDebuff() { super(Categories.Combat, "throw-debuff", "Bind to throw one harmful splash potion toward a visible enemy. Friends are excluded.", "ThrowDebuff"); }
    @Override public void onActivate() { started = false; }
    @Override public void onDeactivate() { use.stop(); }
    @EventHandler private void leave(GameLeftEvent event) { use.stop(); disable(); }
    @EventHandler private void tick(TickEvent.Pre event) {
        use.tick();
        if (started) { if (!use.busy()) disable(); return; }
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || PotionUse.anyBusy()) { disable(); return; }
        var target = CombatTargets.select(range.get(), 90);
        var found = InvUtils.findInHotbar(PotionUse::debuff);
        if (target == null || !found.found() || mc.player.distanceTo(target) < 4) { warning("Need a harmful splash potion and a visible enemy at least 4 blocks away."); disable(); return; }
        for (var player : mc.level.players()) if ((player == mc.player || com.gaspoweredcake.client33.systems.friends.Friends.get().isFriend(player)) && player.distanceTo(target) < 4) {
            warning("A friend or you are too close to the splash area."); disable(); return;
        }
        var point = target.position().add(0, 0.2, 0);
        var difference = point.subtract(mc.player.getEyePosition().add(0, -0.1, 0));
        var motion = mc.player.getKnownMovement();
        var aim = com.gaspoweredcake.client33.utils.entity.simulator.ThrowAim.solve(difference.x, difference.y, difference.z,
            0.5, -20, 0.05, motion.x, mc.player.onGround() ? 0 : motion.y, motion.z, 60);
        var simulator = new com.gaspoweredcake.client33.utils.entity.simulator.ProjectileEntitySimulator();
        var stack = found.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(found.slot());
        boolean valid = false;
        if (aim.error() <= 1 && simulator.setThrow(mc.player, stack, aim.yaw(), aim.pitch())) for (int i = 0; i < 60; i++) {
            var step = simulator.tick();
            if (!step.shouldStop) continue;
            for (var hit : step.hitResults) if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getLocation().distanceTo(point) < 2) {
                boolean clear = true;
                for (var player : mc.level.players()) if ((player == mc.player || com.gaspoweredcake.client33.systems.friends.Friends.get().isFriend(player))
                    && player.position().distanceTo(hit.getLocation()) < 4) clear = false;
                valid |= clear;
            }
            break;
        }
        if (!valid) { warning("No clear potion path to the enemy outside the friendly splash area."); disable(); return; }
        started = use.start(found, aim.yaw(), aim.pitch());
        if (!started) disable();
    }
}
