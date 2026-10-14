package com.gaspoweredcake.client33.systems.hud.elements;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.hud.*;
import com.gaspoweredcake.client33.utils.render.RadarGeometry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import static com.gaspoweredcake.client33.Client33.mc;

public class RadarHud extends HudElement {
    public static final HudElementInfo<RadarHud> INFO = new HudElementInfo<>(Hud.GROUP, "radar", "Rotating 2D radar of loaded players; above and below markers indicate height.", RadarHud::new);
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> size = sg.add(new IntSetting.Builder().name("size").description("Radar size in pixels.").defaultValue(150).range(80, 350).build());
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").description("Horizontal radar range in blocks.").defaultValue(64).range(8, 256).build());
    public RadarHud() { super(INFO); }
    @Override public void tick(HudRenderer renderer) { setSize(size.get(), size.get()); }
    @Override public void render(HudRenderer renderer) {
        double radius = size.get() / 2.0 - 8, cx = x + size.get() / 2.0, cy = y + size.get() / 2.0;
        renderer.quad(x, y, size.get(), size.get(), new Color(12, 16, 24, 200));
        renderer.line(cx - radius, cy, cx + radius, cy, Color.GRAY);
        renderer.line(cx, cy - radius, cx, cy + radius, Color.GRAY);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            renderer.line(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, Color.GRAY);
        }
        renderer.triangle(cx, cy - 5, cx - 4, cy + 4, cx + 4, cy + 4, Color.WHITE);
        if (mc.player == null || mc.level == null) return;
        int drawn = 0;
        for (var player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || ++drawn > 128) continue;
            var point = RadarGeometry.project(player.getX() - mc.player.getX(), player.getZ() - mc.player.getZ(), mc.player.getYRot(), range.get(), radius - 4);
            if (point.clipped()) continue;
            double px = cx + point.x(), py = cy + point.y();
            Color color = PlayerUtils.getPlayerColor(player, new Color(255, 95, 100));
            double height = player.getY() - mc.player.getY();
            if (Math.abs(height) > 3) {
                double sign = height > 0 ? -1 : 1;
                renderer.triangle(px, py + sign * 4, px - 3, py - sign * 3, px + 3, py - sign * 3, color);
            } else renderer.quad(px - 2, py - 2, 4, 4, color);
        }
    }
}
