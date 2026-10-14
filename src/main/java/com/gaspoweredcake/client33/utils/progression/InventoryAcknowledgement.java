package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Waits for a click's expected state to settle. Never replays an unacknowledged click. */
public final class InventoryAcknowledgement {
    public record State(ItemStack cursor, List<ItemStack> slots) {
        public State { cursor = cursor.copy(); slots = slots.stream().map(ItemStack::copy).toList(); }
        boolean matches(State other) {
            if (!ItemStack.matches(cursor, other.cursor) || slots.size() != other.slots.size()) return false;
            for (int i = 0; i < slots.size(); i++) if (!ItemStack.matches(slots.get(i), other.slots.get(i))) return false;
            return true;
        }
    }
    private final State expected;
    private final long deadline;
    private long stableSince = -1;
    public InventoryAcknowledgement(State expected, long now) { this.expected = expected; deadline = now + 5000; }
    public boolean ready(State observed, long now) {
        if (expected.matches(observed)) {
            if (stableSince < 0) stableSince = now;
            if (now - stableSince >= 150) return true;
        } else stableSince = -1;
        if (now >= deadline) throw new IllegalStateException("Inventory click did not settle. Close the inventory and resume; check server latency or other inventory controls.");
        return false;
    }
}
