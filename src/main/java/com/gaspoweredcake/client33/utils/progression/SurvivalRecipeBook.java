package com.gaspoweredcake.client33.utils.progression;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.crafting.display.*;

import static com.gaspoweredcake.client33.Client33.mc;

/** Uses the server's unlocked recipes and ingredient selection, including wood variants. */
public final class SurvivalRecipeBook {
    private SurvivalRecipeBook() {}

    public static boolean place(AbstractCraftingMenu menu, SurvivalPlan.Recipe recipe) {
        var contents = new StackedItemContents();
        mc.player.getInventory().fillStackedContents(contents);
        var context = SlotDisplayContext.fromLevel(mc.level);
        for (var collection : mc.player.getRecipeBook().getCollections()) {
            for (var entry : collection.getRecipes()) {
                boolean fits = switch (entry.display()) {
                    case ShapedCraftingRecipeDisplay shaped -> shaped.width() <= menu.getGridWidth() && shaped.height() <= menu.getGridHeight();
                    case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients().size() <= menu.getInputGridSlots().size();
                    default -> false;
                };
                if (!fits || !entry.canCraft(contents)) continue;
                if (entry.resultItems(context).stream().noneMatch(stack -> SurvivalItems.matches(stack, recipe.output()) && stack.getCount() == recipe.yield())) continue;
                mc.gameMode.handlePlaceRecipe(menu.containerId, entry.id(), false);
                return true;
            }
        }
        return false;
    }
}
