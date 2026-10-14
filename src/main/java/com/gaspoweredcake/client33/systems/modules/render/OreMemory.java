package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.ChunkDataEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.world.BlockObservationHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Only remembers received/loaded observations; never probes or reconstructs hidden server blocks. */
public class OreMemory extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<List<Block>> blocks = sg.add(new BlockListSetting.Builder().name("blocks").description("Block types to remember after they are observed.").defaultValue(Xray.ORES).onChanged(value -> { if (isActive()) clear(); }).build());
    private final Setting<Integer> lifetime = sg.add(new IntSetting.Builder().name("expiry-seconds").description("Expire last observations after this long. Server-hidden replacements do not refresh their age.").defaultValue(120).range(5, 600).build());
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").description("Maximum observation/render distance.").defaultValue(96).range(16, 256).build());
    private final Setting<Integer> budget = sg.add(new IntSetting.Builder().name("blocks-per-tick").description("Maximum loaded block checks per tick; nonmatching palettes are skipped.").defaultValue(2048).range(256, 8192).build());
    private final Setting<Boolean> labels = sg.add(new BoolSetting.Builder().name("labels").description("Label present observations and last-seen memories; at most 32 per frame.").defaultValue(true).build());
    private record Received(BlockPos pos, BlockState state, Object world, long seen) {}
    private final ArrayBlockingQueue<Received> received = new ArrayBlockingQueue<>(2048);
    private final BlockObservationHistory<BlockPos, Block> history = new BlockObservationHistory<>(4096);
    private final LinkedHashMap<Long, ChunkAccess> chunks = new LinkedHashMap<>();
    private ChunkAccess scanning;
    private int section, offset;
    private Object world;
    private long refillAt;
    private Iterator<ChunkAccess> refill;
    private Map<BlockPos, BlockObservationHistory.Observation<Block>> renderSnapshot = Map.of();
    public OreMemory() { super(Categories.Render, "ore-memory", "Remembers recently observed ores with expiring last-seen markers. Cannot reveal server-withheld blocks.", "Revealed Xray"); }
    private void clear() {
        history.clear(); received.clear(); chunks.clear(); scanning = null; section = offset = 0; refillAt = 0; refill = null; renderSnapshot = Map.of(); world = mc.level;
    }
    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void leave(GameLeftEvent event) { clear(); }
    @EventHandler(priority = -2000) private void packet(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        Object level = mc.level; long now = CombatTelemetry.now();
        if (event.packet instanceof ClientboundBlockUpdatePacket packet) received.offer(new Received(packet.getPos().immutable(), packet.getBlockState(), level, now));
        else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) packet.runUpdates((pos, state) -> received.offer(new Received(pos.immutable(), state, level, now)));
    }
    @EventHandler private void chunk(ChunkDataEvent event) {
        if (world == mc.level) enqueue(event.chunk());
    }
    private void enqueue(ChunkAccess chunk) {
        if (chunks.size() < 128 && scanning != chunk) chunks.put(chunk.getPos().pack(), chunk);
    }
    private boolean nearby(BlockPos pos) { return mc.player != null && mc.player.distanceToSqr(pos.getX(), mc.player.getY(), pos.getZ()) <= range.get() * range.get(); }
    private boolean loaded(BlockPos pos) { return mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4); }
    private void observe(BlockPos pos, BlockState state, long seen) {
        if (!loaded(pos) || !nearby(pos)) return;
        if (blocks.get().contains(state.getBlock())) history.observe(pos.immutable(), state.getBlock(), seen);
        else if (state.isAir()) history.forget(pos);
        // Stone may be a rehidden ore or a real replacement. Keep only the dated observation, never a current claim.
    }
    @EventHandler private void tick(TickEvent.Post event) {
        if (world != mc.level) clear();
        if (mc.player == null || mc.level == null) return;
        long now = CombatTelemetry.now();
        for (int i = 0; i < 512; i++) {
            var update = received.poll(); if (update == null) break;
            if (update.world == world) observe(update.pos, update.state, update.seen);
        }
        history.expire(now, lifetime.get() * 1000L, pos -> loaded(pos) && nearby(pos));
        if (refill == null && scanning == null && chunks.isEmpty() && now >= refillAt) refill = Utils.chunks().iterator();
        // Retain the iterator across ticks: restarting at the first 128 chunks starves larger ranges.
        for (int candidates = 0; refill != null && chunks.size() < 128 && candidates < 64; candidates++) {
            if (!refill.hasNext()) { refill = null; refillAt = now + 10000; break; }
            var candidate = refill.next();
            if (nearby(new BlockPos(candidate.getPos().x() * 16 + 8, 0, candidate.getPos().z() * 16 + 8))) enqueue(candidate);
        }
        int work = 0, skipped = 0;
        while (work < budget.get() && skipped < 64) {
            if (scanning == null) {
                if (chunks.isEmpty()) break;
                var first = chunks.entrySet().iterator(); scanning = first.next().getValue(); first.remove(); section = offset = 0;
            }
            if (!mc.level.hasChunk(scanning.getPos().x(), scanning.getPos().z())
                || mc.level.getChunk(scanning.getPos().x(), scanning.getPos().z()) != scanning
                || !nearby(new BlockPos(scanning.getPos().x() * 16 + 8, 0, scanning.getPos().z() * 16 + 8))
                || section >= scanning.getSections().length) { scanning = null; skipped++; continue; }
            var palette = scanning.getSection(section);
            if (offset == 0 && (palette.hasOnlyAir() || !palette.maybeHas(state -> blocks.get().contains(state.getBlock())))) { section++; skipped++; continue; }
            int x = offset & 15, z = offset >>> 4 & 15, y = offset >>> 8 & 15;
            var state = palette.getBlockState(x, y, z);
            if (blocks.get().contains(state.getBlock())) observe(new BlockPos(scanning.getPos().x() * 16 + x, scanning.getMinY() + section * 16 + y, scanning.getPos().z() * 16 + z), state, now);
            work++; if (++offset == 4096) { offset = 0; section++; }
        }
        renderSnapshot = history.snapshot();
    }
    private Color color(BlockPos pos, BlockObservationHistory.Observation<Block> observation) {
        boolean present = mc.level.getBlockState(pos).getBlock() == observation.value();
        double age = Math.max(0, CombatTelemetry.now() - observation.seen()) / (lifetime.get() * 1000.0);
        return present ? new Color(77, 224, 255, 220) : new Color(255, 193, 100, (int) (180 * Math.clamp(1 - age, 0, 1)));
    }
    @EventHandler private void render(Render3DEvent event) {
        if (mc.player == null || world != mc.level) return;
        int drawn = 0;
        for (var entry : renderSnapshot.entrySet()) {
            if (!loaded(entry.getKey()) || !nearby(entry.getKey())) continue;
            if (++drawn > 512) break;
            var color = color(entry.getKey(), entry.getValue());
            event.renderer.box(entry.getKey(), color, color, ShapeMode.Lines, 0);
        }
    }
    @EventHandler private void labels(Render2DEvent event) {
        if (!labels.get() || mc.player == null || world != mc.level) return;
        var text = TextRenderer.get(); text.begin(event.graphics, 0.75); int count = 0;
        for (var entry : renderSnapshot.entrySet()) {
            var pos = entry.getKey(); if (!loaded(pos) || !nearby(pos)) continue;
            if (++count > 32) break;
            boolean present = mc.level.getBlockState(pos).getBlock() == entry.getValue().value();
            String label = entry.getValue().value().getName().getString() + (present ? " / observed" : " / last seen " + Math.max(0, CombatTelemetry.now() - entry.getValue().seen()) / 1000 + "s ago");
            var point = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
            if (NametagUtils.to2D(point, 1, false)) text.render(label, point.x - text.getWidth(label) / 2, point.y, color(pos, entry.getValue()), true);
        }
        text.end();
    }
    @Override public String getInfoString() { return history.size() + " observations"; }
}
