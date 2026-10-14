/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.commands.commands;

import baritone.api.BaritoneAPI;
import baritone.api.schematic.IStaticSchematic;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.gui.GuiThemes;
import com.gaspoweredcake.client33.gui.screens.SchematicMaterialsScreen;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.world.MaterialReport;
import com.gaspoweredcake.client33.commands.Command;
import com.gaspoweredcake.client33.commands.arguments.BlockPosArgumentType;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.pathing.BaritonePathManager;
import com.gaspoweredcake.client33.utils.misc.Names;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import com.gaspoweredcake.client33.utils.world.SchematicMaterials;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Optional Baritone integration; only registered when its API is available. */
public class SchematicCommand extends Command {
    private static final int PAGE_SIZE = 12;
    private IStaticSchematic schematic;
    private String filename;
    private BlockPos origin;
    private final Map<Item, Long> required = new HashMap<>();
    private final Map<Block, Long> manual = new HashMap<>();
    private long cursor, volume, generation;
    private boolean loading, scanning;
    private String notice = "Choose a file to begin.";
    private boolean noticeError;

    public SchematicCommand() {
        super("schematic", "Load schematics, list every material and control Baritone builds.");
        Client33.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> {
            info("load <file>, origin <x y z>, materials [page], screen, export, build, pause, resume, status. Files go in the Minecraft schematics folder.");
            return SINGLE_SUCCESS;
        });
        builder.then(literal("load").then(argument("file", StringArgumentType.greedyString()).executes(ctx -> {
            load(StringArgumentType.getString(ctx, "file"));
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("origin").then(argument("position", BlockPosArgumentType.blockPos()).executes(ctx -> {
            if (!ready()) return SINGLE_SUCCESS;
            if (builderActive()) { error("Finish the current build before changing the origin; use Baritone cancel to end it."); return SINGLE_SUCCESS; }
            origin = BlockPosArgumentType.getValidBlockPos(ctx, "position");
            info("Origin: %s", origin.toShortString());
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("materials").executes(_ -> { materials(1); return SINGLE_SUCCESS; })
            .then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                materials(IntegerArgumentType.getInteger(ctx, "page")); return SINGLE_SUCCESS;
            })));
        builder.then(literal("screen").executes(_ -> { openWorkspace(); return SINGLE_SUCCESS; }));
        builder.then(literal("stop").executes(_ -> { stopBuild(); return SINGLE_SUCCESS; }));
        builder.then(literal("export").executes(_ -> { export(); return SINGLE_SUCCESS; }));
        builder.then(literal("build").executes(_ -> { startBuild(); return SINGLE_SUCCESS; }));
        builder.then(literal("pause").executes(_ -> {
            if (builderActive()) { process().pause(); info("Build paused. Use schematic resume to continue."); }
            else error("No active Baritone build.");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("resume").executes(_ -> {
            if (builderActive()) { process().resume(); info("Build resumed."); }
            else error("No active Baritone build. Use schematic build to start the loaded schematic again.");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("status").executes(_ -> {
            if (loading) info("Loading file...");
            else if (scanning) info("Counting materials: %d%%", cursor * 100 / volume);
            else if (schematic == null) info("No schematic loaded.");
            else info("%s | %dx%dx%d | Origin %s | %s", filename, schematic.widthX(), schematic.heightY(), schematic.lengthZ(),
                origin.toShortString(), builderActive() ? (process().isPaused() ? "Baritone paused" : "Baritone building") : "Ready / builder inactive");
            return SINGLE_SUCCESS;
        }));
    }

    private baritone.api.process.IBuilderProcess process() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess();
    }

    private boolean builderActive() { return process().isActive() || process().isPaused(); }

    public void load(String name) {
        if (mc.player == null || mc.level == null) { error("Join a world before loading a schematic."); return; }
        name = name.strip();
        if (name.isEmpty()) { error("Choose a file or enter its name first."); return; }
        final String requestedName = name;
        if (loading || scanning) { error("A schematic is still loading; wait for the materials scan."); return; }
        if (builderActive()) { error("Finish or cancel the current Baritone build before loading another schematic."); return; }
        long request = ++generation;
        var level = mc.level;
        BlockPos requestedOrigin = mc.player.blockPosition();
        Path folder = mc.gameDirectory.toPath().resolve("schematics").toAbsolutePath().normalize();
        loading = true;
        notice = "Loading " + name + "..."; noticeError = false;
        Client33Executor.execute(() -> {
            try {
                Files.createDirectories(folder);
                Path root = folder.toRealPath();
                Path candidate = root.resolve(requestedName).normalize();
                if (!candidate.startsWith(root)) throw new IllegalArgumentException("File must be inside the schematics folder.");
                if (!Files.isRegularFile(candidate) && !candidate.getFileName().toString().contains(".")) {
                    candidate = root.resolve(requestedName + "." + BaritoneAPI.getSettings().schematicFallbackExtension.value).normalize();
                }
                Path file = candidate.toRealPath();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Invalid schematic file.");
                var formats = BaritoneAPI.getProvider().getSchematicSystem();
                var format = formats.getByFile(file.toFile()).orElseThrow(() -> new IllegalArgumentException("Supported formats: " + formats.getFileExtensions()));
                IStaticSchematic parsed;
                try (var input = Files.newInputStream(file)) { parsed = format.parse(input); }
                long cells = Math.multiplyExact(Math.multiplyExact((long) parsed.widthX(), parsed.heightY()), parsed.lengthZ());
                if (parsed.widthX() <= 0 || parsed.heightY() <= 0 || parsed.lengthZ() <= 0 || cells > 100_000_000) {
                    throw new IllegalArgumentException("Invalid dimensions or schematic exceeds 100 million cells.");
                }
                String loadedName = file.getFileName().toString();
                mc.execute(() -> {
                    if (request != generation || mc.level != level) return;
                    loading = false;
                    schematic = parsed;
                    filename = loadedName;
                    origin = requestedOrigin;
                    required.clear(); manual.clear();
                    cursor = 0; volume = cells; scanning = true;
                    info("Loaded %s. Counting materials without blocking game ticks...", filename);
                });
            } catch (Exception failure) {
                mc.execute(() -> {
                    if (request != generation || mc.level != level) return;
                    loading = false;
                    error("Could not load schematic: %s", failure.getMessage());
                });
            }
        });
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!scanning || mc.level == null) return;
        try {
            // Bound both work and elapsed time so huge schematics do not stall movement/pathing.
            long deadline = System.nanoTime() + 2_000_000;
            for (int i = 0; i < 8192 && cursor < volume && System.nanoTime() < deadline; i++, cursor++) {
                int x = (int) (cursor % schematic.widthX());
                long column = cursor / schematic.widthX();
                int z = (int) (column % schematic.lengthZ());
                int y = (int) (column / schematic.lengthZ());
                if (!schematic.inSchematic(x, y, z, null)) continue;
                var state = schematic.getDirect(x, y, z);
                int units = SchematicMaterials.units(state);
                if (units == 0) continue;
                Item item = state.getBlock().asItem();
                if (item == Items.AIR) manual.merge(state.getBlock(), (long) units, Long::sum);
                else required.merge(item, (long) units, Long::sum);
            }
            if (cursor == volume) {
                scanning = false;
                info("Materials ready: %d item types. Use schematic materials or schematic export for the complete list.", required.size());
                warning("Totals cover the entire source schematic, excluding scaffolding. Inventory counts exclude containers and shulker contents.");
                if (!manual.isEmpty()) warning("%d block types need manual handling (fluids or blocks without items); included in the export.", manual.size());
            }
        } catch (RuntimeException failure) {
            scanning = false; schematic = null; required.clear(); manual.clear();
            error("Material scan failed: %s", failure.getMessage());
        }
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        generation++; loading = scanning = false;
        schematic = null; origin = null; required.clear(); manual.clear();
    }

    private boolean ready() {
        if (loading || scanning) { error("Materials are still loading. Use schematic status for progress."); return false; }
        if (schematic == null) { error("Load a schematic first: schematic load <file>."); return false; }
        return true;
    }

    private List<Item> sortedItems() {
        return required.keySet().stream().sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
    }

    private Map<Item, Long> inventory() {
        Map<Item, Long> counts = new HashMap<>();
        // Player storage plus offhand, excluding armor slots and external containers.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) counts.merge(offhand.getItem(), (long) offhand.getCount(), Long::sum);
        return counts;
    }

    public boolean hasReadySchematic() { return mc.player != null && mc.level != null && schematic != null && !loading && !scanning; }
    public boolean isBuilding() { return builderActive(); }
    public String notice() { return notice; }
    public boolean noticeError() { return noticeError; }
    public BlockPos origin() { return origin; }
    public String statusText() {
        if (mc.player == null || mc.level == null) return "Join a world to load and build.";
        if (loading) return "Loading file...";
        if (scanning) return "Counting materials: " + cursor * 100 / Math.max(1, volume) + "%";
        if (schematic == null) return "No schematic loaded";
        return filename + "  /  " + schematic.widthX() + " x " + schematic.heightY() + " x " + schematic.lengthZ()
            + "  /  " + (builderActive() ? process().isPaused() ? "Paused" : "Building" : "Ready")
            + "  /  origin " + origin.toShortString();
    }
    @Override public void error(String message, Object... args) { notice = message.formatted(args); noticeError = true; super.error(message, args); }
    @Override public void info(String message, Object... args) { notice = message.formatted(args); noticeError = false; super.info(message, args); }
    @Override public void warning(String message, Object... args) { notice = message.formatted(args); noticeError = false; super.warning(message, args); }
    public void setOrigin(BlockPos position) {
        if (!ready()) return;
        if (builderActive()) { error("Stop the build before changing its origin."); return; }
        origin = position.immutable(); info("Origin set to %s", origin.toShortString());
    }
    public void pauseBuild() { if (builderActive()) { process().pause(); info("Build paused. Resume when ready."); } else error("No active build to pause."); }
    public void resumeBuild() { if (builderActive()) { process().resume(); info("Build resumed."); } else error("No active build. Use Build to start."); }
    public void stopBuild() {
        if (!builderActive()) { error("No active build to stop."); return; }
        process().onLostControl();
        if (PathManagers.get() instanceof BaritonePathManager manager) manager.updateBuildGuard();
        info("Build stopped. The schematic is still loaded.");
    }
    public void openWorkspace() {
        var tab = com.gaspoweredcake.client33.gui.tabs.Tabs.get(com.gaspoweredcake.client33.gui.tabs.builtin.SchematicsTab.class);
        if (tab != null) {
            var theme = GuiThemes.get(); var screen = tab.createScreen(theme);
            screen.addDirect(new com.gaspoweredcake.client33.gui.widgets.containers.WPageNavigation(tab)).bottom().pad(12);
            Utils.screenToOpen = screen;
        }
    }

    private void openMaterialsScreen() {
        if (!ready()) return;
        long session = generation;
        var level = mc.level;
        Utils.screenToOpen = new SchematicMaterialsScreen(GuiThemes.get(), filename, this::reportEntries,
            () -> session == generation && level == mc.level && mc.player != null && schematic != null && !loading && !scanning,
            this::export);
    }

    public List<MaterialReport.Entry> reportEntries() {
        Map<Item, Long> held = inventory();
        List<MaterialReport.Entry> entries = new ArrayList<>();
        for (Item item : sortedItems()) entries.add(new MaterialReport.Entry(BuiltInRegistries.ITEM.getKey(item).toString(),
            Names.get(item), required.get(item), held.getOrDefault(item, 0L), new ItemStack(item).getMaxStackSize(), false));
        manual.entrySet().stream().sorted(Comparator.comparing(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString()))
            .forEach(entry -> entries.add(new MaterialReport.Entry(BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString(),
                Names.get(entry.getKey()), entry.getValue(), 0, 1, true)));
        return entries;
    }

    private void materials(int page) {
        if (!ready()) return;
        List<Item> items = sortedItems();
        int pages = Math.max(1, (items.size() + manual.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) { error("Choose a page from 1 to %d.", pages); return; }
        Map<Item, Long> held = inventory();
        List<String> rows = new ArrayList<>();
        for (Item item : items) {
            long total = required.get(item), have = held.getOrDefault(item, 0L);
            rows.add("%s: need %,d (%d stacks), have %,d, missing %,d".formatted(Names.get(item), total,
                SchematicMaterials.stacks(total, new ItemStack(item).getMaxStackSize()), have, Math.max(0, total - have)));
        }
        manual.entrySet().stream().sorted(Comparator.comparing(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString()))
            .forEach(entry -> rows.add("Manual: %s — %,d block positions".formatted(Names.get(entry.getKey()), entry.getValue())));
        info("%s — full schematic materials (%d/%d)", filename, page, pages);
        rows.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, rows.size())).forEach(this::info);
    }

    public void export() {
        if (!ready()) return;
        try {
            Path directory = Client33.FOLDER.toPath().resolve("materials");
            Files.createDirectories(directory);
            Path output = directory.resolve("schematic-materials.csv");
            Map<Item, Long> held = inventory();
            try (var writer = Files.newBufferedWriter(output)) {
                writer.write("type,id,required,stacks,inventory,missing\n");
                for (Item item : sortedItems()) {
                    long total = required.get(item), have = held.getOrDefault(item, 0L);
                    writer.write("item,%s,%d,%d,%d,%d\n".formatted(BuiltInRegistries.ITEM.getKey(item), total,
                        SchematicMaterials.stacks(total, new ItemStack(item).getMaxStackSize()), have, Math.max(0, total - have)));
                }
                for (var entry : manual.entrySet()) writer.write("manual,%s,%d,,,\n".formatted(BuiltInRegistries.BLOCK.getKey(entry.getKey()), entry.getValue()));
            }
            info("Complete materials list saved to %s", output.toAbsolutePath());
        } catch (Exception failure) { error("Could not export materials: %s", failure.getMessage()); }
    }

    public void startBuild() {
        if (!ready()) return;
        if (builderActive()) { error("A Baritone build is already active. Use schematic resume or Baritone cancel."); return; }
        var settings = BaritoneAPI.getSettings();
        if (!settings.buildSubstitutes.value.isEmpty() || !settings.buildSkipBlocks.value.isEmpty()
            || settings.mapArtMode.value || settings.buildOnlySelection.value || !settings.buildRepeat.value.equals(BlockPos.ZERO)) {
            error("This full-schematic build requires no substitutes, skipped blocks, map-art, selection-only or build-repeat settings. Disable them in Baritone first.");
            return;
        }
        long bottom = (long) origin.getY() + (settings.schematicOrientationY.value ? schematic.heightY() : 0);
        if (bottom < mc.level.getMinY() || bottom + schematic.heightY() > (long) mc.level.getMinY() + mc.level.getHeight()) {
            error("The schematic exceeds the world's build height at this origin. Change schematic origin or Baritone schematicOrientationY.");
            return;
        }
        if (settings.startAtLayer.value != 0) {
            error("Set Baritone startAtLayer to 0 before starting this full schematic build.");
            return;
        }
        Map<Item, Long> held = inventory();
        long shortages = required.entrySet().stream().filter(entry -> held.getOrDefault(entry.getKey(), 0L) < entry.getValue()).count();
        if (shortages > 0) warning("%d material types are short for the full build. Restock as needed; schematic materials lists shortages.", shortages);
        if (!manual.isEmpty()) warning("Some blocks require manual placement; check schematic materials.");
        process().build(filename, schematic, origin);
        if (PathManagers.get() instanceof BaritonePathManager manager) manager.updateBuildGuard();
        info("Build started at %s. Existing correct blocks are handled by Baritone. Use schematic pause/resume; status reports whether the builder is active.", origin.toShortString());
        if (!settings.buildInLayers.value) info("For difficult builds, enable Baritone buildInLayers with layerHeight 1 to work from the bottom up.");
    }
}
