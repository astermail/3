package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.FallingBlockEntity;
import meteordevelopment.orbit.EventHandler;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;

public class PropHunt extends Module {
    private final Setting<Double> range = settings.getDefaultGroup().add(new DoubleSetting.Builder().name("range").description("Maximum prop candidate distance.").defaultValue(64).range(8, 256).build());
    private final Setting<SettingColor> color = settings.getDefaultGroup().add(new ColorSetting.Builder().name("color").description("Prop candidate outline color.").defaultValue(new SettingColor(255, 100, 210)).build());
    public PropHunt() { super(Categories.Render, "prop-hunt", "Outlines loaded falling-block and block-display prop candidates. Requires entity-based disguises; candidates are not confirmed players.", "PropHunt"); }
    @EventHandler private void render(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        int count = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof FallingBlockEntity || entity instanceof Display.BlockDisplay) || mc.player.distanceTo(entity) > range.get()) continue;
            if (++count > 256) break;
            var bounds = entity.getBoundingBox();
            if (bounds.getSize() < 0.1) bounds = new net.minecraft.world.phys.AABB(entity.blockPosition());
            event.renderer.box(bounds, color.get(), color.get(), ShapeMode.Lines, 0);
        }
    }
}
