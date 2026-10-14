package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.entity.player.AttackEntityEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import meteordevelopment.orbit.EventHandler;

public class BackTrack extends MovementDelay {
    private final Setting<Integer> window = settings.getDefaultGroup().add(new IntSetting.Builder().name("combat-window-ms").description("Delay movement for this long after a local attack attempt.").defaultValue(500).range(50, 2000).build());
    private volatile long lastAttack = Long.MIN_VALUE / 2;
    public BackTrack() { super("back-track", "Temporarily delays your movement after attacking. Does not change server reach or rewind enemy positions.", "BackTrack"); }
    @EventHandler(priority = -1000) private void attack(AttackEntityEvent event) { if (!event.isCancelled()) lastAttack = CombatTelemetry.now(); }
    @Override protected boolean shouldDelay() { return mc.player != null && mc.player.isAlive() && mc.gui.screen() == null && CombatTelemetry.now() - lastAttack < window.get(); }
    @Override public synchronized void onActivate() { super.onActivate(); lastAttack = Long.MIN_VALUE / 2; }
}
