package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;

public final class SurvivalItems {
    private SurvivalItems() {}
    public static final List<Item> LOGS = List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.PALE_OAK_LOG);
    public static final List<Item> PLANKS = List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.PALE_OAK_PLANKS);
    private static final List<Item> DIAMOND = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
    private static final List<Item> NETHERITE = List.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
    public static Item canonical(Item item) {
        if (LOGS.contains(item)) return Items.OAK_LOG;
        if (PLANKS.contains(item)) return Items.OAK_PLANKS;
        if (item == Items.PORKCHOP || item == Items.MUTTON) return Items.BEEF;
        if (item == Items.COOKED_PORKCHOP || item == Items.COOKED_MUTTON) return Items.COOKED_BEEF;
        if (item == Items.COBBLED_DEEPSLATE || item == Items.BLACKSTONE) return Items.COBBLESTONE;
        if (item == Items.CHARCOAL) return Items.COAL;
        int tier = NETHERITE.indexOf(item);
        return tier < 0 ? item : DIAMOND.get(tier);
    }
    public static boolean matches(ItemStack stack, Item ingredient) { return !stack.isEmpty() && canonical(stack.getItem()) == ingredient; }
    public static Block[] mine(Item item) {
        if (item == Items.OAK_LOG) return LOGS.stream().map(value -> ((BlockItem) value).getBlock()).toArray(Block[]::new);
        if (item == Items.COBBLESTONE) return new Block[]{Blocks.STONE, Blocks.COBBLESTONE};
        if (item == Items.COAL) return new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE};
        if (item == Items.RAW_IRON) return new Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE};
        if (item == Items.DIAMOND) return new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE};
        throw new IllegalArgumentException("Not a mineable survival resource");
    }
}
