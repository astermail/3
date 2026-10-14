package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.pathing.SurvivalBaritone;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.*;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.misc.Names;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import com.gaspoweredcake.client33.utils.player.*;
import com.gaspoweredcake.client33.utils.progression.*;
import com.gaspoweredcake.client33.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Explicit Start/Stop survival progression; active config restoration never starts an unattended job. */
public class SurvivalBot extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Boolean> essentials = sg.add(new BoolSetting.Builder().name("survival-essentials").description("Also make a bed, shield, bucket, shears, chest, torches and building supplies.").defaultValue(true).build());
    private final Setting<Integer> food = sg.add(new IntSetting.Builder().name("cooked-food").description("Cooked beef/pork/mutton to carry. Requires reachable animals.").defaultValue(32).range(0, 64).build());
    private final Setting<Integer> diamonds = sg.add(new IntSetting.Builder().name("spare-diamonds").description("Loose diamonds after the complete armor and tool kit.").defaultValue(8).range(0, 64).build());
    private final Setting<Integer> iron = sg.add(new IntSetting.Builder().name("spare-iron").description("Extra ingots after crafting tools and essentials.").defaultValue(16).range(0, 64).build());
    private final Setting<Double> health = sg.add(new DoubleSetting.Builder().name("minimum-health").description("Pause below this health instead of continuing an unsafe run.").defaultValue(8).range(1, 20).build());
    private final Setting<Integer> timeout = sg.add(new IntSetting.Builder().name("no-progress-seconds").description("Pause when gathering produces no inventory progress for this long.").defaultValue(180).range(30, 900).build());
    private final Setting<Boolean> spoil = sg.add(new BoolSetting.Builder().name("discard-mining-spoil").description("Free space by dropping excess common stone/dirt/gravel. Keeps at least 64 and any quantity carried at Start; never drops tools or valuables.").defaultValue(true).build());
    private SurvivalBaritone baritone;
    private SurvivalPlan.Step step;
    private SurvivalCrafting crafting;
    private SurvivalSmelting smelting;
    private AbstractContainerMenu ownedMenu;
    private Object level, player;
    private BlockPos home, table, furnace, chest, pendingBlock, wanderGoal;
    private Block pendingType;
    private long openUntil, lastProgress, lastAction, generation, lastHunt;
    private int signature, ticks, wander;
    private boolean running, eating;
    private int eatingSlot, oldSlot;
    private final Map<Item, Integer> startingItems = new HashMap<>();
    private String status = "Click Start in settings";
    private WLabel statusLabel;
    public SurvivalBot() { super(Categories.World, "survival-bot", "Baritone progression from wood to a diamond kit and an early survival camp. Start explicitly in settings.", "Auto Mine", "Diamond Kit", "Auto Progression"); }
    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        list.add(theme.label("Start here on solid Overworld ground. This becomes your camp.", 390)).expandX();
        list.add(theme.label("Makes all five diamond tools/weapons and four armor pieces. Requires Baritone; server recipes and reachable resources matter.", 390)).expandX();
        statusLabel = list.add(theme.label(status, 390)).expandX().widget();
        var buttons = list.add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("Start / Resume")).expandX().widget().action = () -> { if (!isActive()) toggle(); start(); };
        buttons.add(theme.button("Stop")).expandX().widget().action = () -> pause("Stopped. Click Start / Resume when ready.");
        return list;
    }
    private void status(String value) { status = value; if (statusLabel != null) statusLabel.set(value); }
    @Override public String getInfoString() { return status; }
    @Override public void onActivate() { running = false; status("Click Start in settings"); }
    @Override public void onDeactivate() { pause("Stopped"); statusLabel = null; }
    @EventHandler private void leave(GameLeftEvent event) { pause("Disconnected. Start a new run after joining."); level = player = null; home = table = furnace = chest = null; }
    private String conflict() {
        for (String name : List.of("flight", "freecam", "kill-aura", "silent-aura", "auto-armor", "armor-switch", "inv-cleaner", "inventory-tweaks", "auto-eat", "auto-gap", "auto-heal", "auto-replenish", "refill", "scaffold", "blink", "fake-lag", "back-track")) {
            Module module = Modules.get().get(name);
            if (module != null && module.isActive()) return module.title;
        }
        return null;
    }
    private void start() {
        if (running) { status("Already running: " + status); return; }
        if (mc.player == null || mc.level == null || mc.gameMode == null) { status("Join a world first."); return; }
        if (!PathManagers.get().getName().equals("Baritone")) { status("Install a compatible Baritone for Minecraft 26.2."); return; }
        if (mc.level.dimension() != Level.OVERWORLD || !mc.player.onGround() || !mc.player.isAlive()) { status("Start alive on solid Overworld ground."); return; }
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()
            || mc.player.inventoryMenu.getInputGridSlots().stream().anyMatch(Slot::hasItem)) { status("Close containers and clear the cursor/crafting grid first."); return; }
        String conflict = conflict(); if (conflict != null) { status("Turn off " + conflict + " before starting Survival Bot."); return; }
        SurvivalBaritone candidate = new SurvivalBaritone();
        if (candidate.busy()) { status("Finish or cancel the current Baritone job first."); return; }
        if (level != mc.level || home == null) { home = mc.player.blockPosition(); table = furnace = chest = null; }
        level = mc.level; player = mc.player; baritone = candidate; baritone.configure(); PathManagers.get().resume();
        startingItems.clear(); for (int i = 0; i < 36; i++) { var stack = mc.player.getInventory().getItem(i); startingItems.merge(stack.getItem(), stack.getCount(), Integer::sum); }
        step = null; crafting = null; smelting = null; ownedMenu = null; pendingBlock = null; wanderGoal = null; wander = ticks = 0;
        signature = counts().hashCode(); lastProgress = CombatTelemetry.now(); lastAction = 0; running = true; generation++;
        mc.gui.setScreen(null); status("Starting survival progression");
    }
    private void pause(String reason) {
        generation++; running = false;
        if (baritone != null) { baritone.close(); baritone = null; }
        if (mc.player == player && mc.level == level) {
            stopEating();
            if (crafting != null) crafting.cancel(); if (smelting != null) smelting.cancel();
            closeOwnedMenu();
        }
        if (eating) mc.options.keyUse.setDown(Input.isPressed(mc.options.keyUse));
        eating = false; crafting = null; smelting = null; step = null; pendingBlock = null; ownedMenu = null;
        status(reason);
    }
    private void closeOwnedMenu() {
        if (ownedMenu != null && mc.player != null && mc.player.containerMenu == ownedMenu && ownedMenu.getCarried().isEmpty()) mc.player.closeContainer();
        ownedMenu = null;
    }
    private Map<Item, Integer> counts() {
        Map<Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < 36; i++) count(counts, mc.player.getInventory().getItem(i));
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) count(counts, mc.player.getItemBySlot(slot));
        if (mc.player.getOffhandItem().is(Items.SHIELD)) count(counts, mc.player.getOffhandItem());
        if (known(chest, Blocks.CHEST)) counts.merge(Items.CHEST, 1, Integer::sum);
        return counts;
    }
    private void count(Map<Item, Integer> counts, ItemStack stack) {
        if (stack.isEmpty() || stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 20) return;
        counts.merge(SurvivalItems.canonical(stack.getItem()), stack.getCount(), Integer::sum);
    }
    private boolean known(BlockPos pos, Block block) { return pos != null && (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || mc.level.getBlockState(pos).is(block)); }
    @EventHandler private void tick(TickEvent.Post event) {
        if (!running) return;
        try {
            if (mc.player != player || mc.level != level || !mc.player.isAlive()) { pause("World/player changed or died. Start again when ready."); return; }
            if (mc.player.getHealth() < health.get() || mc.player.isOnFire() || mc.player.isInLava()) { pause("Unsafe health/fire. Recover, then Start / Resume."); return; }
            String conflict = conflict(); if (conflict != null) { pause("Conflicting module enabled: " + conflict + ". Turn it off, then resume."); return; }
            if (baritone.interrupted()) { pause("Another Baritone process took control."); return; }
            if (mc.player.containerMenu != mc.player.inventoryMenu && mc.player.containerMenu != ownedMenu) {
                if (pendingBlock != null && CombatTelemetry.now() < openUntil && (pendingType == Blocks.CRAFTING_TABLE && mc.player.containerMenu instanceof CraftingMenu
                    || pendingType == Blocks.FURNACE && mc.player.containerMenu instanceof FurnaceMenu)) { ownedMenu = mc.player.containerMenu; pendingBlock = null; }
                else { pause("Another container was opened. Resume after closing it."); return; }
            }
            if (mc.gui.screen() != null && ownedMenu != null && (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || screen.getMenu() != ownedMenu)) { pause("Screen changed during crafting/smelting."); return; }
            if (mc.gui.screen() != null && ownedMenu == null) { baritone.stop(); lastProgress = CombatTelemetry.now(); return; }
            if (++ticks % 2 != 0) return;
            if (eating) { eatTick(); return; }
            if (crafting != null) { if (crafting.tick()) { crafting = null; closeOwnedMenu(); step = null; } return; }
            if (smelting != null) {
                progress();
                if (CombatTelemetry.now() - lastProgress > timeout.get() * 1000L) { pause("Furnace stalled. Check fuel, recipe and server protection."); return; }
                if (smelting.tick()) { smelting = null; closeOwnedMenu(); step = null; }
                return;
            }
            if (ownedMenu == null && mc.player.isUsingItem()) { baritone.stop(); lastProgress = CombatTelemetry.now(); return; }
            if (ownedMenu == null && mc.player.getFoodData().getFoodLevel() < 16 && eat()) return;
            if (mc.player.getFoodData().getFoodLevel() <= 2) { pause("Food exhausted. Eat before resuming."); return; }
            if (ownedMenu == null && freeSlots() < 4) {
                baritone.stop(); if (discardSpoil()) return;
                pause("Need four free inventory slots. Store items, then resume."); return;
            }
            if (ownedMenu == null && equip()) { baritone.stop(); return; }
            progress();
            if (CombatTelemetry.now() - lastProgress > timeout.get() * 1000L) { pause("No progress: " + status + ". Check reachable resources, then resume."); return; }
            if (step == null) {
                var goals = SurvivalPlan.goals(essentials.get(), food.get(), diamonds.get(), iron.get());
                if (mc.player.getFoodData().getFoodLevel() < 8 && food.get() > 0) goals = SurvivalPlan.goals(false, 4, 0, 0).subList(0, 2);
                step = new SurvivalPlan(counts(), known(table, Blocks.CRAFTING_TABLE), known(furnace, Blocks.FURNACE)).next(goals);
                lastAction = CombatTelemetry.now();
            }
            status(step.kind() + ": " + Names.get(step.item()));
            switch (step.kind()) {
                case Mine -> {
                    if (counts().getOrDefault(step.item(), 0) >= step.amount()) { baritone.stop(); step = null; }
                    else if (!hasMiningTool(step.item())) { baritone.stop(); step = null; }
                    else baritone.mine(step.item(), SurvivalItems.mine(step.item()), step.item() == Items.DIAMOND ? -54 : step.item() == Items.RAW_IRON ? 16 : step.item() == Items.COAL ? 64 : null);
                }
                case Table -> { if (station(Blocks.CRAFTING_TABLE, false)) step = null; }
                case Furnace -> { if (station(Blocks.FURNACE, false)) step = null; }
                case Craft -> craft();
                case Smelt -> smelt();
                case Hunt, Wool -> gatherAnimals();
                case Done -> finish();
            }
        } catch (RuntimeException failure) { pause("Paused: " + failure.getMessage()); }
    }
    private boolean hasMiningTool(Item resource) {
        if (resource == Items.OAK_LOG) return true;
        var count = counts();
        return count.getOrDefault(Items.DIAMOND_PICKAXE, 0) > 0 || count.getOrDefault(Items.IRON_PICKAXE, 0) > 0
            || resource != Items.DIAMOND && count.getOrDefault(Items.STONE_PICKAXE, 0) > 0
            || resource == Items.COBBLESTONE && count.getOrDefault(Items.WOODEN_PICKAXE, 0) > 0;
    }
    private void progress() {
        int now = counts().hashCode(); if (now != signature) { signature = now; lastProgress = CombatTelemetry.now(); }
    }
    private int freeSlots() { int free = 0; for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++; return free; }
    private boolean discardSpoil() {
        if (!spoil.get()) return false;
        for (Item item : List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL)) {
            int total = 0; for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) total += mc.player.getInventory().getItem(i).getCount();
            int keep = Math.max(64, startingItems.getOrDefault(item, 0));
            for (int i = 0; i < 36; i++) { var stack = mc.player.getInventory().getItem(i); if (stack.is(item) && total - stack.getCount() >= keep) { InvUtils.drop().slot(i); status("Discarding excess mining spoil"); return true; } }
        }
        return false;
    }
    private boolean equip() {
        var armor = Map.of(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, EquipmentSlot.FEET, Items.DIAMOND_BOOTS);
        for (var entry : armor.entrySet()) {
            if (SurvivalItems.matches(mc.player.getItemBySlot(entry.getKey()), entry.getValue())) continue;
            for (int i = 0; i < 36; i++) if (SurvivalItems.matches(mc.player.getInventory().getItem(i), entry.getValue())) { InvUtils.move().from(i).toArmor(entry.getKey().getIndex()); return true; }
        }
        if (mc.player.getOffhandItem().isEmpty()) for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) { InvUtils.move().from(i).toOffhand(); return true; }
        return false;
    }
    private int hotbar(Item item) {
        for (int i = 0; i < 9; i++) if (SurvivalItems.matches(mc.player.getInventory().getItem(i), item)) return i;
        for (int i = 9; i < 36; i++) if (SurvivalItems.matches(mc.player.getInventory().getItem(i), item)) { InvUtils.move().from(i).toHotbar(8); return -1; }
        throw new IllegalStateException("Missing " + Names.get(item));
    }
    private boolean eat() {
        if (counts().getOrDefault(Items.COOKED_BEEF, 0) == 0 || !mc.player.containerMenu.getCarried().isEmpty()) return false;
        baritone.stop(); int slot = hotbar(Items.COOKED_BEEF); if (slot < 0) return true;
        oldSlot = mc.player.getInventory().getSelectedSlot(); eatingSlot = slot; InvUtils.swap(slot, false);
        mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); eating = true; lastAction = CombatTelemetry.now(); return true;
    }
    private void eatTick() {
        if (mc.player.getInventory().getSelectedSlot() != eatingSlot || CombatTelemetry.now() - lastAction > 4000 || !mc.player.isUsingItem()) { stopEating(); step = null; return; }
        mc.options.keyUse.setDown(true); status("Eating cooked food"); lastProgress = CombatTelemetry.now();
    }
    private void stopEating() {
        if (!eating) return;
        mc.options.keyUse.setDown(Input.isPressed(mc.options.keyUse));
        if (mc.player.getInventory().getSelectedSlot() == eatingSlot) { if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player); InvUtils.swap(oldSlot, false); }
        eating = false;
    }
    private boolean near(BlockPos pos) { return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= Math.pow(Math.min(4.5, mc.player.blockInteractionRange()), 2); }
    private boolean station(Block block, boolean open) {
        BlockPos pos = block == Blocks.CRAFTING_TABLE ? table : block == Blocks.FURNACE ? furnace : chest;
        if (!known(pos, block)) pos = null;
        if (pos == null) {
            if (mc.player.blockPosition().distSqr(home) > 9) { baritone.travel(home); return false; }
            baritone.stop();
            if (CombatTelemetry.now() - lastAction < 500) return false;
            int slot = hotbar(block.asItem()); if (slot < 0) return false;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                BlockPos candidate = home.offset(dx, dy, dz);
                if (!near(candidate) || !mc.level.getBlockState(candidate.below()).isSolidRender() || !BlockUtils.canPlaceBlock(candidate, true, block)) continue;
                if (BlockUtils.place(candidate, new FindItemResult(slot, 1), false, 50, true)) {
                    if (block == Blocks.CRAFTING_TABLE) table = candidate; else if (block == Blocks.FURNACE) furnace = candidate; else chest = candidate;
                    lastAction = CombatTelemetry.now(); return false;
                }
            }
            throw new IllegalStateException("No safe space for the camp workstation. Clear ground near Start.");
        }
        if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !near(pos)) { baritone.travel(pos); return false; }
        baritone.stop();
        if (!open) return true;
        if (pendingBlock != null) {
            if (CombatTelemetry.now() >= openUntil) throw new IllegalStateException("Workstation did not open; check server protections.");
            return false;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(mc.player.getEyePosition(), center,
            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (!hit.getBlockPos().equals(pos)) { baritone.travel(pos); return false; }
        pendingBlock = pos; pendingType = block; openUntil = CombatTelemetry.now() + 5000;
        BlockUtils.interact(hit, InteractionHand.MAIN_HAND, true);
        return false;
    }
    private void craft() {
        if (step.recipe().width() <= 2 && step.recipe().height() <= 2) {
            baritone.stop(); closeOwnedMenu(); ownedMenu = mc.player.inventoryMenu;
            crafting = new SurvivalCrafting(mc.player.inventoryMenu, step.recipe());
        } else if (ownedMenu instanceof CraftingMenu menu) crafting = new SurvivalCrafting(menu, step.recipe());
        else if (!known(table, Blocks.CRAFTING_TABLE)) step = null;
        else station(Blocks.CRAFTING_TABLE, true);
    }
    private void smelt() {
        if (ownedMenu instanceof FurnaceMenu menu) {
            for (int i = 0; i < 3; i++) if (menu.getSlot(i).hasItem()) { InvUtils.shiftClick().slotId(i); return; }
            smelting = new SurvivalSmelting(menu, step.item(), step.amount());
        } else if (!known(furnace, Blocks.FURNACE)) step = null;
        else station(Blocks.FURNACE, true);
    }
    private void gatherAnimals() {
        if (counts().getOrDefault(step.item(), 0) >= step.amount()) { baritone.stop(); step = null; return; }
        ItemEntity drop = null; LivingEntity animal = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && SurvivalItems.matches(item.getItem(), step.item()) && (drop == null || mc.player.distanceToSqr(item) < mc.player.distanceToSqr(drop))) drop = item;
            if (entity instanceof LivingEntity living && living.isAlive() && !living.hasCustomName() && (step.kind() == SurvivalPlan.Kind.Wool
                ? living instanceof Sheep sheep && sheep.getColor() == DyeColor.WHITE && sheep.readyForShearing()
                : (living instanceof Cow || living instanceof Pig || living instanceof Sheep) && !living.isBaby())
                && (animal == null || mc.player.distanceToSqr(living) < mc.player.distanceToSqr(animal))) animal = living;
        }
        if (drop != null) { baritone.collect(drop.blockPosition()); return; }
        if (animal == null) {
            if (wanderGoal == null || mc.player.blockPosition().distSqr(wanderGoal) < 9) {
                int[][] directions = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
                int radius = Math.min(128, 24 + wander / 8 * 24); var d = directions[wander++ % 8]; wanderGoal = home.offset(d[0] * radius, 0, d[1] * radius);
            }
            baritone.travel(wanderGoal); status("Searching loaded terrain for " + (step.kind() == SurvivalPlan.Kind.Wool ? "white sheep" : "food animals")); return;
        }
        if (mc.player.getEyePosition().distanceToSqr(animal.getBoundingBox().getCenter()) > Math.pow(Math.min(3, mc.player.entityInteractionRange()), 2) || !mc.player.hasLineOfSight(animal)) { baritone.follow(animal); return; }
        baritone.stop();
        if (CombatTelemetry.now() - lastHunt < 1000) return;
        int slot = step.kind() == SurvivalPlan.Kind.Wool ? hotbar(Items.SHEARS) : huntingSlot(); if (slot < 0) return;
        int original = mc.player.getInventory().getSelectedSlot(); InvUtils.swap(slot, false);
        LivingEntity target = animal; long session = generation; Object currentLevel = level; var actionStep = step; Item held = mc.player.getMainHandItem().getItem(); lastHunt = CombatTelemetry.now();
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 25, () -> {
            if (!running || generation != session || mc.level != currentLevel || mc.player != player || !target.isAlive()
                || step != actionStep || mc.player.getMainHandItem().getItem() != held || mc.player.getInventory().getSelectedSlot() != slot || !mc.player.hasLineOfSight(target)
                || mc.player.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter()) > Math.pow(Math.min(3, mc.player.entityInteractionRange()), 2)) return;
            if (actionStep.kind() == SurvivalPlan.Kind.Wool) mc.gameMode.interact(mc.player, target, new EntityHitResult(target, target.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND);
            else if (mc.player.getAttackStrengthScale(0) >= 1) { mc.gameMode.attack(mc.player, target); mc.player.swing(InteractionHand.MAIN_HAND); }
            if (mc.player.getInventory().getSelectedSlot() == slot) InvUtils.swap(original, false);
        });
    }
    private int huntingSlot() {
        for (Item weapon : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) if (counts().getOrDefault(weapon, 0) > 0) return hotbar(weapon);
        return mc.player.getInventory().getSelectedSlot();
    }
    private void finish() {
        var remaining = new SurvivalPlan(counts(), known(table, Blocks.CRAFTING_TABLE), known(furnace, Blocks.FURNACE))
            .next(SurvivalPlan.goals(essentials.get(), food.get(), diamonds.get(), iron.get()));
        if (remaining.kind() != SurvivalPlan.Kind.Done) { step = remaining; return; }
        if (essentials.get() && !known(chest, Blocks.CHEST)) { if (!station(Blocks.CHEST, false)) return; }
        if (mc.player.blockPosition().distSqr(home) > 4) { baritone.travel(home); status("Kit ready. Returning to camp"); return; }
        pause("Complete: diamond kit and selected supplies ready at camp.");
        info("Survival kit complete. Camp: %s. The bed and empty bucket are in your inventory.", home.toShortString());
    }
}
