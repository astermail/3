package com.gaspoweredcake.client33.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalBlock;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;

/** One progression run exclusively owns its started Baritone process. */
public final class SurvivalBaritone implements AutoCloseable {
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private static SurvivalBaritone active;
    private final Map<baritone.api.Settings.Setting<?>, Change<?>> settings = new LinkedHashMap<>();
    private static final class Change<T> {
        final baritone.api.Settings.Setting<T> setting;
        final T original;
        T forced;
        Change(baritone.api.Settings.Setting<T> setting) { this.setting = setting; original = forced = setting.value; }
        boolean restore() { if (!Objects.equals(setting.value, forced)) return false; setting.value = original; return true; }
        void reapply() { if (Objects.equals(setting.value, original)) setting.value = forced; }
    }
    private IBaritoneProcess owned;
    private Object target;
    public boolean busy() {
        return baritone.getMineProcess().isActive() || baritone.getBuilderProcess().isActive() || baritone.getBuilderProcess().isPaused()
            || baritone.getCustomGoalProcess().isActive() || baritone.getFollowProcess().isActive() || baritone.getExploreProcess().isActive()
            || baritone.getGetToBlockProcess().isActive() || baritone.getFarmProcess().isActive() || baritone.getElytraProcess().isActive();
    }
    public void configure() {
        active = this;
        var s = BaritoneAPI.getSettings();
        set(s.allowInventory, true); set(s.autoTool, true); set(s.allowBreak, true); set(s.allowPlace, true);
        set(s.allowParkour, false); set(s.allowParkourPlace, false); set(s.allowSprint, false);
        set(s.maxFallHeightNoWater, Math.min(s.maxFallHeightNoWater.value, 2));
        set(s.maxFallHeightBucket, Math.min(s.maxFallHeightBucket.value, 2));
        set(s.allowDiagonalDescend, false); set(s.followRadius, 1); set(s.followOffsetDistance, 0.0);
        set(s.itemSaver, true); set(s.itemSaverThreshold, 16); set(s.mineScanDroppedItems, true);
    }
    @SuppressWarnings("unchecked")
    private <T> void set(baritone.api.Settings.Setting<T> setting, T value) {
        var change = (Change<T>) settings.computeIfAbsent(setting, key -> new Change<>(setting));
        if (!Objects.equals(setting.value, change.forced)) throw new IllegalStateException("Baritone settings changed manually. Resume to use the new settings.");
        setting.value = change.forced = value;
    }
    public static void withOriginals(Runnable action) {
        var run = active;
        if (run == null) { action.run(); return; }
        var restored = run.settings.values().stream().filter(Change::restore).toList();
        try { action.run(); } finally { restored.forEach(Change::reapply); }
    }
    public boolean interrupted() {
        return baritone.getPathingControlManager().mostRecentInControl().filter(p -> p.isActive() && !p.isTemporary() && p != owned).isPresent();
    }
    public void mine(Object key, Block[] blocks, Integer depth) {
        if (key.equals(target) && owned != null && owned.isActive()) return;
        stop(); var s = BaritoneAPI.getSettings(); set(s.legitMine, depth != null); if (depth != null) set(s.legitMineYLevel, depth); target = key; owned = baritone.getMineProcess(); baritone.getMineProcess().mine(blocks);
    }
    public void travel(BlockPos pos) {
        if (pos.equals(target) && owned != null && owned.isActive()) return;
        stop(); target = pos.immutable(); owned = baritone.getCustomGoalProcess(); baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 1));
    }
    public void collect(BlockPos pos) {
        var goal = new GoalBlock(pos);
        if (goal.equals(target) && owned != null && owned.isActive()) return;
        stop(); target = goal; owned = baritone.getCustomGoalProcess(); baritone.getCustomGoalProcess().setGoalAndPath(goal);
    }
    public void follow(Entity entity) {
        if (entity.getUUID().equals(target) && owned != null && owned.isActive()) return;
        stop(); target = entity.getUUID(); owned = baritone.getFollowProcess(); baritone.getFollowProcess().follow(candidate -> candidate == entity);
    }
    public void stop() {
        if (owned != null) owned.onLostControl();
        owned = null; target = null;
    }
    @Override public void close() { stop(); settings.values().forEach(Change::restore); settings.clear(); if (active == this) active = null; }
}
