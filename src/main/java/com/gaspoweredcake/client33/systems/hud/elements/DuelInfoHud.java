package com.gaspoweredcake.client33.systems.hud.elements;

import com.gaspoweredcake.client33.systems.hud.*;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.utils.combat.DuelTelemetry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import java.util.ArrayList;
import java.util.List;
import static com.gaspoweredcake.client33.Client33.mc;

public class DuelInfoHud extends HudElement {
    public static final HudElementInfo<DuelInfoHud> INFO = new HudElementInfo<>(Hud.GROUP, "duel-info", "Session melee hits from server damage events and observed potion uses for you and your last opponent.", DuelInfoHud::new);
    private final Setting<com.gaspoweredcake.client33.utils.misc.Keybind> reset = settings.getDefaultGroup().add(new KeybindSetting.Builder().name("reset-session").description("Reset counts before a duel.").action(DuelTelemetry::reset).build());
    public DuelInfoHud() { super(INFO); }
    private List<String> lines() {
        List<String> lines = new ArrayList<>(); lines.add("Duel / observed hits & potions");
        if (mc.player != null) { var s = DuelTelemetry.stats(mc.player); lines.add("You: " + s.hits() + " hits / " + s.potions() + " pots"); }
        var player = DuelTelemetry.opponent();
        if (player != null) { var s = DuelTelemetry.stats(player); lines.add(s.name() + ": " + s.hits() + " hits / " + s.potions() + " pots"); }
        return lines;
    }
    @Override public void tick(HudRenderer renderer) {
        var lines = lines(); setSize(lines.stream().mapToDouble(s -> renderer.textWidth(s, true)).max().orElse(0) + 12, lines.size() * renderer.textHeight(true) + 12);
    }
    @Override public void render(HudRenderer renderer) {
        renderer.quad(x, y, getWidth(), getHeight(), new Color(12, 16, 24, 200)); double next = y + 6;
        for (String line : lines()) { renderer.text(line, x + 6, next, Color.WHITE, true); next += renderer.textHeight(true); }
    }
}
