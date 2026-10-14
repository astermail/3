package com.gaspoweredcake.client33.utils.world;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.systems.modules.world.StashFinder;
import com.gaspoweredcake.client33.systems.modules.world.StashFinder.Chunk;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Ordered background exports, retaining the newest snapshot while disk writes are pending. */
public final class StashPersistence {
    @FunctionalInterface public interface Writer { void write(Path folder, List<Chunk> records) throws Exception; }
    private final Executor executor;
    private final Writer writer;
    private final BiConsumer<Path, Exception> errors;
    private final Map<Path, List<Chunk>> pending = new LinkedHashMap<>(), latest = new LinkedHashMap<>();
    private boolean draining;

    public StashPersistence() {
        var worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Client33-Stash-Writer");
            thread.setDaemon(true);
            return thread;
        });
        executor = worker;
        writer = (folder, records) -> {
            StashFinder.writeJson(folder.resolve("stashes.json"), records);
            StashFinder.writeCsv(folder.resolve("stashes.csv"), records);
        };
        errors = (folder, failure) -> Client33.LOG.error("Cannot save stash records to {}", folder, failure);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.shutdown();
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) Client33.LOG.error("Stash writes did not finish before shutdown");
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "Client33-Stash-Shutdown"));
    }

    public StashPersistence(Executor executor, Writer writer, BiConsumer<Path, Exception> errors) {
        this.executor = executor; this.writer = writer; this.errors = errors;
    }

    public synchronized void submit(Path folder, List<Chunk> records) {
        folder = folder.toAbsolutePath().normalize();
        List<Chunk> snapshot = copy(records);
        pending.put(folder, snapshot);
        latest.put(folder, snapshot);
        if (!draining) {
            draining = true;
            try { executor.execute(this::drain); }
            catch (RuntimeException rejected) { draining = false; errors.accept(folder, rejected); }
        }
    }

    /** Re-enabling or switching servers must not reload older data while its save is in flight. */
    public synchronized List<Chunk> pendingSnapshot(Path folder) {
        List<Chunk> snapshot = latest.get(folder.toAbsolutePath().normalize());
        return snapshot == null ? null : copy(snapshot);
    }

    private void drain() {
        while (true) {
            Path folder;
            List<Chunk> snapshot;
            synchronized (this) {
                if (pending.isEmpty()) { draining = false; return; }
                var entry = pending.entrySet().iterator().next();
                folder = entry.getKey(); snapshot = entry.getValue();
                pending.remove(folder);
            }
            try {
                writer.write(folder, snapshot);
                synchronized (this) {
                    // Chunk equality ignores counts/time. Compare snapshot identity, not List.equals.
                    if (latest.get(folder) == snapshot) latest.remove(folder);
                }
            } catch (Exception failure) {
                // Retain the newest snapshot for reads and a later explicit save; never spin on errors.
                errors.accept(folder, failure);
            }
        }
    }

    private static List<Chunk> copy(List<Chunk> records) { return records.stream().map(Chunk::copy).toList(); }
}
