package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import static com.gaspoweredcake.client33.Client33.mc;

/** Deposits an exact batch and fuel, collects its output, then retrieves unused inputs. */
public final class SurvivalSmelting {
    private final FurnaceMenu menu;
    private final Item input;
    private final Item output;
    private final int batch;
    private int phase, source = -1, remaining;
    private final int inventoryBefore;
    private long lastTransfer;
    private InventoryAcknowledgement pending;
    private ItemStack carried = ItemStack.EMPTY;
    public SurvivalSmelting(FurnaceMenu menu, Item output, int requested) {
        this.menu = menu; this.output = output;
        inventoryBefore = outputCount();
        Item raw = output == Items.IRON_INGOT ? Items.RAW_IRON : Items.BEEF;
        var found = menu.slots.stream().filter(slot -> slot.container == mc.player.getInventory() && SurvivalItems.matches(slot.getItem(), raw)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Smelting supplies changed."));
        int fuel = menu.slots.stream().filter(slot -> slot.container == mc.player.getInventory() && SurvivalItems.matches(slot.getItem(), Items.COAL))
            .mapToInt(slot -> slot.getItem().getCount()).max().orElse(0);
        if (fuel == 0) throw new IllegalStateException("No smelting fuel available.");
        input = found.getItem().getItem(); batch = Math.min(Math.min(requested, found.getItem().getCount()), fuel * 8);
        if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Clear the cursor first.");
    }
    private void click(int slot, int button, ContainerInput action) { mc.gameMode.handleContainerInput(menu.containerId, slot, button, action, mc.player); }
    private int outputCount() {
        int count = 0;
        for (var slot : menu.slots) if (slot.container == mc.player.getInventory() && SurvivalItems.matches(slot.getItem(), output)) count += slot.getItem().getCount();
        return count;
    }
    private void expect(ItemStack sourceStack) {
        pending = new InventoryAcknowledgement(new InventoryAcknowledgement.State(carried, java.util.List.of(sourceStack)), com.gaspoweredcake.client33.utils.combat.CombatTelemetry.now());
    }
    public boolean tick() {
        if (mc.player.containerMenu != menu) throw new IllegalStateException("Furnace screen changed.");
        if (pending != null) {
            if (!pending.ready(new InventoryAcknowledgement.State(menu.getCarried(), java.util.List.of(menu.getSlot(source).getItem())), com.gaspoweredcake.client33.utils.combat.CombatTelemetry.now())) return false;
            pending = null; return false;
        }
        if (!ItemStack.matches(menu.getCarried(), carried)) throw new IllegalStateException("Furnace inventory changed unexpectedly.");
        if (phase < 6) {
            boolean fuel = phase >= 3;
            int sub = phase % 3, dest = fuel ? 1 : 0;
            if (sub == 0) {
                source = -1;
                for (var slot : menu.slots) if (slot.container == mc.player.getInventory()
                    && (fuel ? SurvivalItems.matches(slot.getItem(), Items.COAL) : slot.getItem().is(input))) { if (source < 0 || slot.getItem().getCount() > menu.getSlot(source).getItem().getCount()) source = slot.index; }
                remaining = fuel ? (batch + 7) / 8 : batch;
                if (source < 0 || menu.getSlot(source).getItem().getCount() < remaining) throw new IllegalStateException("Smelting fuel or inputs changed.");
                carried = menu.getSlot(source).getItem().copy(); expect(ItemStack.EMPTY);
                click(source, 0, ContainerInput.PICKUP); phase++;
            } else if (sub == 1) {
                if (carried.isEmpty() || (fuel ? !SurvivalItems.matches(carried, Items.COAL) : !carried.is(input))) throw new IllegalStateException("Smelting pickup rejected.");
                carried = carried.copyWithCount(carried.getCount() - 1); expect(menu.getSlot(source).getItem());
                click(dest, 1, ContainerInput.PICKUP);
                if (--remaining == 0) phase++;
            } else {
                if (!carried.isEmpty()) { ItemStack remainder = carried; carried = ItemStack.EMPTY; expect(remainder); click(source, 0, ContainerInput.PICKUP); }
                phase++;
            }
            return false;
        }
        long now = com.gaspoweredcake.client33.utils.combat.CombatTelemetry.now();
        if (now - lastTransfer < 400) return false;
        if (menu.getSlot(2).hasItem()) {
            if (!SurvivalItems.matches(menu.getSlot(2).getItem(), output)) throw new IllegalStateException("Unexpected furnace output; check the camp furnace.");
            click(2, 0, ContainerInput.QUICK_MOVE); lastTransfer = now;
            return false;
        }
        if (outputCount() < inventoryBefore + batch) return false;
        for (int slot = 0; slot < 2; slot++) if (menu.getSlot(slot).hasItem()) { click(slot, 0, ContainerInput.QUICK_MOVE); lastTransfer = now; return false; }
        return true;
    }
    public void cancel() {
        if (mc.player != null && mc.player.containerMenu == menu && mc.gameMode != null && source >= 0
            && pending == null && !carried.isEmpty() && ItemStack.matches(menu.getCarried(), carried)) click(source, 0, ContainerInput.PICKUP);
    }
}
