package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.combat.CombatTargets;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import com.gaspoweredcake.client33.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;

public class AimAssist extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").description("Visible target distance.").defaultValue(3).range(1, 6).build());
    private final Setting<Double> fov = sg.add(new DoubleSetting.Builder().name("fov").description("Horizontal targeting field of view in degrees.").defaultValue(70).range(5, 180).build());
    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder().name("degrees-per-tick").description("Maximum aim adjustment per tick.").defaultValue(3).range(0.1, 30).build());
    private final Setting<Boolean> held = sg.add(new BoolSetting.Builder().name("hold-attack").description("Only assist while physically holding attack.").defaultValue(true).build());
    public AimAssist() { super(Categories.Combat, "aim-assist", "Smoothly assists your aim toward visible nearby players.", "AimAssist"); }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || (held.get() && !Input.isPressed(mc.options.keyAttack))) return;
        var target = CombatTargets.select(range.get(), fov.get());
        if (target == null) return;
        var point = CombatTargets.point(target);
        mc.player.setYRot((float) CombatTargets.step(mc.player.getYRot(), Rotations.getYaw(point), speed.get()));
        mc.player.setXRot((float) Math.clamp(CombatTargets.step(mc.player.getXRot(), Rotations.getPitch(point), speed.get()), -90, 90));
    }
}
