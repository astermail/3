/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.utils.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scoped settings changes that preserve manual edits made while the scope is active. */
public final class TemporaryOverrides implements AutoCloseable {
    private final List<Entry<?>> entries = new ArrayList<>();

    public <T> void apply(Supplier<T> get, Consumer<T> set, T temporary) {
        Entry<T> entry = new Entry<>(get, set, get.get(), temporary);
        entries.add(entry);
        set.accept(temporary);
    }

    public void withOriginals(Runnable action) {
        List<Entry<?>> restored = entries.stream().filter(Entry::restore).toList();
        try { action.run(); }
        finally { restored.forEach(Entry::reapply); }
    }

    @Override public void close() {
        for (int i = entries.size() - 1; i >= 0; i--) entries.get(i).restore();
        entries.clear();
    }

    private record Entry<T>(Supplier<T> get, Consumer<T> set, T original, T temporary) {
        private boolean restore() {
            if (!Objects.equals(get.get(), temporary)) return false;
            set.accept(original);
            return true;
        }
        private void reapply() {
            if (Objects.equals(get.get(), original)) set.accept(temporary);
        }
    }
}
