package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.world.MiningQueue;
import com.gaspoweredcake.client33.utils.world.PacketMiningProgress;
import java.util.List;

public final class NukerRegression {
    public static void run() {
        var queue = new MiningQueue<String>();
        check(queue.choose(List.of("stone", "ore"), 0).equals("stone"), "Choose highest-priority candidate");
        check(queue.choose(List.of("ore", "stone"), 10).equals("stone"), "Changing distance or sort order preserves a partially mined block");
        queue.completed(20, 100);
        check(queue.choose(List.of("ore"), 119) == null, "Completion applies millisecond delay");
        check(queue.choose(List.of("ore"), 120).equals("ore"), "Next block starts at the deadline");
        check(queue.choose(List.of("wood"), 121).equals("wood"), "Invalid targets are replaced");
        queue.completed(130, 1000); queue.reset();
        check(queue.choose(List.of("wood"), 131).equals("wood"), "New selection clears old delays and targets");
        check(queue.choose(List.of(), 140) == null && queue.target() == null, "Empty scans release the target");

        var packets = new PacketMiningProgress();
        check(packets.start(0) && !packets.start(10), "Send START once per attempt");
        check(!packets.advance(0.25, 10) && !packets.advance(0.25, 60)
            && !packets.advance(0.25, 110) && packets.advance(0.25, 160), "Hard blocks wait for accumulated mining progress before STOP");
        check(!packets.advance(1, 200) && !packets.start(1659), "Wait for acknowledgement without repeated STOP or START packets");
        check(packets.start(1660) && !packets.advance(Double.NaN, 1660), "Rejected breaks can retry after a bounded wait; invalid progress is ignored");
        packets.reset();
        check(packets.start(1700) && packets.advance(1, 1700), "Instant breaks still complete in one tick");
        System.out.println("Nuker targeting and packet-mining regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
