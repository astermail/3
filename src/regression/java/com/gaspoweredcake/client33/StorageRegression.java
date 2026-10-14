package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.storage.ContainerSnapshot;
import com.gaspoweredcake.client33.utils.storage.ContainerMemory;
import com.gaspoweredcake.client33.utils.render.LabelLayout;
import java.util.*;

final class StorageRegression {
    static void run() {
        rendering();
        var diamond = new ContainerSnapshot.Stack("minecraft:diamond", "Diamond", 64, 64, false, -1);
        var tool = new ContainerSnapshot.Stack("minecraft:pickaxe", "§aPick\naxe", 1, 1, true, 25);
        var slots = new ArrayList<>(Arrays.asList(diamond, diamond, tool, null));
        var snapshot = ContainerSnapshot.summarize(100, slots);
        check(snapshot.serverObserved() && !ContainerSnapshot.summarize(100, slots, false).serverObserved(), "menu prediction distinguished from server packet");
        check(snapshot.slots() == 4 && snapshot.occupied() == 3 && snapshot.items() == 129, "slots/items exclude empty slots");
        check(snapshot.enchanted() == 1 && snapshot.lowestDurability() == 25, "equipment metadata retained");
        check(snapshot.contents().getFirst().count() == 128 && snapshot.contents().get(1).name().equals("Pick axe"), "aggregation and safe text");
        slots.clear(); check(snapshot.contents().size() == 2 && snapshot.occupancy() == 0.75, "immutable summary");
        check(!snapshot.expired(1099, 1000) && snapshot.expired(1100, 1000) && snapshot.expired(99, 1000), "exact expiry and regressed clock");
        try { snapshot.contents().clear(); throw new AssertionError("mutable snapshot"); } catch (UnsupportedOperationException expected) { }
        try { ContainerSnapshot.summarize(0, Collections.nCopies(109, diamond)); throw new AssertionError("unbounded snapshot"); } catch (IllegalArgumentException expected) { }
        check(ContainerSnapshot.summarize(0, List.of()).occupancy() == 0, "empty capacity is finite");
        var negative = new ContainerSnapshot.Stack("x", "x", -4, 64, false, -1);
        check(ContainerSnapshot.summarize(0, List.of(negative)).items() == 0, "reject negative counts");
        var huge = new ContainerSnapshot.Stack("x", "x", Integer.MAX_VALUE, 64, false, -1);
        check(ContainerSnapshot.summarize(0, List.of(huge)).items() == Integer.MAX_VALUE, "server overstack count is not silently truncated");
        check(ContainerSnapshot.summarize(0, Collections.nCopies(108, huge)).items() == 108L * Integer.MAX_VALUE, "overstack totals cannot overflow int");
        var memory = new ContainerMemory<String>(2); Object identity = new Object();
        check(memory.get("unknown", identity, 100, 1000) == null, "unseen must not be empty");
        memory.put("a", identity, snapshot); memory.put("b", identity, snapshot); memory.put("c", identity, snapshot);
        check(memory.size() == 2 && memory.get("a", identity, 100, 1000) == null, "bounded eviction");
        check(memory.get("b", new Object(), 100, 1000) == null, "replaced object cannot inherit inventory");
        memory.expire(1100, 1000); check(memory.size() == 0, "expiry removes retained summaries");
        memory.put("a", identity, snapshot); memory.clear(); check(memory.size() == 0, "world reset clears memory");
        var layout = new LabelLayout(800, 600);
        var first = layout.place(400, 400, 120, 45); var second = layout.place(400, 400, 120, 45);
        check(first != null && second != null && !first.overlaps(second), "cluster labels shift without overlapping");
        check(layout.place(Double.NaN, 200, 100, 30) == null && layout.place(20, 200, 900, 30) == null, "invalid geometry rejected");
        check(layout.place(900, 200, 100, 30) == null, "offscreen targets do not pile onto edges");
        var tiny = new LabelLayout(300, 200); tiny.reserve(new LabelLayout.Rect(0, 0, 300, 200));
        check(tiny.place(150, 190, 120, 30) == null, "inspector exclusion respected");
        System.out.println("Storage observation and label regressions passed.");
    }
    private static void rendering() {
        var origin = new net.minecraft.core.BlockPos(15, 64, -1);
        var east = origin.east();
        var pair = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(origin, east, true);
        var reversed = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(east, origin, true);
        check(pair.equals(reversed) && pair.getXsize() == 1.875 && pair.getZsize() == 0.875,
            "Both indexed halves produce one continuous chest outline across a chunk boundary");
        check(pair.getCenter().x == 16 && pair.maxY == 64.875, "Merged tracer anchor and chest lid height are correct");
        var southPair = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(origin, origin.south(), true);
        check(southPair.getZsize() == 1.875 && southPair.getXsize() == 0.875, "North/south chests merge on the correct axis");
        var single = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(origin, null, true);
        check(single.getXsize() == 0.875, "Unloaded or absent partner remains a single chest");
        var full = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(origin, null, false);
        check(full.getXsize() == 1 && full.getYsize() == 1, "Other storage retains full block bounds");
        check(opacity(0, 6, 64, 12) == 0 && opacity(3, 6, 64, 12) == 0.5 && opacity(6, 6, 64, 12) == 1,
            "Near fade has smooth zero, midpoint and full-opacity boundaries");
        check(opacity(58, 6, 64, 12) == 0.5 && opacity(64, 6, 64, 12) == 0,
            "Far fade respects the distance cap");
        check(opacity(1000, 0, 0, 12) == 1 && opacity(Double.NaN, 6, 64, 12) == 0,
            "Unlimited range and invalid distances are handled explicitly");
        var accent = new com.gaspoweredcake.client33.utils.render.color.Color(100, 200, 220, 180);
        var faded = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.faded(accent, 0.5);
        check(faded.a == 90 && accent.a == 180, "Focused vehicles fade without mutating the configured accent");
        var queue = new com.gaspoweredcake.client33.utils.storage.StorageRenderQueue<String>();
        Object chest = new Object();
        queue.add(chest, "left half", 20); queue.add(chest, "double chest", 20);
        queue.add("vehicle", "nearby boat", 3); queue.add("barrel", "barrel", 10);
        check(queue.nearest(2).equals(List.of("nearby boat", "barrel")), "Nearby vehicles share priority with blocks instead of being starved by the block budget");
        check(queue.nearest(10).equals(List.of("nearby boat", "barrel", "double chest")), "A double chest consumes one overlay, label and tracer budget entry");
        check(queue.nearest(0).isEmpty(), "Zero budget draws nothing");
        queue.clear(); check(queue.nearest(10).isEmpty(), "World/reset clears queued overlays");
    }
    private static double opacity(double distance, double near, double limit, double far) {
        return com.gaspoweredcake.client33.utils.storage.StorageRenderMath.opacity(distance, near, limit, far);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
