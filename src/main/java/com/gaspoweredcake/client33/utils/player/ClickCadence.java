package com.gaspoweredcake.client33.utils.player;

/** Tick cadence with immediate first click and no catch-up bursts after a blocked attack. */
public final class ClickCadence {
    private int remaining;

    public boolean tick(boolean enabled, boolean ready, int delay) {
        if (!enabled) {
            reset();
            return false;
        }
        if (remaining > 0) remaining--;
        if (!ready || remaining > 0) return false;
        remaining = Math.max(1, delay);
        return true;
    }

    public void reset() {
        remaining = 0;
    }
}
