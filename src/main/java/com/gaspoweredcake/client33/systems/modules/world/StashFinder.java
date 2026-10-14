/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.world;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.mixin.MinecraftServerAccessor;
import com.gaspoweredcake.client33.utils.files.AtomicFile;
import com.gaspoweredcake.client33.utils.world.CoordinateContext;
import com.gaspoweredcake.client33.utils.world.StashPersistence;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.ChunkDataEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.containers.WTable;
import com.gaspoweredcake.client33.gui.widgets.containers.WVerticalList;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WMinus;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import com.gaspoweredcake.client33.utils.misc.text.RunnableClickEvent;
import com.gaspoweredcake.client33.utils.player.ChatUtils;
import com.gaspoweredcake.client33.utils.render.Client33Toast;
import com.gaspoweredcake.client33.utils.render.RenderUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class StashFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private static final List<Block> DEFAULT_SUPPORT_BLOCK_BLACKLIST = List.of(
        Blocks.COPPER_BLOCK.weathering().oxidized(),
        Blocks.CUT_COPPER.weathering().oxidized(),
        Blocks.TUFF_BRICKS,
        Blocks.COPPER_BLOCK.waxed().unaffected(),
        Blocks.COPPER_BLOCK.waxed().oxidized(),
        Blocks.CUT_COPPER.waxed().oxidized(),
        Blocks.BARREL,
        Blocks.COPPER_BULB.waxed().unaffected()
    );

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to search for.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minimumStorageCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage-count")
        .description("The minimum amount of storage blocks in a chunk to record the chunk.")
        .defaultValue(4)
        .min(1)
        .sliderMin(1)
        .build()
    );

    private final Setting<List<Block>> blacklistedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blacklisted-support-blocks")
        .description("Blocks that prevent counting a storage block entity when it sits on them.")
        .defaultValue(DEFAULT_SUPPORT_BLOCK_BLACKLIST)
        .build()
    );

    private final Setting<Integer> minimumDistance = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-distance")
        .description("The minimum distance you must be from spawn to record a certain chunk.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Sends Minecraft notifications when new stashes are found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );

    private final Setting<Boolean> renderTracer = sgRender.add(new BoolSetting.Builder()
        .name("render-tracer")
        .description("Renders a tracer to the last found stash.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> traceColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the stash tracer.")
        .defaultValue(new SettingColor(255, 215, 0, 255))
        .visible(renderTracer::get)
        .build()
    );

    private final Setting<Integer> traceArrivalDistance = sgRender.add(new IntSetting.Builder()
        .name("tracer-hide-at-distance")
        .description("Hide the trace when you are this close to the stash.")
        .defaultValue(16)
        .min(1)
        .sliderMin(1)
        .sliderMax(50)
        .visible(renderTracer::get)
        .build()
    );

    private final Setting<Integer> traceMaxDistance = sgRender.add(new IntSetting.Builder()
        .name("tracer-max-distance")
        .description("Hide the trace when you are farther than this distance from the stash.")
        .defaultValue(2000)
        .min(10)
        .sliderMin(50)
        .sliderMax(10000)
        .visible(renderTracer::get)
        .build()
    );

    private final Setting<Boolean> renderChunkColumn = sgRender.add(new BoolSetting.Builder()
        .name("render-chunk-column")
        .description("Renders a vertical column at the center of traced chunks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> traceColumnColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-column-color")
        .description("Color of the stash tracer column.")
        .defaultValue(new SettingColor(255, 215, 0, 100))
        .visible(renderChunkColumn::get)
        .build()
    );

    private final Setting<Keybind> clearTracesBind = sgRender.add(new KeybindSetting.Builder()
        .name("clear-traces-bind")
        .description("Keybind to clear all stash traces.")
        .defaultValue(Keybind.none())
        .build()
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final StashPersistence PERSISTENCE = new StashPersistence();

    private record CsvField(String name, ToIntFunction<Chunk> getter, ObjIntConsumer<Chunk> setter) {
        public CsvField(String name, ToIntFunction<Chunk> getter) {
            this(name, getter, null);
        }
    }

    private static final List<CsvField> CSV_FIELDS = List.of(
        new CsvField("x", c -> c.chunkPos.x()),
        new CsvField("z", c -> c.chunkPos.z()),
        new CsvField("chests", c -> c.chests, (c, v) -> c.chests = v),
        new CsvField("barrels", c -> c.barrels, (c, v) -> c.barrels = v),
        new CsvField("shulkers", c -> c.shulkers, (c, v) -> c.shulkers = v),
        new CsvField("enderChests", c -> c.enderChests, (c, v) -> c.enderChests = v),
        new CsvField("furnaces", c -> c.furnaces, (c, v) -> c.furnaces = v),
        new CsvField("dispensersDroppers", c -> c.dispensersDroppers, (c, v) -> c.dispensersDroppers = v),
        new CsvField("hoppers", c -> c.hoppers, (c, v) -> c.hoppers = v),
        new CsvField("otherStorage", c -> c.otherStorage, (c, v) -> c.otherStorage = v)
    );

    private final Map<Chunk, Vec3> tracerPositions = new HashMap<>();
    public List<Chunk> chunks = new ArrayList<>();
    private String loadedServer;
    private String loadedDimension;
    private Path storageFolder;
    private boolean dirty;
    private int saveTicks;

    public StashFinder() {
        super(Categories.World, "stash-finder", "Searches loaded chunks for storage blocks. Saves to <your minecraft folder>/client33", "Base Finder", "Base Hunter", "Stash Hunter", "Storage Finder");
    }

    @Override
    public void onActivate() {
        ensureContext();
    }

    @Override
    public void onDeactivate() {
        saveRecords();
        tracerPositions.clear();
        loadedServer = loadedDimension = null;
        storageFolder = null;
        chunks.clear();
    }

    private String currentServer() {
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null)
            return "singleplayer:" + ((MinecraftServerAccessor) mc.getSingleplayerServer()).client33$getStorageSource().getLevelId();
        var server = mc.getCurrentServer();
        return server == null ? CoordinateContext.UNKNOWN : "server:" + server.ip.strip().toLowerCase(Locale.ROOT);
    }

    private String currentDimension() { return mc.level == null ? CoordinateContext.UNKNOWN : mc.level.dimension().identifier().toString(); }

    private void ensureContext() {
        if (mc.level == null) return;
        String server = currentServer(), dimension = currentDimension();
        if (CoordinateContext.UNKNOWN.equals(server)) {
            saveRecords();
            loadedServer = loadedDimension = null;
            storageFolder = null; chunks.clear(); tracerPositions.clear();
            return;
        }
        if (!Objects.equals(server, loadedServer)) {
            saveRecords();
            loadedServer = server;
            storageFolder = Client33.FOLDER.toPath().resolve("stashes").resolve("by-server").resolve(CoordinateContext.storageKey(server));
            tracerPositions.clear();
            load();
        }
        if (!Objects.equals(dimension, loadedDimension)) {
            loadedDimension = dimension;
            tracerPositions.clear();
        }
    }

    private boolean current(Chunk chunk) {
        return mc.level != null && chunk.matches(currentServer(), currentDimension());
    }

    private void navigate(Chunk chunk) {
        if (!current(chunk)) { warning("Join the recorded server and dimension first; legacy records have unknown context."); return; }
        PathManagers.get().moveTo(new BlockPos(chunk.chunkPos.getMiddleBlockX(), 0, chunk.chunkPos.getMiddleBlockZ()), true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ensureContext();
        if (++saveTicks >= 20) { saveTicks = 0; saveRecords(); }
        if (!clearTracesBind.get().isPressed()) return;
        tracerPositions.clear();
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) { saveRecords(); }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null || event.chunk().getLevel() != mc.level) return;
        ensureContext();
        if (loadedServer == null) return;
        // Check the distance.
        double chunkXAbs = Math.abs(event.chunk().getPos().x() * 16);
        double chunkZAbs = Math.abs(event.chunk().getPos().z() * 16);
        if (Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < minimumDistance.get()) return;

        Chunk chunk = scanChunk(event.chunk(), storageBlocks.get(), blacklistedBlocks.get());
        chunk.server = loadedServer;
        chunk.dimension = loadedDimension;
        chunk.firstSeen = chunk.lastSeen = System.currentTimeMillis();

        if (chunk.getTotal() >= minimumStorageCount.get()) {
            Chunk prevChunk = recordObservation(chunks, chunk);

            if (renderTracer.get()) {
                double y = mc.player != null ? mc.player.getEyeY() : 0.0;
                tracerPositions.put(chunk, new Vec3(chunk.chunkPos.getMiddleBlockX(), y, chunk.chunkPos.getMiddleBlockZ()));
            }

            dirty = true;

            if (sendNotifications.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                switch (notificationMode.get()) {
                    case Chat -> sendChatNotification(chunk);
                    case Toast -> {
                        Client33Toast toast = new Client33Toast.Builder(title).icon(Items.CHEST).text("Found Stash!").build();
                        mc.gui.toastManager().addToast(toast);
                    }
                    case Both -> {
                        sendChatNotification(chunk);
                        Client33Toast toast = new Client33Toast.Builder(title).icon(Items.CHEST).text("Found Stash!").build();
                        mc.gui.toastManager().addToast(toast);
                    }
                }
            }
        }
    }

    public static Chunk scanChunk(net.minecraft.world.level.chunk.LevelChunk source, List<BlockEntityType<?>> selected, List<Block> blockBlacklist) {
        Chunk chunk = new Chunk(source.getPos());
        for (BlockEntity blockEntity : source.getBlockEntities().values()) {
            if (!selected.contains(blockEntity.getType())) continue;

            if (!blockBlacklist.isEmpty()) {
                BlockPos below = blockEntity.getBlockPos().below();
                if (blockBlacklist.contains(source.getBlockState(below).getBlock())) continue;
            }

            switch (blockEntity) {
                case ChestBlockEntity _ -> chunk.chests++;
                case BarrelBlockEntity _ -> chunk.barrels++;
                case ShulkerBoxBlockEntity _ -> chunk.shulkers++;
                case EnderChestBlockEntity _ -> chunk.enderChests++;
                case AbstractFurnaceBlockEntity _ -> chunk.furnaces++;
                case DispenserBlockEntity _ -> chunk.dispensersDroppers++;
                case HopperBlockEntity _ -> chunk.hoppers++;
                default -> chunk.otherStorage++;
            }
        }
        return chunk;
    }

    /** RTP findings can be saved without enabling the independent Stash Finder module. */
    public void saveFinding(Chunk chunk) {
        ensureContext();
        if (loadedServer == null) return;
        chunk.server = loadedServer; chunk.dimension = loadedDimension;
        chunk.firstSeen = chunk.lastSeen = java.lang.System.currentTimeMillis();
        recordObservation(chunks, chunk);
        dirty = true;
        saveRecords();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        ensureContext();
        // Sort
        chunks.sort(Comparator.comparingInt(value -> -value.getTotal()));

        WVerticalList list = theme.verticalList();

        list.add(theme.label("Server: " + (loadedServer == null ? "Join a world" : loadedServer), 480));
        list.add(theme.label("Times are observations in UTC. Legacy records have unknown server/dimension/time.", 480));
        if (loadedServer != null && storageFolder == null)
            list.add(theme.label("Saving blocked: unreadable stash file. Original files are preserved.", 480));

        // Clear buttons
        WHorizontalList hl = theme.horizontalList();
        String displayedServer = loadedServer;
        WButton clear = hl.add(theme.button("Clear Chunks")).widget();
        WButton resetTracers = hl.add(theme.button("Reset Tracers")).widget();

        list.add(hl);

        WTable table = new WTable();
        list.add(table).expandX();

        clear.action = () -> {
            ensureContext();
            if (!Objects.equals(displayedServer, loadedServer)) { warning("Server changed. Reopen the stash list before clearing it."); return; }
            chunks.clear();
            table.clear();
            tracerPositions.clear();
            dirty = true;
            saveRecords();
        };

        resetTracers.action = () -> {
            table.clear();
            tracerPositions.clear();
            fillTable(theme, table);
        };

        // Chunks
        fillTable(theme, table);

        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (Chunk chunk : chunks) {
            String summary = chunk.chunkPos.getMiddleBlockX() + ", " + chunk.chunkPos.getMiddleBlockZ()
                + " / " + chunk.dimension + " / " + CoordinateContext.time(chunk.lastSeen);
            table.add(theme.label(summary, 350)).padRight(10).widget().tooltip = chunk.context();
            table.add(theme.label("Total: " + chunk.getTotal())).padRight(10);

            WCheckbox visible = table.add(theme.checkbox(tracerPositions.containsKey(chunk))).widget();
            visible.action = () -> {
                if (visible.checked && current(chunk)) {
                    double y = mc.player != null ? mc.player.getEyeY() : 0.0;
                    tracerPositions.put(chunk, new Vec3(chunk.chunkPos.getMiddleBlockX(), y, chunk.chunkPos.getMiddleBlockZ()));
                } else { visible.checked = false; tracerPositions.remove(chunk); }
            };

            WButton open = table.add(theme.button("Open")).widget();
            open.action = () -> mc.gui.setScreen(new ChunkScreen(theme, chunk));

            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> navigate(chunk);
            gotoBtn.tooltip = "Only available on the recorded server and dimension.";

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                ensureContext();
                if (!Objects.equals(chunk.server, loadedServer) && !CoordinateContext.UNKNOWN.equals(chunk.server)) return;
                if (chunks.remove(chunk)) {
                    tracerPositions.remove(chunk);
                    table.clear();
                    fillTable(theme, table);

                    dirty = true;
                    saveRecords();
                }
            };

            table.row();
        }
    }

    public static Chunk recordObservation(List<Chunk> records, Chunk observation) {
        int index = records.indexOf(observation);
        if (index < 0) { records.add(observation); return null; }
        Chunk previous = records.set(index, observation);
        observation.firstSeen = previous.firstSeen > 0 ? previous.firstSeen : observation.firstSeen;
        observation.lastSeen = Math.max(previous.lastSeen, observation.lastSeen);
        return previous;
    }

    private void load() {
        dirty = false; saveTicks = 0;
        chunks = new ArrayList<>();
        List<Chunk> queued = PERSISTENCE.pendingSnapshot(storageFolder);
        if (queued != null) { chunks = new ArrayList<>(queued); return; }
        Path json = getJsonFile(), csv = getCsvFile();
        // Old files had neither reliable server identity nor a dimension. Keep them explicitly unknown.
        if (!Files.exists(json) && !Files.exists(csv)) {
            Path legacy = Client33.FOLDER.toPath().resolve("stashes").resolve(Utils.getFileWorldName());
            json = legacy.resolve("stashes.json"); csv = legacy.resolve("stashes.csv");
        }
        try {
            if (Files.exists(json)) chunks = readJson(json);
            else if (Files.exists(csv)) chunks = readCsv(csv);
        } catch (Exception failure) {
            Client33.LOG.error("Cannot read stash records; original files were left intact", failure);
            // Do not overwrite an unreadable file with an empty list.
            storageFolder = null;
        }
    }

    public static List<Chunk> readJson(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            List<Chunk> records = GSON.fromJson(reader, new TypeToken<List<Chunk>>() {}.getType());
            if (records == null) throw new IOException("Stash list is null");
            for (Chunk record : records) {
                if (record == null || record.chunkPos == null) throw new IOException("Invalid stash record");
                record.normalize();
            }
            return new ArrayList<>(records);
        }
    }

    public static List<Chunk> readCsv(Path file) throws IOException {
        List<Chunk> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Stash CSV header is missing");
            List<String> columns = CoordinateContext.parseCsv(header);
            if (!columns.contains("x") || !columns.contains("z") || new HashSet<>(columns).size() != columns.size())
                throw new IOException("Invalid stash CSV header");
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank()) continue;
                List<String> values = CoordinateContext.parseCsv(line);
                if (values.size() != columns.size())
                    throw new IOException("Invalid stash CSV row length");
                Chunk chunk = new Chunk(new ChunkPos(Integer.parseInt(values.get(columns.indexOf("x"))), Integer.parseInt(values.get(columns.indexOf("z")))));
                for (CsvField field : CSV_FIELDS) {
                    int column = columns.indexOf(field.name());
                    if (field.setter() != null && column >= 0) field.setter().accept(chunk, Integer.parseInt(values.get(column)));
                }
                if (columns.contains("server") && columns.contains("dimension") && columns.contains("firstSeenUTC") && columns.contains("lastSeenUTC")) {
                    chunk.server = values.get(columns.indexOf("server")); chunk.dimension = values.get(columns.indexOf("dimension"));
                    chunk.firstSeen = CoordinateContext.parseTime(values.get(columns.indexOf("firstSeenUTC"))); chunk.lastSeen = CoordinateContext.parseTime(values.get(columns.indexOf("lastSeenUTC")));
                }
                chunk.normalize(); records.add(chunk);
            }
        }
        return records;
    }

    public static void writeJson(Path file, List<Chunk> records) throws IOException {
        AtomicFile.write(file, staging -> {
            try (BufferedWriter writer = Files.newBufferedWriter(staging)) { GSON.toJson(records, writer); }
        });
    }

    public static void writeCsv(Path file, List<Chunk> records) throws IOException {
        AtomicFile.write(file, staging -> {
            try (BufferedWriter writer = Files.newBufferedWriter(staging)) {
                writer.write(CSV_FIELDS.stream().map(CsvField::name).collect(Collectors.joining(","))
                    + ",server,dimension,firstSeenUTC,lastSeenUTC");
                writer.newLine();
                for (Chunk chunk : records) chunk.write(writer);
            }
        });
    }

    private void saveRecords() {
        if (!dirty || storageFolder == null) return;
        PERSISTENCE.submit(storageFolder, chunks);
        dirty = false;
    }

    private Path getJsonFile() { return storageFolder.resolve("stashes.json"); }
    private Path getCsvFile() { return storageFolder.resolve("stashes.csv"); }

    @Override
    public String getInfoString() {
        return String.valueOf(chunks.size());
    }

    private void sendChatNotification(Chunk chunk) {
        MutableComponent coords = Component.literal(chunk.chunkPos.getMiddleBlockX() + ", " + chunk.chunkPos.getMiddleBlockZ())
            .setStyle(Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .applyFormat(ChatFormatting.UNDERLINE)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(chunk.context() + " / Path to stash")))
                .withClickEvent(new RunnableClickEvent(() -> navigate(chunk))));

        MutableComponent message = Component.literal("Found stash at ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(coords)
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" / " + chunk.context()).withStyle(ChatFormatting.GRAY));

        ChatUtils.sendMsg(message);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        ensureContext();
        if (tracerPositions.isEmpty()) return;
        tracerPositions.keySet().removeIf(chunk -> !current(chunk));

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        tracerPositions.entrySet().removeIf(entry -> {
            Vec3 pos = entry.getValue();
            double horizontalDist = Math.hypot(pos.x - playerX, pos.z - playerZ);
            return horizontalDist <= traceArrivalDistance.get();
        });

        if (!renderTracer.get() && !renderChunkColumn.get()) return;

        for (Vec3 pos : tracerPositions.values()) {
            double horizontalDist = Math.hypot(pos.x - playerX, pos.z - playerZ);
            if (horizontalDist > traceMaxDistance.get()) continue;

            if (renderTracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.x, mc.player.getEyeY(), pos.z, traceColor.get()
                );
            }

            if (renderChunkColumn.get()) {
                double x1 = pos.x - 0.5;
                double x2 = pos.x + 0.5;
                double z1 = pos.z - 0.5;
                double z2 = pos.z + 0.5;

                int bottomY = mc.level.getMinY();
                int topY = bottomY + mc.level.dimensionType().height();

                event.renderer.line(x1, bottomY, z1, x1, topY, z1, traceColumnColor.get());
                event.renderer.line(x1, bottomY, z2, x1, topY, z2, traceColumnColor.get());
                event.renderer.line(x2, bottomY, z1, x2, topY, z1, traceColumnColor.get());
                event.renderer.line(x2, bottomY, z2, x2, topY, z2, traceColumnColor.get());
            }
        }
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public static class Chunk {
        public final ChunkPos chunkPos;
        public String server = CoordinateContext.UNKNOWN, dimension = CoordinateContext.UNKNOWN;
        public long firstSeen, lastSeen;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, otherStorage;

        public Chunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }

        public Chunk copy() {
            Chunk copy = new Chunk(chunkPos);
            copy.server = server; copy.dimension = dimension;
            copy.firstSeen = firstSeen; copy.lastSeen = lastSeen;
            copy.chests = chests; copy.barrels = barrels; copy.shulkers = shulkers;
            copy.enderChests = enderChests; copy.furnaces = furnaces;
            copy.dispensersDroppers = dispensersDroppers; copy.hoppers = hoppers;
            copy.otherStorage = otherStorage;
            return copy;
        }

        public void normalize() {
            if (server == null || server.isBlank()) server = CoordinateContext.UNKNOWN;
            if (dimension == null || dimension.isBlank()) dimension = CoordinateContext.UNKNOWN;
            firstSeen = Math.max(0, firstSeen); lastSeen = Math.max(firstSeen, lastSeen);
        }

        public boolean matches(String server, String dimension) {
            return !CoordinateContext.UNKNOWN.equals(this.server) && !CoordinateContext.UNKNOWN.equals(this.dimension)
                && Objects.equals(this.server, server) && Objects.equals(this.dimension, dimension);
        }

        public String context() {
            return "Server: " + server + " / " + dimension + " / First: " + CoordinateContext.time(firstSeen)
                + " / Last: " + CoordinateContext.time(lastSeen);
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers + otherStorage;
        }

        public void write(BufferedWriter writer) throws IOException {
            String line = CSV_FIELDS.stream()
                .map(field -> String.valueOf(field.getter().applyAsInt(this)))
                .collect(Collectors.joining(","));
            writer.write(line + "," + CoordinateContext.csv(server) + "," + CoordinateContext.csv(dimension)
                + "," + CoordinateContext.csv(CoordinateContext.csvTime(firstSeen)) + "," + CoordinateContext.csv(CoordinateContext.csvTime(lastSeen)));
            writer.newLine();
        }

        public boolean countsEqual(Chunk c) {
            if (c == null) return false;
            return chests == c.chests && barrels == c.barrels && shulkers == c.shulkers && enderChests == c.enderChests && furnaces == c.furnaces && dispensersDroppers == c.dispensersDroppers && hoppers == c.hoppers && otherStorage == c.otherStorage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Chunk chunk = (Chunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos) && Objects.equals(server, chunk.server) && Objects.equals(dimension, chunk.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos, server, dimension);
        }
    }

    private static class ChunkScreen extends WindowScreen {
        private final Chunk chunk;

        public ChunkScreen(GuiTheme theme, Chunk chunk) {
            super(theme, "Chunk at " + chunk.chunkPos.getMiddleBlockX() + ", " + chunk.chunkPos.getMiddleBlockZ());

            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = add(theme.table()).expandX().widget();

            t.add(theme.label(chunk.context(), 440)).expandX();
            t.row();

            // Total
            t.add(theme.label("Total:"));
            t.add(theme.label(chunk.getTotal() + ""));
            t.row();

            t.add(theme.horizontalSeparator()).expandX();
            t.row();

            // Separate
            t.add(theme.label("Chests:"));
            t.add(theme.label(chunk.chests + ""));
            t.row();

            t.add(theme.label("Barrels:"));
            t.add(theme.label(chunk.barrels + ""));
            t.row();

            t.add(theme.label("Shulkers:"));
            t.add(theme.label(chunk.shulkers + ""));
            t.row();

            t.add(theme.label("Ender Chests:"));
            t.add(theme.label(chunk.enderChests + ""));
            t.row();

            t.add(theme.label("Furnaces:"));
            t.add(theme.label(chunk.furnaces + ""));
            t.row();

            t.add(theme.label("Dispensers and droppers:"));
            t.add(theme.label(chunk.dispensersDroppers + ""));
            t.row();

            t.add(theme.label("Hoppers:"));
            t.add(theme.label(chunk.hoppers + ""));
            t.row();
            t.add(theme.label("Other selected storage:"));
            t.add(theme.label(chunk.otherStorage + ""));
        }
    }
}
