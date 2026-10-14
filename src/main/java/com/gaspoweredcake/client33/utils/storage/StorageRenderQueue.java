package com.gaspoweredcake.client33.utils.storage;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** One shared budget for logical containers, regardless of their render mode or source type. */
public final class StorageRenderQueue<T> {
    private record Entry<T>(T value, double distance) {}
    private final LinkedHashMap<Object, Entry<T>> entries = new LinkedHashMap<>();

    public void clear() { entries.clear(); }
    public boolean contains(Object key) { return entries.containsKey(key); }
    public void add(Object key, T value, double distance) {
        if (Double.isFinite(distance) && distance >= 0) entries.put(key, new Entry<>(value, distance));
    }
    public List<T> nearest(int budget) {
        return entries.values().stream().sorted(Comparator.comparingDouble(Entry::distance))
            .limit(Math.max(0, budget)).map(Entry::value).toList();
    }
}
