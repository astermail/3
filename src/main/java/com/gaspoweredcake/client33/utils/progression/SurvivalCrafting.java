package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import java.util.ArrayList;
import java.util.List;
import static com.gaspoweredcake.client33.Client33.mc;

/** One recipe batch. Each ingredient click waits for a stable cursor and grid acknowledgement. */
public final class SurvivalCrafting {
    private final AbstractCraftingMenu menu;
    private final SurvivalPlan.Recipe recipe;
    private int cell, phase, source = -1, waits, outputBefore;
    private ItemStack carried = ItemStack.EMPTY;
    private boolean resultTaken;
    private InventoryAcknowledgement pending;
    private final List<ItemStack> placed = new ArrayList<>();
    public SurvivalCrafting(AbstractCraftingMenu menu, SurvivalPlan.Recipe recipe) {
        this.menu = menu; this.recipe = recipe;
        for (var slot : menu.getInputGridSlots()) {
            if (slot.hasItem()) throw new IllegalStateException("Clear the crafting grid before starting.");
            placed.add(ItemStack.EMPTY);
        }
        if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Clear the cursor before crafting.");
    }
    private void click(int slot, int button, ContainerInput action) { mc.gameMode.handleContainerInput(menu.containerId, slot, button, action, mc.player); }
    private InventoryAcknowledgement.State observed() {
        var slots = new ArrayList<ItemStack>();
        for (var slot : menu.getInputGridSlots()) slots.add(slot.getItem());
        if (source >= 0) slots.add(menu.getSlot(source).getItem());
        return new InventoryAcknowledgement.State(menu.getCarried(), slots);
    }
    private void expect(ItemStack sourceStack) {
        var slots = new ArrayList<>(placed);
        if (source >= 0) slots.add(sourceStack);
        pending = new InventoryAcknowledgement(new InventoryAcknowledgement.State(carried, slots), CombatTelemetry.now());
    }
    private int outputCount() {
        int count = 0;
        for (var slot : menu.slots) if (slot.container == mc.player.getInventory() && SurvivalItems.matches(slot.getItem(), recipe.output())) count += slot.getItem().getCount();
        return count;
    }
    public boolean tick() {
        if (mc.player.containerMenu != menu) throw new IllegalStateException("Crafting screen changed; resume when ready.");
        if (pending != null) {
            if (!pending.ready(observed(), CombatTelemetry.now())) return false;
            pending = null;
            return false;
        }
        if (resultTaken) {
            if (menu.getCarried().isEmpty() && menu.getInputGridSlots().stream().allMatch(slot -> !slot.hasItem()) && outputCount() >= outputBefore + recipe.yield()) return true;
            if (++waits > 100) throw new IllegalStateException("Craft result was not received. Check inventory space and server recipes.");
            return false;
        }
        if (!ItemStack.matches(menu.getCarried(), carried)) throw new IllegalStateException("Crafting cursor changed outside an acknowledged click. Clear it and resume.");
        var grid = menu.getInputGridSlots();
        for (int i = 0; i < grid.size(); i++) if (!ItemStack.matches(grid.get(i).getItem(), placed.get(i))) throw new IllegalStateException("Crafting grid changed; check the inventory.");
        int width = grid.size() == 4 ? 2 : 3;
        while (cell < recipe.cells().size() && recipe.cells().get(cell) == Items.AIR) cell++;
        if (cell == recipe.cells().size()) {
            var result = menu.getResultSlot().getItem();
            if (SurvivalItems.matches(result, recipe.output()) && result.getCount() == recipe.yield()) {
                outputBefore = outputCount(); click(menu.getResultSlot().index, 0, ContainerInput.QUICK_MOVE); resultTaken = true; waits = 0;
            } else if (++waits > 100) throw new IllegalStateException("Recipe result unavailable. Server recipes may differ.");
            return false;
        }
        int destination = cell / recipe.width() * width + cell % recipe.width();
        var ingredient = recipe.cells().get(cell);
        if (phase == 0) {
            source = -1;
            for (var slot : menu.slots) if (slot.container == mc.player.getInventory() && SurvivalItems.matches(slot.getItem(), ingredient)) { source = slot.index; break; }
            if (source < 0) throw new IllegalStateException("Ingredients changed during crafting.");
            carried = menu.getSlot(source).getItem().copy();
            expect(ItemStack.EMPTY); click(source, 0, ContainerInput.PICKUP); phase = 1;
        } else if (phase == 1) {
            placed.set(destination, carried.copyWithCount(1));
            carried = carried.copyWithCount(carried.getCount() - 1);
            expect(menu.getSlot(source).getItem()); click(grid.get(destination).index, 1, ContainerInput.PICKUP); phase = 2;
        } else {
            if (!carried.isEmpty()) {
                ItemStack remainder = carried; carried = ItemStack.EMPTY; expect(remainder); click(source, 0, ContainerInput.PICKUP);
            }
            phase = 0; cell++;
        }
        return false;
    }
    public void cancel() {
        if (mc.player == null || mc.player.containerMenu != menu || mc.gameMode == null) return;
        // Only return a cursor whose ownership has actually settled; never move an unknown stack.
        if (pending == null && !carried.isEmpty() && ItemStack.matches(menu.getCarried(), carried) && source >= 0) click(source, 0, ContainerInput.PICKUP);
    }
}
