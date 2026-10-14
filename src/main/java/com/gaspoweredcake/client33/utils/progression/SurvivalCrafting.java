package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import java.util.ArrayList;
import java.util.List;
import static com.gaspoweredcake.client33.Client33.mc;

/** One recipe batch. Each ingredient click waits for a stable cursor and grid acknowledgement. */
public final class SurvivalCrafting {
    /** Boundary between the click transaction and the live client. */
    public interface InventoryAccess {
        List<Slot> grid();
        List<Slot> inventory();
        Slot result();
        ItemStack cursor();
        Slot slot(int id);
        int width();
        boolean valid();
        long now();
        void click(int slot, int button, ContainerInput action);
        default boolean placeRecipe(SurvivalPlan.Recipe recipe) { return false; }
    }
    private final InventoryAccess inventory;
    private final SurvivalPlan.Recipe recipe;
    private int cell, phase, source = -1, waits, outputBefore;
    private ItemStack carried = ItemStack.EMPTY;
    private boolean resultTaken, placementAttempted, recipeBook;
    private long recipeDeadline;
    private InventoryAcknowledgement recipeReady;
    private InventoryAcknowledgement.State recipeState;
    private InventoryAcknowledgement pending;
    private final List<ItemStack> placed = new ArrayList<>();
    public SurvivalCrafting(AbstractCraftingMenu menu, SurvivalPlan.Recipe recipe) {
        this(new InventoryAccess() {
            public List<Slot> grid() { return menu.getInputGridSlots(); }
            public List<Slot> inventory() {
                return menu.slots.stream().filter(slot -> slot.container == mc.player.getInventory()
                    && slot.getContainerSlot() < 36).toList();
            }
            public Slot result() { return menu.getResultSlot(); }
            public ItemStack cursor() { return menu.getCarried(); }
            public Slot slot(int id) { return menu.getSlot(id); }
            public int width() { return menu.getGridWidth(); }
            public boolean valid() { return mc.player != null && mc.gameMode != null && mc.player.containerMenu == menu; }
            public long now() { return CombatTelemetry.now(); }
            public boolean placeRecipe(SurvivalPlan.Recipe recipe) { return SurvivalRecipeBook.place(menu, recipe); }
            public void click(int slot, int button, ContainerInput action) {
                mc.gameMode.handleContainerInput(menu.containerId, slot, button, action, mc.player);
            }
        }, recipe);
    }

