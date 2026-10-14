package com.gaspoweredcake.client33.utils.world;

import java.util.*;
import java.util.function.Predicate;

/** Bounded, expiring observations. A replacement state never invents a new observation. */
public final class BlockObservationHistory<K, V> {
    public record Observation<V>(V value, long seen) {}
    private final LinkedHashMap<K, Observation<V>> observations = new LinkedHashMap<>();
    private final int capacity;
    public BlockObservationHistory(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    public void observe(K key, V value, long now) {
        observations.remove(key);
        observations.put(key, new Observation<>(value, now));
        while (observations.size() > capacity) observations.remove(observations.keySet().iterator().next());
    }
    public void forget(K key) { observations.remove(key); }
    public void clear() { observations.clear(); }
    public void expire(long now, long lifetime, Predicate<K> valid) {
        observations.entrySet().removeIf(entry -> now - entry.getValue().seen >= lifetime || !valid.test(entry.getKey()));
    }
    public Map<K, Observation<V>> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(observations)); }
    public int size() { return observations.size(); }
}
