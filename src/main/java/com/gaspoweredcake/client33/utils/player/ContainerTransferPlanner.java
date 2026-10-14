package com.gaspoweredcake.client33.utils.player;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/** Chooses a movable stack without letting an incompatible earlier stack starve later items. */
public final class ContainerTransferPlanner {
    private ContainerTransferPlanner() {}
    public enum Filter { Include, Exclude }
    public record Decision(int slot, int matching, boolean waiting) {}
    public static boolean matches(ItemStack stack, List<Item> items, Filter mode) {
        return items.isEmpty() || (mode == Filter.Include) == items.contains(stack.getItem());
    }
    public static Decision choose(List<Slot> slots, int containerSlots, Predicate<ItemStack> filter, IntPredicate available) {
        if (containerSlots < 0 || containerSlots > slots.size()) throw new IllegalArgumentException("containerSlots");
        int matching = 0, selected = -1;
        boolean waiting = false;
        var destinations = slots.subList(containerSlots, slots.size());
        for (int i = 0; i < containerSlots; i++) {
            var stack = slots.get(i).getItem();
            if (stack.isEmpty() || !filter.test(stack)) continue;
            matching++;
            if (!hasCapacity(stack, destinations)) continue;
            if (!available.test(i)) { waiting = true; continue; }
            if (selected == -1) selected = i;
        }
        return new Decision(selected, matching, waiting);
    }
    public static boolean hasCapacity(ItemStack source, List<Slot> destinations) {
        if (source.isEmpty()) return false;
        for (var slot : destinations) {
            if (!slot.isActive() || !slot.mayPlace(source)) continue;
            var destination = slot.getItem();
            int capacity = Math.min(source.getMaxStackSize(), slot.getMaxStackSize(source));
            if (capacity > 0 && (destination.isEmpty() || ItemStack.isSameItemSameComponents(destination, source) && destination.getCount() < capacity)) return true;
        }
        return false;
    }
}
