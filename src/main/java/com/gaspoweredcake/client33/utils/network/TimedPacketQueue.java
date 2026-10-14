package com.gaspoweredcake.client33.utils.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded FIFO, monotonic deadlines; connection ownership belongs to the caller. */
public final class TimedPacketQueue<T> {
    private record Entry<T>(T value, long due) {}
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();
    private final int capacity;
    public TimedPacketQueue(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    public synchronized boolean offer(T value, long now, long delay) {
        if (entries.size() >= capacity) return false;
        long due = now + Math.max(0, delay);
        if (!entries.isEmpty()) due = Math.max(due, entries.getLast().due);
        entries.addLast(new Entry<>(value, due));
        return true;
    }
    public synchronized List<T> drain(long now, boolean all) {
        List<T> result = new ArrayList<>();
        while (!entries.isEmpty() && (all || entries.getFirst().due <= now)) result.add(entries.removeFirst().value);
        return result;
    }
    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }
}
