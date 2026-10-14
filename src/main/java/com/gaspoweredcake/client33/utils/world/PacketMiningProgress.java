package com.gaspoweredcake.client33.utils.world;

/** Models a single START -> accumulated hardness -> STOP sequence. */
public final class PacketMiningProgress {
    private double progress;
    private boolean started, stopped;
    private long retryAt;

    public boolean start(long now) {
        if (stopped && now >= retryAt) reset();
        if (started) return false;
        started = true;
        return true;
    }

    public boolean advance(double delta, long now) {
        if (!started || stopped || !Double.isFinite(delta) || delta <= 0) return false;
        progress += delta;
        if (progress < 1) return false;
        stopped = true;
        retryAt = now + 1500;
        return true;
    }

    public void reset() { progress = 0; started = stopped = false; retryAt = 0; }
}
