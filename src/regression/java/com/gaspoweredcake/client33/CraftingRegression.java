package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.progression.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.util.*;

/** Exercises the production click transaction, including delayed server result delivery. */
public final class CraftingRegression {
    public static void run() {
        // The standalone harness has registries but does not load a world's item components.
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).orElseThrow();
            if (!holder.areComponentsBound()) holder.bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
        }
        var recipes = new ArrayList<>(SurvivalPlan.RECIPES.values());
        recipes.add(new SurvivalPlan.Recipe(Items.OAK_PLANKS, 4, 1, List.of(Items.OAK_LOG)));
        for (var recipe : recipes) {
            for (int width : new int[]{2, 3}) {
                if (recipe.width() > width || recipe.height() > width) continue;
                for (int extra : new int[]{0, 17}) for (boolean book : new boolean[]{false, true}) {
                    var access = new Inventory(recipe, width, extra);
                    access.book = book;
                    var crafting = new SurvivalCrafting(access, recipe);
                    boolean done = false;
                    for (int tick = 0; tick < 1000 && !done; tick++) {
                        access.advance();
                        done = crafting.tick();
                    }
                    check(done, "Craft transaction completes: " + recipe.output());
                    check(access.requests == (book ? 1 : 0), "Server recipe placement is requested once");
                    check(access.takes == 1 && access.cursor.isEmpty(), "Output taken exactly once with an empty cursor");
                    check(access.grid().stream().noneMatch(Slot::hasItem), "Every ingredient is consumed");
                    for (var entry : recipe.ingredients().entrySet()) {
                        int remaining = access.inventory().stream().map(Slot::getItem)
                            .filter(stack -> stack.is(entry.getKey())).mapToInt(ItemStack::getCount).sum();
                        check(remaining == extra, "Unused ingredients return to their source");
                    }
                }
            }
        }
        var recipe = recipes.getLast();
        var rejected = new Inventory(recipe, 2, 0);
        rejected.reject = true;
        var transaction = new SurvivalCrafting(rejected, recipe);
        boolean stopped = false;
        try {
            for (int i = 0; i < 80; i++) { rejected.advance(); transaction.tick(); }
        } catch (IllegalStateException expected) { stopped = true; }
        check(stopped && rejected.clicks == 1, "Rejected pickup times out without replaying it");
        var denied = new Inventory(recipe, 2, 0);
        denied.book = denied.reject = true;
        transaction = new SurvivalCrafting(denied, recipe);
        stopped = false;
        try {
            for (int i = 0; i < 80; i++) { denied.advance(); transaction.tick(); }
        } catch (IllegalStateException expected) { stopped = true; }
        check(stopped && denied.requests == 1 && denied.clicks == 0, "Rejected recipe placement never falls through to manual clicks");
        System.out.println("Crafting transaction regressions passed.");
    }

    private static final class Inventory implements SurvivalCrafting.InventoryAccess {
        final SurvivalPlan.Recipe recipe;
        final int width, gridEnd;
        final List<Slot> slots = new ArrayList<>();
        ItemStack cursor = ItemStack.EMPTY;
        long clock, outputAt = Long.MAX_VALUE;
        int takes, clicks, requests;
        long placementAt = Long.MAX_VALUE;
        boolean reject, book;
        Inventory(SurvivalPlan.Recipe recipe, int width, int extra) {
            this.recipe = recipe; this.width = width; gridEnd = 1 + width * width;
            SimpleContainer container = new SimpleContainer(gridEnd + 36);
            for (int i = 0; i < container.getContainerSize(); i++) {
                Slot slot = new Slot(container, i, 0, 0); slot.index = i; slots.add(slot);
            }
            int index = gridEnd;
            for (var entry : recipe.ingredients().entrySet()) {
                slots.get(index++).set(new ItemStack(entry.getKey(), entry.getValue() + extra));
            }
        }
        void advance() {
            clock += 100;
            if (clock >= placementAt) {
                placementAt = Long.MAX_VALUE;
                for (int i = 0; i < recipe.cells().size(); i++) {
                    Item item = recipe.cells().get(i);
                    if (item == Items.AIR) continue;
                    Slot source = inventory().stream().filter(slot -> slot.getItem().is(item)).findFirst().orElseThrow();
                    grid().get(i / recipe.width() * width + i % recipe.width()).set(source.getItem().copyWithCount(1));
                    source.set(source.getItem().copyWithCount(source.getItem().getCount() - 1));
                }
                outputAt = clock + 600;
            }
            if (clock >= outputAt) { result().set(new ItemStack(recipe.output(), recipe.yield())); outputAt = Long.MAX_VALUE; }
        }
        public List<Slot> grid() { return slots.subList(1, gridEnd); }
        public List<Slot> inventory() { return slots.subList(gridEnd, slots.size()); }
        public Slot result() { return slots.getFirst(); }
        public ItemStack cursor() { return cursor; }
        public Slot slot(int id) { return slots.get(id); }
        public int width() { return width; }
        public boolean valid() { return true; }
        public long now() { return clock; }
        public boolean placeRecipe(SurvivalPlan.Recipe recipe) {
            if (!book) return false;
            requests++;
            if (!reject) placementAt = clock + 300;
            return true;
        }
        public void click(int id, int button, ContainerInput action) {
            clicks++;
            if (reject) return;
            Slot slot = slot(id);
            if (action == ContainerInput.QUICK_MOVE) {
                check(id == 0 && result().hasItem() && cursor.isEmpty(), "Only a received result can be collected");
                inventory().stream().filter(target -> !target.hasItem()).findFirst().orElseThrow().set(result().getItem().copy());
                result().set(ItemStack.EMPTY);
                grid().forEach(target -> target.set(ItemStack.EMPTY));
                takes++;
                return;
            }
            if (cursor.isEmpty()) { cursor = slot.getItem().copy(); slot.set(ItemStack.EMPTY); }
            else if (button == 1) {
                check(!slot.hasItem(), "Each recipe cell receives one ingredient");
                slot.set(cursor.copyWithCount(1)); cursor = cursor.copyWithCount(cursor.getCount() - 1);
            } else {
                check(!slot.hasItem(), "Remainder goes back into the empty source slot");
                slot.set(cursor.copy()); cursor = ItemStack.EMPTY;
            }
            if (matchesRecipe() && !result().hasItem() && outputAt == Long.MAX_VALUE) outputAt = clock + 600;
        }
        private boolean matchesRecipe() {
            for (int row = 0; row < width; row++) for (int col = 0; col < width; col++) {
                int cell = row * recipe.width() + col;
                Item expected = row < recipe.height() && col < recipe.width() ? recipe.cells().get(cell) : Items.AIR;
                ItemStack stack = grid().get(row * width + col).getItem();
                if (expected == Items.AIR ? !stack.isEmpty() : !stack.is(expected) || stack.getCount() != 1) return false;
            }
            return true;
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
