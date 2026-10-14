package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.player.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.Direction;
import meteordevelopment.orbit.EventHandler;

public class MLG extends Module {
    private final Setting<Double> fall = settings.getDefaultGroup().add(new DoubleSetting.Builder().name("minimum-fall").description("Minimum fall distance before placing water.").defaultValue(3).range(2, 20).build());
    private boolean pending;
    private long generation;
    private long queuedAt;
    private int timer;
    private Object world;
    public MLG() { super(Categories.Movement, "mlg", "Places a hotbar water bucket below you during a fall when the landing surface enters normal block reach."); }
    private void reset() { generation++; pending = false; timer = 0; world = mc.level; }
    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }
    @EventHandler private void leave(GameLeftEvent event) { reset(); }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (world != mc.level) reset();
        if (pending && com.gaspoweredcake.client33.utils.combat.CombatTelemetry.now() - queuedAt > 300) { pending = false; generation++; }
        if (mc.player == null || mc.level == null || mc.gameMode == null || pending || mc.gui.screen() != null
            || mc.player.isUsingItem() || PotionUse.anyBusy() || Modules.get().isActive(NoFall.class)) return;
        if (timer > 0) { timer--; return; }
        if (mc.player.onGround() || mc.player.isInWater() || mc.player.isFallFlying() || mc.player.getDeltaMovement().y >= 0
            || mc.player.fallDistance < fall.get() || mc.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.WATER_EVAPORATES)) return;
        var found = InvUtils.findInHotbar(Items.WATER_BUCKET);
        if (!found.found()) return;
        var owner = mc.player; var level = mc.level;
        var from = owner.getEyePosition();
        double reach = Math.min(4.5, owner.blockInteractionRange());
        var end = from.add(owner.getDeltaMovement().x, -reach, owner.getDeltaMovement().z);
        var hit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP || from.distanceTo(hit.getLocation()) > reach) return;
        int original = owner.getInventory().getSelectedSlot();
        long session = generation;
        queuedAt = com.gaspoweredcake.client33.utils.combat.CombatTelemetry.now();
        pending = true; timer = 5;
        Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), 100, () -> {
            if (session != generation) return;
            pending = false;
            if (!isActive() || mc.player != owner || mc.level != level || mc.gui.screen() != null || owner.isUsingItem()
                || owner.getInventory().getSelectedSlot() != original || from.distanceTo(owner.getEyePosition()) > 1.5) return;
            var stack = found.isOffhand() ? owner.getOffhandItem() : owner.getInventory().getItem(found.slot());
            if (!stack.is(Items.WATER_BUCKET)) return;
            if (!found.isOffhand()) InvUtils.swap(found.slot(), false);
            mc.gameMode.useItem(owner, found.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!found.isOffhand() && owner.getInventory().getSelectedSlot() == found.slot()) InvUtils.swap(original, false);
        });
    }
}
