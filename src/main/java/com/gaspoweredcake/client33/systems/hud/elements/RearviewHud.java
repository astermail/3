package com.gaspoweredcake.client33.systems.hud.elements;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.hud.*;
import com.gaspoweredcake.client33.utils.player.Rotations;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.util.Mth;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import static com.gaspoweredcake.client33.Client33.mc;

/** Rear awareness without a second world render or camera state mutation. */
public class RearviewHud extends HudElement {
    public static final HudElementInfo<RearviewHud> INFO = new HudElementInfo<>(Hud.GROUP, "rearview", "Lists nearby loaded players behind you. This is an awareness overlay, not a live camera.", RearviewHud::new);
    private final Setting<Double> range = settings.getDefaultGroup().add(new DoubleSetting.Builder().name("range").description("Rear awareness range.").defaultValue(32).range(4, 128).build());
    public RearviewHud() { super(INFO); }
    private List<Player> players() {
        if (mc.player == null || mc.level == null) return List.of();
        return mc.level.players().stream().filter(p -> p != mc.player && p.isAlive() && mc.player.distanceTo(p) <= range.get()
            && Math.abs(Mth.wrapDegrees(Rotations.getYaw(p) - mc.player.getYRot())) > 100)
            .sorted(java.util.Comparator.comparingDouble(mc.player::distanceTo)).limit(4).map(p -> (Player) p).toList();
    }
    @Override public void tick(HudRenderer renderer) {
        double width = renderer.textWidth("Behind: clear", true);
        var players = players();
        for (var player : players) width = Math.max(width, renderer.textWidth(label(player), true));
        setSize(width + 12, renderer.textHeight(true) * (1 + players.size()) + 12);
    }
    private String label(Player p) { return p.getName().getString() + "  " + Math.round(mc.player.distanceTo(p)) + "m"; }
    @Override public void render(HudRenderer renderer) {
        renderer.quad(x, y, getWidth(), getHeight(), new Color(12, 16, 24, 200));
        var players = players();
        renderer.text(players.isEmpty() ? "Behind: clear" : "Behind:", x + 6, y + 6, Color.WHITE, true);
        double next = y + 6 + renderer.textHeight(true);
        for (var player : players) { renderer.text(label(player), x + 6, next, PlayerUtils.getPlayerColor(player, Color.WHITE), true); next += renderer.textHeight(true); }
    }
}
