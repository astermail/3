package com.gaspoweredcake.client33.utils.player;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static com.gaspoweredcake.client33.Client33.mc;

/** Owns one hotbar use session without borrowing the global inventory swap-back slot. */
public final class PotionUse {
    private static PotionUse active;
    private Object player, world;
    private HotbarOwnership ownership;
    private long generation;
    private int original, selected, ticks;
    private InteractionHand hand;
    private Item item;
    private boolean consuming, sawUse, started;
    public static boolean healing(ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || !(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION))) return false;
        boolean heals = false;
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return false;
            if (effect.getEffect().equals(MobEffects.INSTANT_HEALTH) || effect.getEffect().equals(MobEffects.REGENERATION)) heals = true;
        }
        return heals;
    }
    public static boolean debuff(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (var effect : contents.getAllEffects()) if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
        return false;
    }
    public boolean busy() { return active == this; }
    public static boolean anyBusy() { return active != null; }
    public boolean start(FindItemResult found, double yaw, double pitch) {
        if (active != null || mc.player == null || mc.gameMode == null || mc.gui.screen() != null
            || mc.player.isUsingItem()
            || com.gaspoweredcake.client33.systems.modules.Modules.get().get(com.gaspoweredcake.client33.systems.modules.player.AutoEat.class).eating
            || com.gaspoweredcake.client33.systems.modules.Modules.get().get(com.gaspoweredcake.client33.systems.modules.player.AutoGap.class).isEating()
            || !found.found() || (!found.isHotbar() && !found.isOffhand())) return false;
        player = mc.player; world = mc.level;
        original = mc.player.getInventory().getSelectedSlot();
        selected = found.isOffhand() ? original : found.slot();
        hand = found.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = found.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(selected);
        item = stack.getItem(); consuming = !stack.is(Items.SPLASH_POTION); sawUse = false; started = false; ticks = 0;
        active = this;
        ownership = new HotbarOwnership(player, world, original, selected);
        long session = ++generation;
        Rotations.rotate(yaw, pitch, 50, () -> {
            if (generation != session || active != this) return;
            if (!ownership.mayStart(mc.player, mc.level, mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot()) || mc.gui.screen() != null
                || mc.player.isUsingItem()
                || (hand == InteractionHand.OFF_HAND ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(selected)).getItem() != item) { stop(); return; }
            InvUtils.swap(selected, false);
            started = true;
            if (consuming) mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, hand);
            sawUse = mc.player.isUsingItem();
            if (!consuming) stop();
        });
        return true;
    }
    public void tick() {
        if (!busy()) return;
        if (mc.player != player || mc.level != world || mc.gui.screen() != null || !mc.player.isAlive() || ++ticks > 80) { stop(); return; }
        if (!started) { if (ticks > 5 || mc.player.getInventory().getSelectedSlot() != original) stop(); return; }
        if (mc.player.getInventory().getSelectedSlot() != selected) { stop(); return; }
        if (consuming) {
            sawUse |= mc.player.isUsingItem();
            if (sawUse && !mc.player.isUsingItem() || ticks > 5 && !sawUse) { stop(); return; }
            mc.options.keyUse.setDown(true);
        }
    }
    public void stop() {
        if (!busy()) return;
        if (started && mc.player == player && mc.level == world) {
            if (consuming) {
                mc.options.keyUse.setDown(mc.gui.screen() == null && com.gaspoweredcake.client33.utils.misc.input.Input.isPressed(mc.options.keyUse));
                if (mc.player.isUsingItem() && mc.player.getUsedItemHand() == hand && mc.player.getUseItem().getItem() == item
                    && mc.player.getInventory().getSelectedSlot() == selected) mc.gameMode.releaseUsingItem(mc.player);
            }
            if (ownership.mayRestore(mc.player, mc.level, mc.player.getInventory().getSelectedSlot())) InvUtils.swap(original, false);
        }
        generation++;
        active = null; player = world = null; ownership = null;
    }
}
