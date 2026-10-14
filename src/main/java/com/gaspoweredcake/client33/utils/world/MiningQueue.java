package com.gaspoweredcake.client33.utils.world;

import java.util.List;

/** Retains mining progress on a valid target and applies delay only after completion. */
public final class MiningQueue<T> {
    private T target;
    private long readyAt;

    public T choose(List<T> candidates, long now) {
        if (target != null && !candidates.contains(target)) target = null;
        if (now < readyAt) return null;
        if (target == null && !candidates.isEmpty()) target = candidates.getFirst();
        return target;
    }

    public T target() { return target; }

    public void completed(long now, long delayMillis) {
        target = null;
        readyAt = now + Math.max(0, delayMillis);
    }

    public void reset() {
        target = null;
        readyAt = 0;
    }
}
