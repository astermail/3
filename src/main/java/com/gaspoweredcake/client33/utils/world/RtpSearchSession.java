package com.gaspoweredcake.client33.utils.world;

import java.util.Objects;

/** Requests are paced by observed teleport arrival, scan time and the server-command interval. */
public final class RtpSearchSession {
    public enum State { Ready, Waiting, Scanning, Cooling, Found, TimedOut, Limit }
    public record Position(double x, double y, double z, String dimension) {}
    private final int minimumStorage, maxRequests;
    private final long interval, timeout, scanTime;
    private final double displacementSquared;
    private State state = State.Ready;
    private int requests;
    private Position origin;
    private long sentAt, arrivedAt;

    public RtpSearchSession(int minimumStorage, int maxRequests, long interval, long timeout, long scanTime, double displacement) {
        if (minimumStorage < 1 || maxRequests < 1 || interval < 1 || timeout < 1 || scanTime < 1 || !Double.isFinite(displacement) || displacement <= 0)
            throw new IllegalArgumentException("Invalid RTP search limits");
        this.minimumStorage = minimumStorage; this.maxRequests = maxRequests;
        this.interval = interval; this.timeout = timeout; this.scanTime = scanTime;
        displacementSquared = displacement * displacement;
    }

    /** True means send exactly one command now. */
    public boolean tick(long now, Position position) {
        if (state == State.Waiting) {
            double dx = position.x - origin.x, dy = position.y - origin.y, dz = position.z - origin.z;
            if (!Objects.equals(position.dimension, origin.dimension) || dx * dx + dy * dy + dz * dz >= displacementSquared) {
                arrivedAt = now; state = State.Scanning;
            } else if (now - sentAt >= timeout) state = State.TimedOut;
        } else if (state == State.Scanning && now - arrivedAt >= scanTime) state = State.Cooling;
        if (state == State.Cooling && now - sentAt >= interval) state = State.Ready;
        if (state != State.Ready) return false;
        if (requests >= maxRequests) { state = State.Limit; return false; }
        requests++; sentAt = now; origin = position; state = State.Waiting;
        return true;
    }

    public boolean match(int storage) {
        if (state != State.Scanning || storage < minimumStorage) return false;
        state = State.Found;
        return true;
    }
    public State state() { return state; }
    public int requests() { return requests; }
}
