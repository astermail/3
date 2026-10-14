package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Freecam;
import com.gaspoweredcake.client33.utils.combat.CombatTargets;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import com.gaspoweredcake.client33.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class SilentAura extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").description("Attack distance; server reach rules still apply.").defaultValue(3).range(1, 6).build());
    private final Setting<Double> fov = sg.add(new DoubleSetting.Builder().name("fov").description("Target field of view.").defaultValue(70).range(5, 180).build());
    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder().name("degrees-per-tick").description("Maximum server aim change per tick.").defaultValue(12).range(1, 90).build());
    private final Setting<Boolean> held = sg.add(new BoolSetting.Builder().name("hold-attack").description("Require physically held attack.").defaultValue(true).build());
    private Player target;
    private double yaw, pitch;
    private boolean tracking;
    private long generation;
    public SilentAura() { super(Categories.Combat, "silent-aura", "Visible-player aura using bounded server rotations without moving your camera. No detection guarantees.", "SilentAura"); }
    @Override public void onActivate() { generation++; tracking = false; target = null; }
    @Override public void onDeactivate() { generation++; tracking = false; target = null; }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null || mc.player.isUsingItem()
            || Modules.get().isActive(KillAura.class) || Modules.get().isActive(Freecam.class) || com.gaspoweredcake.client33.utils.player.PotionUse.anyBusy()
            || held.get() && !Input.isPressed(mc.options.keyAttack)) { tracking = false; target = null; return; }
        target = CombatTargets.select(range.get(), fov.get());
        if (target == null) { tracking = false; return; }
        if (!tracking) { yaw = mc.player.getYRot(); pitch = mc.player.getXRot(); tracking = true; }
        var at = CombatTargets.point(target);
        double desiredYaw = Rotations.getYaw(at), desiredPitch = Rotations.getPitch(at);
        yaw = CombatTargets.step(yaw, desiredYaw, speed.get());
        pitch = Math.clamp(CombatTargets.step(pitch, desiredPitch, speed.get()), -90, 90);
        long session = generation;
        Player selected = target;
        var owner = mc.player;
        var world = mc.level;
        boolean aligned = Math.abs(CombatTargets.step(yaw, desiredYaw, 360) - yaw) < 2 && Math.abs(pitch - desiredPitch) < 2;
        Rotations.rotate(yaw, pitch, 5, () -> {
            if (session != generation || target != selected || !isActive() || mc.player != owner || mc.level != world || !aligned || mc.gui.screen() != null || owner.isUsingItem()
                || held.get() && !Input.isPressed(mc.options.keyAttack) || !selected.isAlive() || !owner.hasLineOfSight(selected)
                || !com.gaspoweredcake.client33.systems.friends.Friends.get().shouldAttack(selected)
                || owner.getAttackStrengthScale(0) < 1 || selected.getBoundingBox().distanceToSqr(owner.getEyePosition()) > range.get() * range.get()) return;
            mc.gameMode.attack(owner, selected);
            owner.swing(InteractionHand.MAIN_HAND);
        });
    }
    @Override public String getInfoString() { return target == null ? "Waiting" : target.getName().getString(); }
}
