/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.world;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import com.gaspoweredcake.client33.events.entity.player.BlockBreakingCooldownEvent;
import com.gaspoweredcake.client33.events.client33.KeyInputEvent;
import com.gaspoweredcake.client33.events.client33.MouseClickEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import com.gaspoweredcake.client33.utils.misc.ListMode;
import com.gaspoweredcake.client33.utils.misc.Names;
import com.gaspoweredcake.client33.utils.misc.input.KeyAction;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.player.Rotations;
import com.gaspoweredcake.client33.utils.render.RenderUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import com.gaspoweredcake.client33.utils.world.BlockIterator;
import com.gaspoweredcake.client33.utils.world.MiningQueue;
import com.gaspoweredcake.client33.utils.world.PacketMiningProgress;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import net.minecraft.world.level.block.state.BlockState;
import com.gaspoweredcake.client33.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Nuker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Selection> selection = sgGeneral.add(new EnumSetting.Builder<Selection>()
        .name("selection").description("Selected Block waits for a right-click; Area mines every allowed block.")
        .defaultValue(Selection.SelectedBlock).onChanged(value -> resetMining()).build());
    private final Setting<Integer> mineDelay = sgGeneral.add(new IntSetting.Builder()
        .name("mine-delay-ms").description("Minimum delay between completed blocks, in milliseconds.")
        .defaultValue(100).range(0, 5000).sliderMax(500).build());
    private final Setting<Boolean> distanceCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-check").description("Restrict targets to the player's normal block interaction reach.")
        .defaultValue(true).build());
    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent").description("Rotate server-side only; disable to turn the camera toward the block.")
        .defaultValue(true).visible(this::rotating).build());

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape")
        .description("The shape of nuking algorithm.")
        .defaultValue(Shape.Sphere)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The way the blocks are broken.")
        .defaultValue(Mode.All)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum scan distance in blocks.")
        .defaultValue(4)
        .range(0, 16)
        .visible(() -> shape.get() != Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_up = sgGeneral.add(new IntSetting.Builder()
        .name("up")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_down = sgGeneral.add(new IntSetting.Builder()
        .name("down")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_left = sgGeneral.add(new IntSetting.Builder()
        .name("left")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_right = sgGeneral.add(new IntSetting.Builder()
        .name("right")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_forward = sgGeneral.add(new IntSetting.Builder()
        .name("forward")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Integer> range_back = sgGeneral.add(new IntSetting.Builder()
        .name("back")
        .description("Maximum scan distance in blocks.")
        .defaultValue(1)
        .range(0, 16)
        .visible(() -> shape.get() == Shape.Cube)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to break when behind blocks.")
        .defaultValue(4.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .range(0, 100)
        .description("Legacy tick delay between completed blocks; combined with mine-delay-ms using the larger delay.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks-per-tick")
        .description("Maximum blocks to try to break per tick. Useful when insta mining.")
        .defaultValue(1)
        .range(1, 64)
        .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("The blocks you want to mine first.")
        .defaultValue(SortMode.Closest)
        .build()
    );

    private final Setting<Boolean> packetMine = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mine")
        .description("Use sequenced mining packets for fast block breaking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<PacketStrategy> packetStrategy = sgGeneral.add(new EnumSetting.Builder<PacketStrategy>()
        .name("packet-strategy")
        .description("Adaptive uses packets for instantly breakable blocks and normal mining for harder blocks.")
        .defaultValue(PacketStrategy.Adaptive)
        .visible(packetMine::get)
        .build()
    );

    private final Setting<Boolean> suitableTools = sgGeneral.add(new BoolSetting.Builder()
        .name("only-suitable-tools")
        .description("Only mines when using an appropriate for the block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> interact = sgGeneral.add(new BoolSetting.Builder()
        .name("interact")
        .description("Interacts with the block instead of mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates server-side to the block being mined.")
        .defaultValue(true)
        .build()
    );

    // Whitelist and blacklist

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("Selection mode.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("The blocks you don't want to mine.")
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("The blocks you want to mine.")
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    private final Setting<Keybind> selectBlockBind = sgWhitelist.add(new KeybindSetting.Builder()
        .name("select-block-bind")
        .description("Adds targeted block to list when this button is pressed.")
        .defaultValue(Keybind.none())
        .build()
    );

    // Rendering

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("Whether to swing hand client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableRenderBounding = sgRender.add(new BoolSetting.Builder()
        .name("bounding-box")
        .description("Enable rendering bounding box for Cube and Uniform Cube.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBox = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-box-mode")
        .description("How the shape for the bounding box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColorBox = sgRender.add(new ColorSetting.Builder()
        .name("bounding-side-color")
        .description("The side color of the bounding box.")
        .defaultValue(new SettingColor(16, 106, 144, 100))
        .build()
    );

    private final Setting<SettingColor> lineColorBox = sgRender.add(new ColorSetting.Builder()
        .name("bounding-line-color")
        .description("The line color of the bounding box.")
        .defaultValue(new SettingColor(16, 106, 144, 255))
        .build()
    );

    private final Setting<Boolean> enableRenderBreaking = sgRender.add(new BoolSetting.Builder()
        .name("broken-blocks")
        .description("Enable rendering bounding box for Cube and Uniform Cube.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeModeBreak = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("nuke-block-mode")
        .description("How the shapes for broken blocks are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the target block rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target block rendering.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(enableRenderBreaking::get)
        .build()
    );

    private final List<BlockPos> blocks = new ArrayList<>();
    private final Set<BlockPos> interacted = new ObjectOpenHashSet<>();

    private final MiningQueue<BlockPos> mining = new MiningQueue<>();
    private final PacketMiningProgress packetProgress = new PacketMiningProgress();
    private BlockPos packetTarget;
    private Block selectedBlock;
    private BlockState targetState;
    private long generation;
    private boolean ownsBreaking;
    private Object miningLevel, miningPlayer;
    private boolean boundsReady;
    private String status = "Ready";

    private final BlockPos.MutableBlockPos pos1 = new BlockPos.MutableBlockPos(); // Rendering for cubes
    private final BlockPos.MutableBlockPos pos2 = new BlockPos.MutableBlockPos();
    int maxh = 0;
    int maxv = 0;

    public Nuker() {
        super(Categories.World, "nuker", "Breaks blocks around you.");
    }

    private boolean rotating() { return rotate.get(); }

    @Override
    public void onActivate() {
        selectedBlock = null;
        resetMining();
        miningLevel = mc.level;
        miningPlayer = mc.player;
        status = selection.get() == Selection.SelectedBlock ? "Right-click a block to select its type" : "Searching";
        if (selection.get() == Selection.SelectedBlock) info(status);
    }

    @Override
    public void onDeactivate() {
        resetMining();
        selectedBlock = null;
        miningLevel = miningPlayer = null;
    }

    private void resetMining() {
        generation++;
        if (mining != null) mining.reset();
        targetState = null;
        if (blocks != null) blocks.clear();
        if (interacted != null) interacted.clear();
        boundsReady = false;
        stopMining();
    }

    private void stopMining() {
        boolean sameWorld = mc.level == miningLevel && mc.player == miningPlayer;
        if (sameWorld && packetTarget != null && mc.gameMode != null && mc.level != null && mc.getConnection() != null) {
            BlockPos abandoned = packetTarget;
            mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, abandoned, BlockUtils.getDirection(abandoned), sequence));
        }
        packetTarget = null;
        if (packetProgress != null) packetProgress.reset();
        if (sameWorld && ownsBreaking && mc.gameMode != null) {
            BlockUtils.breaking = false;
            mc.gameMode.stopDestroyBlock();
        }
        ownsBreaking = false;
    }

    @Override
    public String getInfoString() {
        return selectedBlock == null ? status : Names.get(selectedBlock) + " / " + status;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (boundsReady && enableRenderBounding.get()) {
            // Render bounding box if cube and should break stuff
            if (shape.get() != Shape.Sphere && mode.get() != Mode.Smash) {
                int minX = Math.min(pos1.getX(), pos2.getX());
                int minY = Math.min(pos1.getY(), pos2.getY());
                int minZ = Math.min(pos1.getZ(), pos2.getZ());
                int maxX = Math.max(pos1.getX(), pos2.getX());
                int maxY = Math.max(pos1.getY(), pos2.getY());
                int maxZ = Math.max(pos1.getZ(), pos2.getZ());
                event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, sideColorBox.get(), lineColorBox.get(), shapeModeBox.get(), 0);
            }
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press || !Utils.canUpdate() || mc.gui.screen() != null) return;
        if (selection.get() == Selection.SelectedBlock && event.button() == 1
            && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
            resetMining();
            selectedBlock = block;
            status = "Searching";
            info("Selected %s", Names.get(block));
            event.cancel();
        } else if (selectBlockBind.get().matches(event.input)) addTargetedBlockToList();
    }

    @EventHandler
    private void onKey(KeyInputEvent event) {
        if (event.action == KeyAction.Press && selectBlockBind.get().matches(event.input)) addTargetedBlockToList();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        blocks.clear();
        if (!Utils.canUpdate() || mc.gameMode == null) return;
        if (mc.level != miningLevel || mc.player != miningPlayer) {
            resetMining();
            miningLevel = mc.level;
            miningPlayer = mc.player;
        }
        if (mc.gui.screen() != null || mc.player.isUsingItem() || !mc.player.isAlive()) {
            generation++;
            mining.reset();
            targetState = null;
            stopMining();
            status = "Paused";
            return;
        }
        if (selection.get() == Selection.SelectedBlock && selectedBlock == null) {
            status = "Right-click a block to select its type";
            return;
        }
        if (mining.target() != null && targetState != null && !mc.level.getBlockState(mining.target()).equals(targetState)) {
            mining.completed(CombatTelemetry.now(), delayMillis());
            targetState = null;
            stopMining();
        }
        final long session = generation;
        final var level = mc.level;
        final var player = mc.player;

        // Calculate some stuff
        double pX = mc.player.getX(), pY = mc.player.getY(), pZ = mc.player.getZ();
        double rangeSq = Math.pow(range.get(), 2);
        BlockPos playerBlockPos = mc.player.blockPosition();

        double pX_ = pX;
        double pZ_ = pZ;
        int r = (int) Math.round(range.get());

        if (shape.get() == Shape.UniformCube) {
            pX_ += 1; // weird position stuff
            pos1.set(pX_ - r, pY - r + 1, pZ - r + 1); // down
            pos2.set(pX_ + r - 1, pY + r, pZ + r); // up
            maxh = 0;
            maxv = 0;
        } else {
            // Only change me if you want to mess with 3D rotations:
            // I messed with it
            Direction direction = mc.player.getDirection();
            switch (direction) {
                case Direction.SOUTH -> {
                    pZ_ += 1;
                    pX_ += 1;
                    pos1.set(pX_ - (range_right.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_back.get() + 1)); // down
                    pos2.set(pX_ + range_left.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_forward.get()); // up
                }
                case Direction.WEST -> {
                    pos1.set(pX_ - range_forward.get(), Math.ceil(pY) - range_down.get(), pZ_ - range_right.get()); // down
                    pos2.set(pX_ + range_back.get() + 1, Math.ceil(pY + range_up.get() + 1), pZ_ + range_left.get() + 1); // up
                }
                case Direction.NORTH -> {
                    pX_ += 1;
                    pZ_ += 1;
                    pos1.set(pX_ - (range_left.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - (range_forward.get() + 1)); // down
                    pos2.set(pX_ + range_right.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_back.get()); // up
                }
                case Direction.EAST -> {
                    pX_ += 1;
                    pos1.set(pX_ - (range_back.get() + 1), Math.ceil(pY) - range_down.get(), pZ_ - range_left.get()); // down
                    pos2.set(pX_ + range_forward.get(), Math.ceil(pY + range_up.get() + 1), pZ_ + range_right.get() + 1); // up
                }
            }

            // get largest horizontal
            maxh = 1 + Math.max(Math.max(Math.max(range_back.get(), range_right.get()), range_forward.get()), range_left.get());
            maxv = 1 + Math.max(range_up.get(), range_down.get());
        }

        // Flatten
        if (mode.get() == Mode.Flatten) pos1.setY((int) Math.floor(pY + 0.5));

        boundsReady = true;
        AABB box = new AABB(Vec3.atCenterOf(pos1), Vec3.atCenterOf(pos2));

        // Find blocks to break
        BlockIterator.register(Math.max((int) Math.ceil(range.get() + 1), maxh), Math.max((int) Math.ceil(range.get()), maxv), (blockPos, blockState) -> {
            if (!isActive() || generation != session || mc.level != level || mc.player != player) return;
            Vec3 center = Vec3.atCenterOf(blockPos);
            switch (shape.get()) {
                case Sphere -> {
                    if (center.distanceToSqr(pX, pY, pZ) > rangeSq)
                        return;
                }
                case UniformCube -> {
                    if (chebyshevDist(playerBlockPos.getX(), playerBlockPos.getY(), playerBlockPos.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ()) >= range.get())
                        return;
                }
                case Cube -> {
                    if (!box.contains(center)) return;
                }
            }

            // Flatten
            if (mode.get() == Mode.Flatten && blockPos.getY() + 0.5 < pY) return;

            // Smash
            if (mode.get() == Mode.Smash && blockState.getDestroySpeed(mc.level, blockPos) != 0) return;

            if (!eligible(blockPos, blockState)) return;

            // Add block
            blocks.add(blockPos.immutable());
        });

        // Break block if found
        BlockIterator.after(() -> {
            if (!isActive() || generation != session || mc.level != level || mc.player != player || mc.gui.screen() != null) return;
            // Sort blocks
            if (sortMode.get() == SortMode.TopDown)
                blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
            else if (sortMode.get() == SortMode.Fastest)
                blocks.sort(Comparator.<BlockPos>comparingDouble(value -> -mc.level.getBlockState(value).getDestroyProgress(mc.player, mc.level, value))
                    .thenComparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5)));
            else if (sortMode.get() != SortMode.None)
                blocks.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(pX, pY, pZ, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));

            if (blocks.isEmpty()) {
                mining.choose(blocks, CombatTelemetry.now());
                targetState = null;
                stopMining();
                status = "No matching reachable blocks";
                return;
            }
            for (int count = 0; count < maxBlocksPerTick.get(); count++) {
                BlockPos target = mining.choose(blocks, CombatTelemetry.now());
                if (target == null) { status = "Waiting for mine delay"; return; }
                targetState = mc.level.getBlockState(target);
                status = interact.get() ? "Interacting" : "Mining";
                Runnable action = () -> {
                    if (!isActive() || session != generation || mc.level != level || mc.player != player
                        || mc.gui.screen() != null || mc.player.isUsingItem() || !target.equals(mining.target())) return;
                    mineTarget(target);
                };
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 0, !silent.get(), action);
                    return;
                }
                action.run();
                if (mining.target() != null) return;
                blocks.remove(target);
            }
        });
    }

    private long delayMillis() { return Math.max(mineDelay.get(), delay.get() * 50L); }

    private boolean eligible(BlockPos pos, BlockState state) {
        if (state.isAir() || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (selection.get() == Selection.SelectedBlock && state.getBlock() != selectedBlock) return false;
        if (!interact.get() && !BlockUtils.canBreak(pos, state)) return false;
        if (suitableTools.get() && !interact.get() && state.requiresCorrectToolForDrops() && !mc.player.hasCorrectToolForDrops(state)) return false;
        if (isOutOfRange(pos)) return false;
        boolean listed = (listMode.get() == ListMode.Whitelist ? whitelist.get() : blacklist.get()).contains(state.getBlock());
        return listMode.get().allows(listed) && (!interact.get() || !interacted.contains(pos));
    }

    private void mineTarget(BlockPos target) {
        BlockState state = mc.level.getBlockState(target);
        if (!eligible(target, state) || !state.equals(targetState)) return;
        boolean instant = BlockUtils.canInstaBreak(target);
        boolean usePacket = packetMine.get() && (packetStrategy.get() == PacketStrategy.Always || instant);
        breakBlock(target, usePacket);
        if (enableRenderBreaking.get()) RenderUtils.renderTickingBlock(target, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
        if (interact.get() || !mc.level.getBlockState(target).equals(state)) {
            mining.completed(CombatTelemetry.now(), delayMillis());
            targetState = null;
        }
    }

    private void breakBlock(BlockPos blockPos, boolean usePacket) {
        if (interact.get()) {
            // Interact mode
            BlockUtils.interact(new BlockHitResult(Vec3.atCenterOf(blockPos), BlockUtils.getDirection(blockPos), blockPos, true), InteractionHand.MAIN_HAND, swing.get());
            interacted.add(blockPos);
        } else if (usePacket) {
            // Keep hardness progress across ticks. A hard block must not receive an
            // immediate STOP on every tick; servers reject that as an unfinished break.
            if (!blockPos.equals(packetTarget)) {
                stopMining();
                packetTarget = blockPos.immutable();
            }
            long now = CombatTelemetry.now();
            if (packetProgress.start(now)) {
                mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos), sequence));
            }
            if (packetProgress.advance(mc.player.isCreative() ? 1 : mc.level.getBlockState(blockPos).getDestroyProgress(mc.player, mc.level, blockPos), now)) {
                mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos), sequence));
            }
            if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            else mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        } else {
            // Legit mine mode
            if (packetTarget != null) stopMining();
            ownsBreaking = BlockUtils.breakBlock(blockPos, swing.get());
        }
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        double x = Mth.clamp(eye.x, blockPos.getX(), blockPos.getX() + 1);
        double y = Mth.clamp(eye.y, blockPos.getY(), blockPos.getY() + 1);
        double z = Mth.clamp(eye.z, blockPos.getZ(), blockPos.getZ() + 1);
        if (distanceCheck.get() && eye.distanceToSqr(x, y, z) > reach * reach) return true;

        Vec3 pos = Vec3.atCenterOf(blockPos);
        ClipContext clipContext = new ClipContext(eye, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.level.clip(clipContext);
        if (result == null || !result.getBlockPos().equals(blockPos))
            return !PlayerUtils.isWithin(pos, wallsRange.get());

        return false;
    }

    private void addTargetedBlockToList() {
        if (!Utils.canUpdate() || mc.gui.screen() != null) return;

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        Block targetBlock = mc.level.getBlockState(pos).getBlock();

        List<Block> list = listMode.get() == ListMode.Whitelist ? whitelist.get() : blacklist.get();
        String modeName = listMode.get().name();

        if (list.contains(targetBlock)) {
            list.remove(targetBlock);
            info("Removed " + Names.get(targetBlock) + " from " + modeName);
        } else {
            list.add(targetBlock);
            info("Added " + Names.get(targetBlock) + " to " + modeName);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        if (ownsBreaking) event.cooldown = 0;
    }

    public enum Selection { SelectedBlock, Area }

    public enum Mode {
        All,
        Flatten,
        Smash
    }

    public enum SortMode {
        None,
        Closest,
        Furthest,
        TopDown,
        Fastest
    }

    public enum PacketStrategy {
        Adaptive,
        Always
    }

    public enum Shape {
        Cube,
        UniformCube,
        Sphere
    }

    public static int chebyshevDist(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Gets the largest X, Y or Z difference, chebyshev distance
        int dX = Math.abs(x2 - x1);
        int dY = Math.abs(y2 - y1);
        int dZ = Math.abs(z2 - z1);
        return Math.max(Math.max(dX, dY), dZ);
    }
}
