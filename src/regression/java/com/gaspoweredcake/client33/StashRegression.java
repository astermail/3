package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.systems.modules.world.StashFinder;
import com.gaspoweredcake.client33.systems.modules.world.StashFinder.Chunk;
import com.gaspoweredcake.client33.utils.world.CoordinateContext;
import com.gaspoweredcake.client33.utils.world.StashPersistence;
import net.minecraft.world.level.ChunkPos;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Exercises coordinate provenance through record identity, legacy loading and on-disk exports. */
public final class StashRegression {
    public static void run() throws Exception {
        var first = record("server:one.example:25565", "minecraft:overworld", 1000);
        var otherServer = record("server:two.example:25565", "minecraft:overworld", 2000);
        var otherDimension = record(first.server, "minecraft:the_nether", 3000);
        List<Chunk> records = new ArrayList<>();
        for (var entry : List.of(first, otherServer, otherDimension)) StashFinder.recordObservation(records, entry);
        check(records.size() == 3, "Same coordinates must remain distinct across servers and dimensions");
        var newer = record(first.server, first.dimension, 5000);
        newer.chests = 7;
        check(StashFinder.recordObservation(records, newer) == first, "Revisit replaces only matching provenance");
        check(newer.firstSeen == 1000 && newer.lastSeen == 5000 && records.size() == 3, "Revisit preserves first observation and advances last observation");
        check(!newer.matches(otherServer.server, newer.dimension), "Cross-server navigation must fail");
        check(!newer.matches(newer.server, otherDimension.dimension), "Cross-dimension navigation must fail");
        check(newer.matches(first.server, first.dimension), "Exact context may navigate");
        check(!CoordinateContext.storageKey("server:one:25565").equals(CoordinateContext.storageKey("server:one_25565")), "Filesystem sanitization collisions cannot mix server records");
        check(CoordinateContext.storageKey("../../outside").matches("[0-9a-f]{64}"), "Server IDs cannot escape their storage folder");
        check(CoordinateContext.time(1000).equals("1970-01-01 00:00:01 UTC"), "Display time has an explicit unambiguous timezone");
        check(CoordinateContext.time(0).contains("Unknown"), "Legacy dates must not be invented");

        var folder = Files.createTempDirectory("stash-provenance-");
        try {
            var tasks = new ArrayList<Runnable>();
            var writes = new ArrayList<List<Chunk>>();
            var failures = new ArrayList<Exception>();
            var store = new StashPersistence(tasks::add, (path, snapshot) -> writes.add(snapshot), (path, failure) -> failures.add(failure));
            var pending = record(first.server, first.dimension, 9000);
            store.submit(folder, List.of(pending));
            pending.chests = 30; pending.otherStorage = 1;
            store.submit(folder, List.of(pending));
            check(tasks.size() == 1 && writes.isEmpty(), "Observations queue one worker without writing on the caller thread");
            var latest = store.pendingSnapshot(folder);
            check(latest.getFirst().getTotal() == 31, "Reload sees newest queued counts including additional storage types");
            latest.getFirst().chests = 100;
            tasks.removeFirst().run();
            check(writes.size() == 1 && writes.getFirst().getFirst().getTotal() == 31 && store.pendingSnapshot(folder) == null, "Coalesced immutable snapshot is written once");
            store.submit(folder, List.of()); tasks.removeFirst().run();
            check(writes.getLast().isEmpty(), "Clearing cannot be overwritten by older queued discoveries");

            StashPersistence[] racing = new StashPersistence[1];
            var duringWrite = new ArrayList<Integer>();
            racing[0] = new StashPersistence(tasks::add, (path, snapshot) -> {
                duringWrite.add(snapshot.getFirst().chests);
                if (duringWrite.size() == 1) {
                    var newerDuringWrite = snapshot.getFirst().copy(); newerDuringWrite.chests = 40;
                    racing[0].submit(path, List.of(newerDuringWrite));
                    check(racing[0].pendingSnapshot(path).getFirst().chests == 40, "Newer snapshot survives an in-flight save with equal coordinate identity");
                }
            }, (path, failure) -> { throw new AssertionError(failure); });
            racing[0].submit(folder, List.of(pending)); tasks.removeFirst().run();
            check(duringWrite.equals(List.of(30, 40)), "In-flight and newer writes retain chronological order");
            var rejected = new StashPersistence(tasks::add, (path, snapshot) -> { throw new java.io.IOException("synthetic disk failure"); }, (path, failure) -> failures.add(failure));
            rejected.submit(folder, List.of(pending)); tasks.removeFirst().run();
            check(failures.size() == 1 && rejected.pendingSnapshot(folder).getFirst().getTotal() == 31 && tasks.isEmpty(), "Failed save retains records without a retry loop");

            var json = folder.resolve("stashes.json"); var csv = folder.resolve("stashes.csv");
            StashFinder.writeJson(json, records);
            StashFinder.writeCsv(csv, records);
            var jsonRecords = StashFinder.readJson(json); var csvRecords = StashFinder.readCsv(csv);
            check(jsonRecords.size() == 3 && csvRecords.size() == 3, "Both export formats retain all contexts");
            check(jsonRecords.getFirst().equals(newer) && jsonRecords.getFirst().firstSeen == 1000 && jsonRecords.getFirst().lastSeen == 5000, "JSON roundtrip preserves identity and timestamps");
            check(csvRecords.getFirst().equals(newer) && csvRecords.getFirst().firstSeen == 1000 && csvRecords.getFirst().lastSeen == 5000, "CSV roundtrip preserves identity and timestamps");
            StashFinder.writeJson(json, List.of(pending)); StashFinder.writeCsv(csv, List.of(pending));
            check(StashFinder.readJson(json).getFirst().getTotal() == 31 && StashFinder.readCsv(csv).getFirst().otherStorage == 1, "Additional selected storage persists in both formats");
            var quoted = record("server:a,\"b\"", "custom:underworld", 8000);
            StashFinder.writeCsv(csv, List.of(quoted));
            check(StashFinder.readCsv(csv).getFirst().equals(quoted), "CSV quoted identity fields roundtrip without splitting columns");

            Files.writeString(json, "[{\"chunkPos\":{\"x\":4,\"z\":-2},\"chests\":5}]");
            Chunk legacy = StashFinder.readJson(json).getFirst();
            check(legacy.server.equals(CoordinateContext.UNKNOWN) && legacy.dimension.equals(CoordinateContext.UNKNOWN), "Old JSON is retained with unknown provenance");
            check(legacy.firstSeen == 0 && legacy.lastSeen == 0 && !legacy.matches(first.server, first.dimension), "Old records cannot be mistaken for a newly observed stash");
            Files.writeString(csv, "x,z,chests,barrels,shulkers,enderChests,furnaces,dispensersDroppers,hoppers\n4,-2,5,0,0,0,0,0,0\n");
            check(StashFinder.readCsv(csv).getFirst().equals(legacy), "Legacy CSV remains readable with unknown context");
            Files.writeString(csv, "x,z,chests,barrels,shulkers,enderChests,furnaces,dispensersDroppers,hoppers,server,dimension,firstSeenUTC,lastSeenUTC\n4,-2,5,0,0,0,0,0,0,server:test,minecraft:overworld,1970-01-01T00:00:01Z,1970-01-01T00:00:02Z\n");
            check(StashFinder.readCsv(csv).getFirst().server.equals("server:test") && StashFinder.readCsv(csv).getFirst().lastSeen == 2000, "Previous provenance CSV retains metadata after adding a count column");
            StashFinder.writeJson(json, List.of()); StashFinder.writeCsv(csv, List.of());
            check(StashFinder.readJson(json).isEmpty() && StashFinder.readCsv(csv).isEmpty(), "Persisted clear cannot resurrect deleted records on reload");
            Files.writeString(json, "null");
            try { StashFinder.readJson(json); throw new AssertionError("Null records must fail closed"); }
            catch (java.io.IOException expected) { }
            try (var files = Files.list(folder)) { check(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "Atomic export leaves no staging files"); }
        } finally {
            try (var files = Files.list(folder)) { for (var path : files.toList()) Files.delete(path); }
            Files.delete(folder);
        }
        System.out.println("Stash provenance and persistence regressions passed.");
    }

    private static Chunk record(String server, String dimension, long time) {
        var record = new Chunk(new ChunkPos(4, -2));
        record.server = server; record.dimension = dimension;
        record.firstSeen = record.lastSeen = time; record.chests = 5;
        return record;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
