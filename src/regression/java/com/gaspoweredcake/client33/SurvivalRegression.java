package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.progression.SurvivalPlan;
import com.gaspoweredcake.client33.utils.progression.SurvivalItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.*;

/** Simulates accepted gather/craft/smelt results to verify the full dependency graph and resource accounting. */
public final class SurvivalRegression {
    public static void run() {
        validateVanillaRecipes();
        validateInventoryAcknowledgement();
        var goals = SurvivalPlan.goals(true, 32, 8, 16);
        Map<Item, Integer> counts = new HashMap<>();
        boolean table = false, furnace = false, done = false;
        int diamondMined = 0, crafts = 0;
        for (int i = 0; i < 2000; i++) {
            var step = new SurvivalPlan(counts, table, furnace).next(goals);
            if (step.kind() == SurvivalPlan.Kind.Done) { done = true; break; }
            switch (step.kind()) {
                case Mine, Hunt, Wool -> {
                    if (step.item() == Items.DIAMOND) {
                        check(counts.getOrDefault(Items.IRON_PICKAXE, 0) > 0 || counts.getOrDefault(Items.DIAMOND_PICKAXE, 0) > 0, "Diamonds require a harvesting-tier pick");
                        diamondMined += step.amount() - counts.getOrDefault(step.item(), 0);
                    }
                    if (step.kind() == SurvivalPlan.Kind.Wool) check(counts.getOrDefault(Items.SHEARS, 0) > 0, "Shearing waits for shears");
                    counts.put(step.item(), step.amount());
                }
                case Craft -> {
                    var recipe = step.recipe();
                    check(recipe.width() <= 3 && recipe.height() <= 3 && recipe.cells().size() % recipe.width() == 0, "Recipe geometry fits vanilla grids");
                    check(table || recipe.width() <= 2 && recipe.height() <= 2, "Large recipes wait for a table");
                    recipe.ingredients().forEach((item, amount) -> consume(counts, item, amount));
                    counts.merge(recipe.output(), recipe.yield(), Integer::sum); crafts++;
                }
                case Smelt -> {
                    check(furnace, "Smelting waits for a furnace");
                    consume(counts, step.item() == Items.IRON_INGOT ? Items.RAW_IRON : Items.BEEF, step.amount());
                    consume(counts, Items.COAL, (step.amount() + 7) / 8);
                    counts.merge(step.item(), step.amount(), Integer::sum);
                }
                case Table -> { consume(counts, Items.CRAFTING_TABLE, 1); table = true; }
                case Furnace -> { consume(counts, Items.FURNACE, 1); furnace = true; }
                default -> throw new AssertionError("Unexpected step");
            }
        }
        check(done && crafts > 20 && table && furnace, "Empty inventory reaches the complete early survival kit without a dependency loop");
        check(diamondMined == 43 && counts.get(Items.DIAMOND) == 8, "Armor and every diamond tool consume exactly 35 diamonds, plus the requested spare eight");
        check(counts.get(Items.IRON_INGOT) == 16 && counts.get(Items.COOKED_BEEF) == 32 && counts.get(Items.TORCH) >= 64, "Spare resources and cooked food are part of completion");
        counts.remove(Items.DIAMOND_PICKAXE); counts.remove(Items.IRON_PICKAXE);
        check(new SurvivalPlan(counts, table, furnace).next(goals).kind() != SurvivalPlan.Kind.Done, "Lost or worn tools invalidate completion and trigger replacement");
        check(SurvivalItems.canonical(Items.NETHERITE_PICKAXE) == Items.DIAMOND_PICKAXE, "Superior tools satisfy the diamond goal");
        check(SurvivalItems.canonical(Items.SPRUCE_LOG) == Items.OAK_LOG && SurvivalItems.canonical(Items.CHERRY_PLANKS) == Items.OAK_PLANKS, "Wood families share the planner's ingredients");
        check(SurvivalItems.canonical(Items.COOKED_MUTTON) == Items.COOKED_BEEF, "Common cooked meats contribute to the food goal");
        System.out.println("Survival progression regressions passed.");
    }
    private static void validateInventoryAcknowledgement() {
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.OAK_LOG)).orElseThrow();
        if (!holder.areComponentsBound()) holder.bindComponents(net.minecraft.core.component.DataComponentMap.builder()
            .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
        var empty = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement.State(net.minecraft.world.item.ItemStack.EMPTY, List.of(new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 4)));
        var cursor = new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 4);
        var expected = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement.State(cursor, List.of(net.minecraft.world.item.ItemStack.EMPTY));
        var wait = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement(expected, 100);
        cursor.setCount(2);
        check(!wait.ready(empty, 200), "A delayed pickup acknowledgement does not immediately fail");
        check(!wait.ready(expected, 300), "A predicted cursor waits for stability");
        check(!wait.ready(empty, 350), "A transient server correction restarts the settling period");
        check(!wait.ready(expected, 400) && !wait.ready(expected, 500) && wait.ready(expected, 550), "Stable expected cursor and source complete acknowledgement");
        var rejected = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement(expected, 100);
        boolean timedOut = false;
        try { rejected.ready(empty, 5100); } catch (IllegalStateException e) { timedOut = true; }
        check(timedOut, "Persistently rejected clicks stop after a bounded wait instead of being replayed");
        var partial = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement.State(expected.cursor(), List.of(new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 4)));
        var partialWait = new com.gaspoweredcake.client33.utils.progression.InventoryAcknowledgement(expected, 0);
        check(!partialWait.ready(partial, 200), "Cursor-only synchronization cannot complete until the source slot matches too");
    }
    private static com.google.gson.JsonObject resource(String path) {
        try (var stream = SurvivalRegression.class.getResourceAsStream("/data/minecraft/" + path + ".json")) {
            check(stream != null, "Missing vanilla resource: " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static boolean accepts(com.google.gson.JsonElement ingredient, Item item) {
        if (ingredient.isJsonArray()) {
            for (var choice : ingredient.getAsJsonArray()) if (accepts(choice, item)) return true;
            return false;
        }
        String value = ingredient.isJsonObject() ? ingredient.getAsJsonObject().get("id").getAsString() : ingredient.getAsString();
        if (value.startsWith("#minecraft:")) return accepts(resource("tags/item/" + value.substring(11)).get("values"), item);
        return value.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
    }
    private static void validateVanillaRecipes() {
        for (var recipe : SurvivalPlan.RECIPES.values()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(recipe.output()).getPath();
            var vanilla = resource("recipe/" + path);
            var pattern = vanilla.getAsJsonArray("pattern");
            check(pattern.size() == recipe.height(), "Vanilla recipe height: " + path);
            var result = vanilla.getAsJsonObject("result");
            check(result.get("id").getAsString().equals("minecraft:" + path), "Vanilla output: " + path);
            check((result.has("count") ? result.get("count").getAsInt() : 1) == recipe.yield(), "Vanilla yield: " + path);
            for (int y = 0; y < pattern.size(); y++) {
                String row = pattern.get(y).getAsString();
                check(row.length() == recipe.width(), "Vanilla recipe width: " + path);
                for (int x = 0; x < row.length(); x++) {
                    Item item = recipe.cells().get(y * recipe.width() + x);
                    char key = row.charAt(x);
                    check(key == ' ' ? item == Items.AIR : accepts(vanilla.getAsJsonObject("key").get(String.valueOf(key)), item), "Vanilla ingredient: " + path + " at " + x + "," + y);
                }
            }
        }
    }
    private static void consume(Map<Item, Integer> counts, Item item, int amount) {
        int remaining = counts.getOrDefault(item, 0) - amount;
        check(remaining >= 0, "Never craft/smelt with unavailable ingredients: " + item);
        counts.put(item, remaining);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
