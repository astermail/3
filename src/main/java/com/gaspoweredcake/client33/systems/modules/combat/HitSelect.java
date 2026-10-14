package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.entity.player.DoAttackEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class HitSelect extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> cooldown = sg.add(new DoubleSetting.Builder().name("minimum-cooldown").description("Hold back manual attacks until this fraction of weapon cooldown.").defaultValue(0.9).range(0, 1).build());
    private final Setting<Boolean> hurt = sg.add(new BoolSetting.Builder().name("skip-hurt-targets").description("Wait until the target's visible damage immunity ends.").defaultValue(false).build());
    public HitSelect() { super(Categories.Combat, "hit-select", "Paces manual entity attacks using cooldown and optional target hurt time.", "HitSelect"); }
    @EventHandler private void attack(DoAttackEvent event) {
        if (mc.player == null || !(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity entity)) return;
        if (mc.player.getAttackStrengthScale(0) < cooldown.get() || hurt.get() && entity.hurtTime > 0) event.cancel();
    }
}
