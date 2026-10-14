package com.gaspoweredcake.client33.systems.hud.elements;

import com.gaspoweredcake.client33.systems.hud.*;
import com.gaspoweredcake.client33.utils.combat.DuelTelemetry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import java.util.Locale;

public class ReachDisplayHud extends HudElement {
    public static final HudElementInfo<ReachDisplayHud> INFO = new HudElementInfo<>(Hud.GROUP, "reach-display", "Eye-to-hitbox distance of your last local player attack attempt; does not confirm server damage.", ReachDisplayHud::new);
    public ReachDisplayHud() { super(INFO); }
    private String text() { return DuelTelemetry.hasAttack() ? String.format(Locale.ROOT, "Last reach: %.2fm", DuelTelemetry.reach()) : "Last reach: --"; }
    @Override public void tick(HudRenderer renderer) { setSize(renderer.textWidth(text(), true), renderer.textHeight(true)); }
    @Override public void render(HudRenderer renderer) { renderer.text(text(), x, y, Color.WHITE, true); }
}
