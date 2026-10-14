package com.gaspoweredcake.client33.utils.combat;

/** Monotonic receipt-time model. Server ticks and network jitter are estimates, never acknowledgements. */
public final class ServerTickEstimator {
    private long lastReceipt = -1, lastServerTick, lastAttack = -1;
    private double millisPerTick = 50, jitter;
    private int samples;

    public synchronized void reset() {
        lastReceipt = lastAttack = -1;
        lastServerTick = 0;
        millisPerTick = 50;
        jitter = 0;
        samples = 0;
    }

    public synchronized void sample(long serverTick, long now) {
        if (lastReceipt >= 0 && (serverTick <= lastServerTick || now <= lastReceipt)) return;
        if (lastReceipt >= 0) {
            double interval = (double) (now - lastReceipt) / (serverTick - lastServerTick);
            if (interval >= 10 && interval <= 2000) {
                interval = Math.max(50, Math.min(1000, interval));
                jitter = Math.min(250, jitter * 0.75 + Math.abs(interval - millisPerTick) * 0.25);
                millisPerTick = samples == 0 ? interval : millisPerTick * 0.7 + interval * 0.3;
                samples++;
            }
        }
        lastServerTick = serverTick;
        lastReceipt = now;
    }

    public synchronized void attack(long now) { lastAttack = now; }
    public synchronized double tickMillis() { return millisPerTick; }
    public synchronized boolean sampled() { return samples > 0; }
    public synchronized long ageMillis(long now) {
        return lastReceipt < 0 ? Long.MAX_VALUE : Math.max(0, now - lastReceipt);
    }
    public synchronized boolean stale(long now) {
        return lastReceipt >= 0 && now - lastReceipt > Math.max(6000, 60 * millisPerTick);
    }
    public synchronized long delayMillis(double ticks, double margin) {
        if (!Double.isFinite(ticks) || ticks < 0 || !Double.isFinite(margin)) return Long.MAX_VALUE;
        return (long) Math.ceil(ticks * millisPerTick + Math.max(0, margin) + jitter);
    }
    /** Vanilla-rate fallback for attacks when time packets stop but other server packets still arrive. */
    public synchronized boolean readyWithFallback(long now, double ticks, double margin) {
        if (!stale(now)) return ready(now, ticks, margin);
        if (!Double.isFinite(ticks) || ticks < 0 || !Double.isFinite(margin)) return false;
        long delay = (long) Math.ceil(ticks * 50 + Math.max(0, margin));
        return lastAttack < 0 || now - lastAttack >= delay;
    }

    public synchronized boolean ready(long now, double ticks, double margin) {
        long delay = delayMillis(ticks, margin);
        return delay != Long.MAX_VALUE && !stale(now) && (lastAttack < 0 || now - lastAttack >= delay);
    }
}
