package com.gaspoweredcake.client33.utils.movement;

/** Network-thread corrections only record deadlines; world mutation stays on the client thread. */
public final class FlightRecovery {
    private long pauseUntil;
    private long windowStart;
    private int corrections;
    private boolean held;

    public synchronized void corrected(long now, long pauseMillis, int limit) {
        if (corrections == 0 || now - windowStart >= 10000 || now < windowStart) {
            windowStart = now;
            corrections = 0;
        }
        corrections++;
        pauseUntil = Math.max(pauseUntil, now + Math.max(0, pauseMillis));
        if (corrections >= Math.max(1, limit)) held = true;
    }

    public synchronized boolean allows(long now) { return !held && now >= pauseUntil; }
    public synchronized boolean held() { return held; }
    public synchronized void reset() { pauseUntil = windowStart = 0; corrections = 0; held = false; }
}
