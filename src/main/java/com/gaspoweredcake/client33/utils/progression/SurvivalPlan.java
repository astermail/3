package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.*;

/** Replans from observed supplies: no speculative success counters or fixed-time crafting assumptions. */
public final class SurvivalPlan {
    public enum Kind { Mine, Craft, Smelt, Table, Furnace, Hunt, Wool, Done }
    public record Recipe(Item output, int yield, int width, List<Item> cells) {
        public int height() { return cells.size() / width; }
        public Map<Item, Integer> ingredients() {
            Map<Item, Integer> result = new LinkedHashMap<>();
            for (Item item : cells) if (item != Items.AIR) result.merge(item, 1, Integer::sum);
            return result;
        }
    }
    public record Step(Kind kind, Item item, int amount, Recipe recipe) {}
    public record Goal(Item item, int amount) {}
    public static final Map<Item, Recipe> RECIPES = new LinkedHashMap<>();
    static {
        add(Items.CRAFTING_TABLE, 1, 2, "PPPP"); add(Items.STICK, 4, 1, "PP");
        add(Items.WOODEN_PICKAXE, 1, 3, "PPP S  S "); add(Items.STONE_PICKAXE, 1, 3, "CCC S  S ");
        add(Items.IRON_PICKAXE, 1, 3, "III S  S "); add(Items.FURNACE, 1, 3, "CCCC CCCC");
        add(Items.DIAMOND_PICKAXE, 1, 3, "DDD S  S "); add(Items.DIAMOND_AXE, 1, 2, "DDDS S");
        add(Items.DIAMOND_SHOVEL, 1, 1, "DSS"); add(Items.DIAMOND_HOE, 1, 2, "DD S S");
        add(Items.DIAMOND_SWORD, 1, 1, "DDS"); add(Items.DIAMOND_HELMET, 1, 3, "DDDD D");
        add(Items.DIAMOND_CHESTPLATE, 1, 3, "D DDDDDDD"); add(Items.DIAMOND_LEGGINGS, 1, 3, "DDDD DD D");
        add(Items.DIAMOND_BOOTS, 1, 3, "D DD D"); add(Items.SHIELD, 1, 3, "PIPPPP P ");
        add(Items.BUCKET, 1, 3, "I I I "); add(Items.SHEARS, 1, 2, " II ");
        add(Items.CHEST, 1, 3, "PPPP PPPP"); add(Items.BED.white(), 1, 3, "WWWPPP");
        add(Items.TORCH, 4, 1, "KS");
    }
    private static void add(Item output, int yield, int width, String pattern) {
        List<Item> cells = new ArrayList<>();
        for (char c : pattern.toCharArray()) cells.add(switch(c) {
            case 'P' -> Items.OAK_PLANKS; case 'S' -> Items.STICK; case 'C' -> Items.COBBLESTONE;
            case 'I' -> Items.IRON_INGOT; case 'D' -> Items.DIAMOND; case 'W' -> Items.WOOL.white();
            case 'K' -> Items.COAL; default -> Items.AIR;
        });
        RECIPES.put(output, new Recipe(output, yield, width, List.copyOf(cells)));
    }
    public static List<Goal> goals(boolean supplies, int food, int spareDiamonds, int spareIron) {
        var goals = new ArrayList<Goal>();
        // Early essentials precede the long diamond-mining phase.
        goals.add(new Goal(Items.STONE_PICKAXE, 1));
        if (supplies) { goals.add(new Goal(Items.SHIELD, 1)); goals.add(new Goal(Items.TORCH, 32)); }
        if (food > 0) goals.add(new Goal(Items.COOKED_BEEF, food));
        if (supplies) goals.add(new Goal(Items.BED.white(), 1));
        for (Item item : List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)) goals.add(new Goal(item, 1));
        if (supplies) for (Item item : List.of(Items.BUCKET, Items.SHEARS, Items.CHEST)) goals.add(new Goal(item, 1));
        goals.add(new Goal(Items.DIAMOND, spareDiamonds)); goals.add(new Goal(Items.IRON_INGOT, spareIron));
        if (supplies) { goals.add(new Goal(Items.OAK_LOG, 16)); goals.add(new Goal(Items.COBBLESTONE, 64)); goals.add(new Goal(Items.TORCH, 64)); }
        return List.copyOf(goals);
    }
    private final Map<Item, Integer> counts;
    private final boolean table, furnace;
    public SurvivalPlan(Map<Item, Integer> counts, boolean table, boolean furnace) { this.counts = Map.copyOf(counts); this.table = table; this.furnace = furnace; }
    private int count(Item item) { return counts.getOrDefault(item, 0); }
    public Step next(List<Goal> goals) {
        for (var goal : goals) { Step step = need(goal.item, goal.amount); if (step != null) return step; }
        return new Step(Kind.Done, Items.AIR, 0, null);
    }
    private Step tool(int tier) {
        if (count(Items.DIAMOND_PICKAXE) > 0 || (tier <= 2 && count(Items.IRON_PICKAXE) > 0) || (tier <= 1 && count(Items.STONE_PICKAXE) > 0)
            || (tier == 0 && count(Items.WOODEN_PICKAXE) > 0)) return null;
        return need(tier == 2 ? Items.IRON_PICKAXE : tier == 1 ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE, 1);
    }
    private Step need(Item item, int amount) {
        if (count(item) >= amount) return null;
        // Superior picks satisfy the bootstrap goal, so a worn stone pick is not endlessly replaced.
        if (item == Items.STONE_PICKAXE && (count(Items.IRON_PICKAXE) > 0 || count(Items.DIAMOND_PICKAXE) > 0)) return null;
        if (item == Items.OAK_LOG) return new Step(Kind.Mine, item, amount, null);
        if (item == Items.OAK_PLANKS) {
            Step log = need(Items.OAK_LOG, 1); if (log != null) return log;
            return new Step(Kind.Craft, item, amount, new Recipe(item, 4, 1, List.of(Items.OAK_LOG)));
        }
        if (item == Items.COBBLESTONE || item == Items.COAL || item == Items.RAW_IRON || item == Items.DIAMOND) {
            Step pick = tool(item == Items.DIAMOND ? 2 : item == Items.COBBLESTONE ? 0 : 1);
            return pick != null ? pick : new Step(Kind.Mine, item, amount, null);
        }
        if (item == Items.WOOL.white()) {
            Step shears = need(Items.SHEARS, 1); return shears != null ? shears : new Step(Kind.Wool, item, amount, null);
        }
        if (item == Items.BEEF) return new Step(Kind.Hunt, item, amount, null);
        if (item == Items.IRON_INGOT || item == Items.COOKED_BEEF) {
            Step station = station(false); if (station != null) return station;
            int missing = Math.min(32, amount - count(item));
            Step raw = need(item == Items.IRON_INGOT ? Items.RAW_IRON : Items.BEEF, missing); if (raw != null) return raw;
            Step fuel = need(Items.COAL, (missing + 7) / 8); if (fuel != null) return fuel;
            return new Step(Kind.Smelt, item, missing, null);
        }
        Recipe recipe = RECIPES.get(item);
        if (recipe == null) throw new IllegalArgumentException("Unsupported survival goal: " + item);
        if (recipe.width > 2 || recipe.height() > 2) { Step station = station(true); if (station != null) return station; }
        for (var ingredient : recipe.ingredients().entrySet()) {
            Step missing = need(ingredient.getKey(), ingredient.getValue()); if (missing != null) return missing;
        }
        return new Step(Kind.Craft, item, amount, recipe);
    }
    private Step station(boolean crafting) {
        if (crafting ? table : furnace) return null;
        Item item = crafting ? Items.CRAFTING_TABLE : Items.FURNACE;
        Step supplies = need(item, 1);
        return supplies != null ? supplies : new Step(crafting ? Kind.Table : Kind.Furnace, item, 1, null);
    }
}
