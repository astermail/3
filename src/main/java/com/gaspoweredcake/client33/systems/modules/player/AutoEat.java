/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.player;

import it.unimi.dsi.fastutil.booleans.BooleanBinaryOperator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import com.gaspoweredcake.client33.events.entity.player.ItemUseCrosshairTargetEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import net.minecraft.world.InteractionHand;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.combat.AnchorAura;
import com.gaspoweredcake.client33.systems.modules.combat.BedAura;
import com.gaspoweredcake.client33.systems.modules.combat.CrystalAura;
import com.gaspoweredcake.client33.systems.modules.combat.KillAura;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.player.InvUtils;
import com.gaspoweredcake.client33.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class AutoEat extends Module {
    @SuppressWarnings("unchecked")
    private static final Class<? extends Module>[] AURAS = new Class[]{CrystalAura.class, AnchorAura.class, BedAura.class};

    // Settings groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreshold = settings.createGroup("Threshold");

    // General
    public final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("blacklist")
        .description("Which items to not eat.")
        .defaultValue(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_APPLE,
            Items.CHORUS_FRUIT,
            Items.POISONOUS_POTATO,
            Items.PUFFERFISH,
            Items.CHICKEN,
            Items.ROTTEN_FLESH,
            Items.SPIDER_EYE,
            Items.SUSPICIOUS_STEW
        )
        .filter(Utils::isFood)
        .bypassFilterWhenSavingAndLoading()
        .build()
    );

    private final Setting<Boolean> pauseAuras = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-auras")
        .description("Pauses all auras when eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Pause baritone when eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("search-inventory")
        .description("Search the full inventory for food, not only the hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Priority> prioritise = sgGeneral.add(new EnumSetting.Builder<Priority>()
        .name("food-priority")
        .description("Which aspect of the food to prioritise selecting for.")
        .defaultValue(Priority.Saturation)
        .build()
    );

    // Threshold
    private final Setting<ThresholdMode> thresholdMode = sgThreshold.add(new EnumSetting.Builder<ThresholdMode>()
        .name("threshold-mode")
        .description("The threshold mode to trigger auto eat.\n'Both' == health AND hunger, 'Any' == health OR hunger")
        .defaultValue(ThresholdMode.Any)
        .build()
    );

    private final Setting<Double> healthThreshold = sgThreshold.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("The level of health you eat at.")
        .defaultValue(10)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Hunger)
        .build()
    );

    private final Setting<Integer> hungerThreshold = sgThreshold.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("The level of hunger you eat at.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Health)
        .build()
    );

    // An eating session owns one food slot until completion or cancellation.
    public boolean eating;
    private int slot = -1, prevSlot = -1;
    private int borrowedFrom = -1;
    private ItemStack displaced = ItemStack.EMPTY;
    private ItemStack borrowedFood = ItemStack.EMPTY;
    private final List<Class<? extends Module>> wasAura = new ReferenceArrayList<>();
    private boolean wasBaritone;

    public AutoEat() {
        super(Categories.Player, "auto-eat", "Selects suitable food and eats without interrupting other item use.");
    }

    @Override
    public void onDeactivate() {
        stopEating();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stopEating();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || !mc.player.isAlive() || mc.player.isSpectator()) {
            stopEating();
            return;
        }
        if (Modules.get().get(AutoGap.class).isEating() || com.gaspoweredcake.client33.utils.player.PotionUse.anyBusy()) return;
        if (mc.gui.screen() != null) {
            stopEating();
            return;
        }

        if (eating) {
            // Never chase changing scores mid-bite or overwrite a manual slot change.
            if ((slot != SlotUtils.OFFHAND && mc.player.getInventory().getSelectedSlot() != slot)
                || !thresholdReached() || !canEat(mc.player.getInventory().getItem(slot))) {
                stopEating();
                return;
            }
            useFood();
        } else if (!mc.player.isUsingItem() && !Input.isPressed(mc.options.keyUse) && thresholdReached()) {
            int candidate = findSlot();
            if (candidate != -1) startEating(candidate);
        }
    }

    @EventHandler
    private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
        if (eating) event.target = null;
    }

    private void startEating(int candidate) {
        if (SlotUtils.isMain(candidate) && (mc.player.containerMenu != mc.player.inventoryMenu
            || !mc.player.containerMenu.getCarried().isEmpty())) return;
        // Aura shutdown may restore a weapon slot, so finish it before selecting food.
        eating = true;
        wasAura.clear();
        if (pauseAuras.get()) {
            Modules.get().get(KillAura.class).pauseForEating();
            for (Class<? extends Module> klass : AURAS) {
                Module module = Modules.get().get(klass);
                if (module.isActive()) {
                    wasAura.add(klass);
                    module.toggle();
                }
            }
        }
        prevSlot = mc.player.getInventory().getSelectedSlot();
        slot = candidate;
        if (SlotUtils.isMain(candidate)) {
            // A complete inventory swap also works when every hotbar slot is occupied.
            int empty = InvUtils.find(ItemStack::isEmpty, SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END).slot();
            slot = empty == -1 ? prevSlot : empty;
            borrowedFrom = candidate;
            displaced = mc.player.getInventory().getItem(slot).copy();
            borrowedFood = mc.player.getInventory().getItem(candidate).copy();
            InvUtils.move().from(candidate).toHotbar(slot);
        }
        if (slot != SlotUtils.OFFHAND) InvUtils.swap(slot, false);
        if (!canEat(mc.player.getInventory().getItem(slot))) {
            stopEating();
            return;
        }

        if (pauseBaritone.get() && PathManagers.get().isPathing()) {
            wasBaritone = true;
            PathManagers.get().pause();
        }
        useFood();
    }

    private void useFood() {
        mc.options.keyUse.setDown(true);
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, slot == SlotUtils.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
    }

    /** Releases ownership before AutoGap starts its higher-priority eating session. */
    public void stopEating() {
        if (!eating) return;
        eating = false;
        mc.options.keyUse.setDown(Input.isPressed(mc.options.keyUse));
        if (mc.player != null) {
            boolean ownsSlot = mc.player.getInventory().getSelectedSlot() == (slot == SlotUtils.OFFHAND ? prevSlot : slot);
            InteractionHand hand = slot == SlotUtils.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (ownsSlot && mc.gameMode != null && mc.player.isUsingItem() && mc.player.getUsedItemHand() == hand) {
                mc.gameMode.releaseUsingItem(mc.player);
            }
            if (ownsSlot && borrowedFrom != -1 && mc.player.containerMenu == mc.player.inventoryMenu
                && mc.player.containerMenu.getCarried().isEmpty()) {
                ItemStack source = mc.player.getInventory().getItem(borrowedFrom);
                ItemStack food = mc.player.getInventory().getItem(slot);
                boolean sourceUnchanged = (source.isEmpty() && displaced.isEmpty())
                    || (source.getCount() == displaced.getCount() && ItemStack.isSameItemSameComponents(source, displaced));
                boolean foodUnchanged = food.isEmpty() || ItemStack.isSameItemSameComponents(food, borrowedFood);
                if (sourceUnchanged && foodUnchanged) InvUtils.move().from(borrowedFrom).toHotbar(slot);
            }
            if (ownsSlot && prevSlot >= 0) InvUtils.swap(prevSlot, false);
        }
        // Release exactly the resources this session acquired, even if settings changed.
        for (Class<? extends Module> klass : wasAura) Modules.get().get(klass).enable();
        wasAura.clear();
        if (wasBaritone) {
            wasBaritone = false;
            PathManagers.get().resume();
        }
        slot = prevSlot = borrowedFrom = -1;
        displaced = borrowedFood = ItemStack.EMPTY;
    }

    public boolean pausesKillAura() {
        return isActive() && eating && pauseAuras.get();
    }

    private boolean thresholdReached() {
        return thresholdMode.get().test(mc.player.getHealth() <= healthThreshold.get(),
            mc.player.getFoodData().getFoodLevel() <= hungerThreshold.get());
    }

    public boolean shouldEat() {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator() && thresholdReached() && findSlot() != -1;
    }

    private boolean canEat(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return !stack.isEmpty() && food != null && Utils.isFood(stack) && !blacklist.get().contains(stack.getItem())
            && (mc.player.getFoodData().needsFood() || food.canAlwaysEat());
    }

    private int findSlot() {
        if (canEat(mc.player.getOffhandItem())) return SlotUtils.OFFHAND;
        int best = findBestFood(SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END);
        if (best != -1 || !searchInventory.get()) return best;
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return -1;
        return findBestFood(SlotUtils.MAIN_START, SlotUtils.MAIN_END);
    }

    private int findBestFood(int start, int end) {
        int best = -1;
        float bestScore = -1;
        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!canEat(stack)) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            float score = prioritise.get().value(food);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public enum ThresholdMode {
        Health((health, _) -> health),
        Hunger((_, hunger) -> hunger),
        Any((health, hunger) -> health || hunger),
        Both((health, hunger) -> health && hunger);

        private final BooleanBinaryOperator predicate;

        ThresholdMode(BooleanBinaryOperator predicate) {
            this.predicate = predicate;
        }

        public boolean test(boolean health, boolean hunger) {
            return predicate.apply(health, hunger);
        }
    }

    public enum Priority {
        Combined,
        Hunger,
        Saturation;

        public float value(FoodProperties food) {
            return switch (this) {
                case Combined -> food.nutrition() + food.saturation();
                case Hunger -> food.nutrition();
                case Saturation -> food.saturation();
            };
        }
    }
}