    public SurvivalCrafting(InventoryAccess inventory, SurvivalPlan.Recipe recipe) {
        this.inventory = inventory;
        this.recipe = recipe;
        if (recipe.width() > inventory.width() || recipe.height() > inventory.grid().size() / inventory.width()) {
            throw new IllegalArgumentException("Recipe does not fit the open crafting grid.");
        }
        for (var slot : inventory.grid()) {
            if (slot.hasItem()) throw new IllegalStateException("Clear the crafting grid before starting.");
            placed.add(ItemStack.EMPTY);
        }
        if (!inventory.cursor().isEmpty()) throw new IllegalStateException("Clear the cursor before crafting.");
    }
    private void click(int slot, int button, ContainerInput action) { inventory.click(slot, button, action); }
    private InventoryAcknowledgement.State observed() {
        var slots = new ArrayList<ItemStack>();
        for (var slot : inventory.grid()) slots.add(slot.getItem());
        if (source >= 0) slots.add(inventory.slot(source).getItem());
        return new InventoryAcknowledgement.State(inventory.cursor(), slots);
    }
    private void expect(ItemStack sourceStack) {
        var slots = new ArrayList<>(placed);
        if (source >= 0) slots.add(sourceStack);
        pending = new InventoryAcknowledgement(new InventoryAcknowledgement.State(carried, slots), inventory.now());
    }
    private int outputCount() {
        int count = 0;
        for (var slot : inventory.inventory()) if (SurvivalItems.matches(slot.getItem(), recipe.output())) count += slot.getItem().getCount();
        return count;
    }
    public boolean tick() {
        if (!inventory.valid()) throw new IllegalStateException("Crafting screen changed; resume when ready.");
        if (pending != null) {
            if (!pending.ready(observed(), inventory.now())) return false;
            pending = null;
            return false;
        }
        if (resultTaken) {
            if (inventory.cursor().isEmpty() && inventory.grid().stream().allMatch(slot -> !slot.hasItem()) && outputCount() >= outputBefore + recipe.yield()) return true;
            if (++waits > 100) throw new IllegalStateException("Craft result was not received. Check inventory space and server recipes.");
            return false;
        }
        if (!placementAttempted) {
            placementAttempted = true;
            recipeBook = inventory.placeRecipe(recipe);
            recipeDeadline = inventory.now() + 5000;
        }
        if (recipeBook) {
            var result = inventory.result().getItem();
            if (inventory.cursor().isEmpty() && SurvivalItems.matches(result, recipe.output()) && result.getCount() == recipe.yield()
                && inventory.grid().stream().anyMatch(Slot::hasItem)) {
                var observed = observed();
                if (recipeReady == null || !recipeState.matches(observed)) {
                    recipeState = observed;
                    recipeReady = new InventoryAcknowledgement(observed, inventory.now());
                }
                if (recipeReady.ready(observed, inventory.now())) {
                    outputBefore = outputCount();
                    click(inventory.result().index, 0, ContainerInput.QUICK_MOVE);
                    resultTaken = true;
                    waits = 0;
                }
            } else recipeReady = null;
            if (!resultTaken && inventory.now() >= recipeDeadline) {
                throw new IllegalStateException("Server did not place the crafting recipe. Check recipe permissions and the crafting grid.");
            }
            return false;
        }
        if (!ItemStack.matches(inventory.cursor(), carried)) throw new IllegalStateException("Crafting cursor changed outside an acknowledged click. Clear it and resume.");
        var grid = inventory.grid();
        for (int i = 0; i < grid.size(); i++) if (!ItemStack.matches(grid.get(i).getItem(), placed.get(i))) throw new IllegalStateException("Crafting grid changed; check the inventory.");
        int width = inventory.width();
        while (cell < recipe.cells().size() && recipe.cells().get(cell) == Items.AIR) cell++;
        if (cell == recipe.cells().size()) {
            var result = inventory.result().getItem();
            if (SurvivalItems.matches(result, recipe.output()) && result.getCount() == recipe.yield()) {
                outputBefore = outputCount(); click(inventory.result().index, 0, ContainerInput.QUICK_MOVE); resultTaken = true; waits = 0;
            } else if (++waits > 100) throw new IllegalStateException("Recipe result unavailable. Server recipes may differ.");
            return false;
        }
        int destination = cell / recipe.width() * width + cell % recipe.width();
        var ingredient = recipe.cells().get(cell);
        if (phase == 0) {
            source = -1;
            for (var slot : inventory.inventory()) if (SurvivalItems.matches(slot.getItem(), ingredient)) { source = slot.index; break; }
            if (source < 0) throw new IllegalStateException("Ingredients changed during crafting.");
            carried = inventory.slot(source).getItem().copy();
            expect(ItemStack.EMPTY); click(source, 0, ContainerInput.PICKUP); phase = 1;
        } else if (phase == 1) {
            placed.set(destination, carried.copyWithCount(1));
            carried = carried.copyWithCount(carried.getCount() - 1);
            expect(inventory.slot(source).getItem()); click(grid.get(destination).index, 1, ContainerInput.PICKUP); phase = 2;
        } else {
            if (!carried.isEmpty()) {
                ItemStack remainder = carried; carried = ItemStack.EMPTY; expect(remainder); click(source, 0, ContainerInput.PICKUP);
            }
            phase = 0; cell++;
        }
        return false;
    }
    public void cancel() {
        if (!inventory.valid()) return;
        // Only return a cursor whose ownership has actually settled; never move an unknown stack.
        if (pending == null && !carried.isEmpty() && ItemStack.matches(inventory.cursor(), carried) && source >= 0) click(source, 0, ContainerInput.PICKUP);
    }
}
