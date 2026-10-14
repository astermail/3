package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.ChunkDataEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.player.TeleportRequest;
import com.gaspoweredcake.client33.utils.world.RtpSearchSession;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Server-supported random teleports, with an explicit bounded storage-search mode. */
public class RandomTeleport extends Module {
    public enum Mode { Once, FindStorage }
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Mode> mode = general.add(new EnumSetting.Builder<Mode>().name("mode")
        .description("Once sends one command. Find Storage repeats after arrival and scanning until a qualifying chunk is found.")
        .defaultValue(Mode.Once).build());
    private final Setting<String> command = general.add(new StringSetting.Builder().name("command")
        .description("Server's random teleport command, including any arguments. Requires server support and permission.")
        .defaultValue("rtp").build());
    private final Setting<Integer> minimumStorage = general.add(new IntSetting.Builder().name("minimum-storage-count")
        .description("Stop at this many selected storage blocks in one loaded chunk. 31 means more than 30.")
        .defaultValue(31).range(1, 4096).sliderRange(1, 100).visible(this::automatic).build());
    private final Setting<List<BlockEntityType<?>>> storageBlocks = general.add(new StorageBlockListSetting.Builder().name("storage-blocks")
        .description("Storage types included in each chunk count.").defaultValue(StorageBlockListSetting.STORAGE_BLOCKS).visible(this::automatic).build());
    private final Setting<Integer> interval = general.add(new IntSetting.Builder().name("request-interval-seconds")
        .description("Minimum interval between commands. Set at least the server's RTP cooldown.")
        .defaultValue(30).range(5, 3600).sliderRange(5, 120).visible(this::automatic).build());
    private final Setting<Integer> timeout = general.add(new IntSetting.Builder().name("teleport-timeout-seconds")
        .description("Stop if no teleport is observed; prevents repeated denied or queued commands.")
        .defaultValue(45).range(5, 3600).sliderRange(5, 120).visible(this::automatic).build());
    private final Setting<Integer> scanSeconds = general.add(new IntSetting.Builder().name("scan-seconds")
        .description("Wait this long after arrival for nearby chunk data before trying another destination.")
        .defaultValue(10).range(2, 120).visible(this::automatic).build());
    private final Setting<Integer> maxRequests = general.add(new IntSetting.Builder().name("maximum-teleports")
        .description("Stop after this many requests without a match.").defaultValue(50).range(1, 1000).visible(this::automatic).build());
    private final Setting<Double> displacement = general.add(new DoubleSetting.Builder().name("arrival-distance")
        .description("Movement required to confirm teleport arrival. Increase if normal movement causes false arrivals.")
        .defaultValue(32).range(8, 10000).sliderRange(8, 128).visible(this::automatic).build());
    private final Setting<Integer> radius = general.add(new IntSetting.Builder().name("scan-radius")
        .description("Horizontal radius in blocks. Only chunks received from the server can be searched.")
        .defaultValue(128).range(16, 512).visible(this::automatic).build());
    private RtpSearchSession session;
    private Iterator<ChunkAccess> loaded;
    private final Map<ChunkPos, LevelChunk> updates = new LinkedHashMap<>();
    private String requestCommand, status = "Ready";
    private WLabel statusLabel;

    public RandomTeleport() {
        super(Categories.World, "random-teleport", "Request RTP once or automatically search received chunks for storage. Stops at a match, timeout or request limit.",
            "RTP", "Wild", "Random TP", "Auto RTP", "Storage Search");
    }
    private boolean automatic() { return mode.get() == Mode.FindStorage; }
    @Override public void onActivate() { session = null; loaded = null; updates.clear(); status("Ready"); }
    @Override public void onDeactivate() { session = null; loaded = null; updates.clear(); }
    @Override public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        statusLabel = list.add(theme.label(status, 440)).widget();
        list.add(theme.button("Stop search")).widget().action = () -> { if (isActive()) finish("Stopped"); };
        return list;
    }
    private void status(String text) { status = text; if (statusLabel != null) statusLabel.set(text); }
    private void finish(String text) { status(text); if (isActive()) toggle(); }
    @EventHandler private void onLeave(GameLeftEvent event) { finish("Disconnected"); }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (session != null && session.state() == RtpSearchSession.State.Scanning && event.chunk().getLevel() == mc.level && nearby(event.chunk()))
            updates.put(event.chunk().getPos(), event.chunk());
    }
    private boolean nearby(LevelChunk chunk) {
        double dx = Math.max(Math.abs(mc.player.getX() - chunk.getPos().getMiddleBlockX()) - 8, 0);
        double dz = Math.max(Math.abs(mc.player.getZ() - chunk.getPos().getMiddleBlockZ()) - 8, 0);
        return dx * dx + dz * dz <= (double) radius.get() * radius.get();
    }
    private boolean scan(LevelChunk chunk) {
        if (chunk.getLevel() != mc.level || !nearby(chunk)) return false;
        var finding = StashFinder.scanChunk(chunk, storageBlocks.get(), List.of());
        if (!session.match(finding.getTotal())) return false;
        Modules.get().get(StashFinder.class).saveFinding(finding);
        String result = "Found " + finding.getTotal() + " storage blocks at " + finding.chunkPos.getMiddleBlockX() + ", " + finding.chunkPos.getMiddleBlockZ();
        info("%s / %s", result, finding.context());
        finish(result);
        return true;
    }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mc.player.isAlive()) { finish("Join a world alive before requesting RTP"); return; }
        try {
            if (session == null) {
                requestCommand = TeleportRequest.command(command.get());
                if (!automatic()) {
                    mc.player.connection.sendCommand(requestCommand);
                    info("Random teleport requested. The server controls destination, permissions and cooldown.");
                    finish("Requested once"); return;
                }
                session = new RtpSearchSession(minimumStorage.get(), maxRequests.get(), interval.get() * 1000L,
                    timeout.get() * 1000L, scanSeconds.get() * 1000L, displacement.get());
            }
            var previous = session.state();
            if (session.tick(CombatTelemetry.now(), new RtpSearchSession.Position(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().identifier().toString()))) {
                loaded = null; updates.clear();
                mc.player.connection.sendCommand(requestCommand);
            }
            switch (session.state()) {
                case TimedOut -> { warning("RTP did not arrive. Check command permissions, queue and cooldown."); finish("Teleport timed out"); return; }
                case Limit -> { finish("Request limit reached without a match"); return; }
                case Scanning -> {
                    if (previous != RtpSearchSession.State.Scanning) { loaded = Utils.chunks().iterator(); updates.clear(); }
                    long deadline = java.lang.System.nanoTime() + 2_000_000;
                    for (int i = 0; i < 8 && java.lang.System.nanoTime() < deadline; i++) {
                        LevelChunk chunk = null;
                        if (!updates.isEmpty()) {
                            var entry = updates.entrySet().iterator().next(); chunk = entry.getValue(); updates.remove(entry.getKey());
                        } else if (loaded != null && loaded.hasNext()) {
                            var candidate = loaded.next(); if (candidate instanceof LevelChunk levelChunk) chunk = levelChunk;
                        } else break;
                        if (chunk != null && scan(chunk)) return;
                    }
                }
                default -> { }
            }
            status(session.state() + " / " + session.requests() + " teleports / target " + minimumStorage.get() + "+ storage");
        } catch (IllegalArgumentException invalid) { error(invalid.getMessage()); finish("Invalid RTP settings"); }
    }
    @Override public String getInfoString() { return status; }
}
