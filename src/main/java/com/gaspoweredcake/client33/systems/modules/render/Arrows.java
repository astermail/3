package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.Rotations;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;

public class Arrows extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").description("Maximum player distance.").defaultValue(128).range(8, 512).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").description("Arrow color; friends use the friend color.").defaultValue(new SettingColor(255, 210, 80)).build());
    public Arrows() { super(Categories.Render, "arrows", "Shows direction arrows for loaded players outside your screen."); }
    @EventHandler private void render(Render2DEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        double cx = event.screenWidth / 2.0, cy = event.screenHeight / 2.0;
        double rx = Math.max(20, cx - 32), ry = Math.max(20, cy - 32);
        Renderer2D.COLOR.begin();
        int drawn = 0;
        for (var player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || mc.player.distanceTo(player) > range.get()) continue;
            var screen = new Vector3d(player.getX(), player.getEyeY(), player.getZ());
            if (NametagUtils.to2D(screen, 1, false) && screen.x >= 0 && screen.x <= event.screenWidth && screen.y >= 0 && screen.y <= event.screenHeight) continue;
            if (++drawn > 64) break;
            double angle = Math.toRadians(Rotations.getYaw(player) - mc.gameRenderer.mainCamera().yRot() - 90);
            double dx = Math.cos(angle), dy = Math.sin(angle);
            double x = cx + dx * rx, y = cy + dy * ry;
            var c = PlayerUtils.getPlayerColor(player, color.get());
            Renderer2D.COLOR.triangle(x + dx * 10, y + dy * 10, x - dx * 7 - dy * 6, y - dy * 7 + dx * 6,
                x - dx * 7 + dy * 6, y - dy * 7 - dx * 6, c);
        }
        Renderer2D.COLOR.render();
    }
}
