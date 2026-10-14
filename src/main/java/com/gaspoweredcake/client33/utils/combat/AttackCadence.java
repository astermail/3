package com.gaspoweredcake.client33.utils.combat;

/** One charged attack at a time, independent of custom tick delays and rotation callback count. */
public final class AttackCadence {
    private long lastAttack = -1;
    public void reset() { lastAttack = -1; }
    public void record(long now) { lastAttack = Math.max(lastAttack, now); }

    public boolean ready(long now, double strength, double cooldownTicks, double tickMillis, double marginMillis) {
        if (!Double.isFinite(strength) || strength < 1 || !Double.isFinite(cooldownTicks) || cooldownTicks <= 0
            || !Double.isFinite(tickMillis) || tickMillis < 50 || !Double.isFinite(marginMillis) || marginMillis < 0) return false;
        double delay = Math.ceil(cooldownTicks * tickMillis + marginMillis);
        return Double.isFinite(delay) && (lastAttack < 0 || (now >= lastAttack && now - lastAttack >= delay));
    }
}
