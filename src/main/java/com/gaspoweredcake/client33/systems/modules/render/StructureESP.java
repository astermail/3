package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.LabelLayout;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class StructureESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Boolean> loadedPois = sg.add(new BoolSetting.Builder().name("loaded-pois").description("Scan loaded block-state palettes for registered 26.2 POIs (beds, workstations, portals, etc.).").defaultValue(true).build());
    private final Setting<Boolean> signs = sg.add(new BoolSetting.Builder().name("sign-markers").description("Highlight loaded signs and show their received front text. Signs indicate activity, not a confirmed base or placement date.").defaultValue(true).build());
    private final Setting<Boolean> debugData = sg.add(new BoolSetting.Builder().name("received-debug-data").description("Render structure/POI debug data only when actually sent by the server; sends no debug subscription requests.").defaultValue(true).build());
    private final Setting<List<Block>> markers = sg.add(new BlockListSetting.Builder().name("landmark-blocks").description("Additional loaded block markers. A block marker does not confirm a generated structure.").defaultValue(Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.SCULK_SHRIEKER, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL).build());
    private final Setting<Integer> budget = sg.add(new IntSetting.Builder().name("blocks-per-tick").description("Maximum detailed block checks per tick; nonmatching palettes are skipped.").defaultValue(2048).range(256, 16384).sliderRange(256, 8192).build());
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").description("Discovery and render distance in blocks.").defaultValue(128).range(16, 512).sliderRange(16, 512).build());
    private final Setting<Integer> ttl = sg.add(new IntSetting.Builder().name("debug-expiry-seconds").description("Expire received debug observations after this interval.").defaultValue(120).range(5, 600).sliderRange(5, 600).build());
    private final Setting<Boolean> labels = sg.add(new BoolSetting.Builder().name("labels").description("Label POIs by observed source; at most 32 labels per frame.").defaultValue(true).build());
    private record Point(String label, long seen, boolean debug) {}
    private record Structures(List<BoundingBox> boxes, long seen) {}
    private record Pending(Object packet, Object world) {}
    private final LinkedHashMap<BlockPos, Point> points = new LinkedHashMap<>();
    private final LinkedHashMap<ChunkPos, Structures> structures = new LinkedHashMap<>();
    private final ArrayBlockingQueue<Pending> received = new ArrayBlockingQueue<>(128);
    private final ArrayDeque<ChunkAccess> queue = new ArrayDeque<>();
    private ChunkAccess scanning;
    private int section, offset;
    private Object world;
    public StructureESP() { super(Categories.Render, "structure-esp", "Loaded POI/landmark markers and server-sent 26.2 structure debug bounds. No hidden-data or seed inference.", "POI ESP", "Sign ESP", "Landmark Finder"); }
    private void clear() { points.clear(); structures.clear(); received.clear(); queue.clear(); scanning = null; section = offset = 0; world = mc.level; }
    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void onLeave(GameLeftEvent event) { clear(); }
    @EventHandler private void onPacket(PacketEvent.Receive event) {
        if (!event.isCancelled() && debugData.get()
            && (event.packet instanceof ClientboundDebugChunkValuePacket || event.packet instanceof ClientboundDebugBlockValuePacket))
            received.offer(new Pending(event.packet, mc.level));
    }
    private boolean matches(BlockState state) { return (signs.get() && state.is(BlockTags.ALL_SIGNS)) || (loadedPois.get() && PoiTypes.hasPoi(state)) || markers.get().contains(state.getBlock()); }
    private boolean nearby(BlockPos pos) { return mc.player.distanceToSqr(pos.getX(), mc.player.getY(), pos.getZ()) <= range.get() * range.get(); }
    @EventHandler private void onTick(TickEvent.Post event) {
        if (world != mc.level) clear();
        long now = CombatTelemetry.now();
        for (int i = 0; i < 128; i++) {
            Pending pending = received.poll();
            if (pending == null) break;
            if (!debugData.get() || pending.world != mc.level) continue;
            if (pending.packet instanceof ClientboundDebugChunkValuePacket packet && packet.update().subscription() == DebugSubscriptions.STRUCTURES) {
                Object value = packet.update().value().orElse(null);
                if (value instanceof List<?> list) {
                    List<BoundingBox> bounds = list.stream().filter(DebugStructureInfo.class::isInstance).map(DebugStructureInfo.class::cast)
                        .limit(64).map(DebugStructureInfo::boundingBox).toList();
                    structures.put(packet.chunkPos(), new Structures(bounds, now));
                } else structures.remove(packet.chunkPos());
            } else if (pending.packet instanceof ClientboundDebugBlockValuePacket packet && packet.update().subscription() == DebugSubscriptions.POIS) {
                if (packet.update().value().orElse(null) instanceof DebugPoiInfo info)
                    points.put(packet.blockPos().immutable(), new Point("POI / server debug", now, true));
                else points.remove(packet.blockPos());
            }
        }
        points.entrySet().removeIf(e -> e.getValue().debug ? !debugData.get() || now - e.getValue().seen > ttl.get() * 1000L
            : !mc.level.hasChunk(e.getKey().getX() >> 4, e.getKey().getZ() >> 4) || !matches(mc.level.getBlockState(e.getKey())));
        structures.entrySet().removeIf(e -> !debugData.get() || now - e.getValue().seen > ttl.get() * 1000L);
        if (scanning == null && queue.isEmpty()) for (ChunkAccess chunk : Utils.chunks()) {
            if (nearby(new BlockPos(chunk.getPos().x() * 16 + 8, 0, chunk.getPos().z() * 16 + 8))) queue.addLast(chunk);
        }
        int work = 0, skippedSections = 0;
        while (work < budget.get() && skippedSections < 64) {
            if (scanning == null) {
                scanning = queue.pollFirst(); section = offset = 0;
                if (scanning == null) break;
            }
            if (!mc.level.hasChunk(scanning.getPos().x(), scanning.getPos().z())
                || mc.level.getChunk(scanning.getPos().x(), scanning.getPos().z()) != scanning
                || section >= scanning.getSections().length) {
                scanning = null; skippedSections++; continue;
            }
            var palette = scanning.getSection(section);
            if (offset == 0 && (palette.hasOnlyAir() || !palette.maybeHas(this::matches))) { section++; skippedSections++; continue; }
            int x = offset & 15, z = (offset >>> 4) & 15, y = (offset >>> 8) & 15;
            BlockState state = palette.getBlockState(x, y, z);
            if (matches(state)) {
                var pos = new BlockPos(scanning.getPos().x() * 16 + x, scanning.getMinY() + section * 16 + y, scanning.getPos().z() * 16 + z);
                if (!points.containsKey(pos) || !points.get(pos).debug)
                    points.put(pos, new Point(state.getBlock().getName().getString() + (PoiTypes.hasPoi(state) ? " / loaded POI" : " / block marker"), now, false));
            }
            work++;
            if (++offset == 4096) { offset = 0; section++; }
        }
        while (points.size() > 4096) points.remove(points.keySet().iterator().next());
        while (structures.size() > 256) structures.remove(structures.keySet().iterator().next());
    }
    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || world != mc.level) return;
        int count = 0;
        for (var entry : points.entrySet()) {
            if (!nearby(entry.getKey())) continue;
            if (++count > 512) break;
            Color color = entry.getValue().debug ? new Color(255, 186, 90) : new Color(100, 215, 230);
            event.renderer.box(entry.getKey(), new Color(color).a(20), color, ShapeMode.Both, 0);
        }
        int drawn = 0;
        for (var entry : structures.values()) for (var box : entry.boxes) {
            if (!nearby(box.getCenter()) || ++drawn > 256) continue;
            event.renderer.box(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1,
                new Color(255, 186, 90, 10), new Color(255, 186, 90), ShapeMode.Both, 0);
        }
    }
    @EventHandler private void onLabels(Render2DEvent event) {
        if (!labels.get() || mc.player == null || world != mc.level) return;
        TextRenderer text = TextRenderer.get(); text.begin(event.graphics, 0.8);
        Renderer2D.COLOR.begin();
        double width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
        LabelLayout layout = new LabelLayout(width, height);
        Vector3d projected = new Vector3d();
        int count = 0;
        for (var entry : points.entrySet()) {
            if (count >= 32) break;
            if (!nearby(entry.getKey())) continue;
            var point = entry.getKey();
            projected.set(point.getX() + 0.5, point.getY() + 1.2, point.getZ() + 0.5);
            if (!NametagUtils.to2D(projected, 1, false) || projected.z < 0 || projected.z > 1) continue;
            String label = pointLabel(point, entry.getValue());
            var rect = layout.place(projected.x, projected.y, text.getWidth(label) + 14, text.getHeight() + 10);
            if (rect == null) continue;
            Renderer2D.COLOR.quad(rect.x(), rect.y(), rect.width(), rect.height(), new Color(12, 16, 23, 218));
            Renderer2D.COLOR.quad(rect.x(), rect.y(), 2, rect.height(), new Color(100, 215, 230, 210));
            text.render(label, rect.x() + 7, rect.y() + 5, Color.WHITE, true);
            count++;
        }
        Renderer2D.COLOR.render();
        text.end();
    }
    private String pointLabel(BlockPos pos, Point point) {
        if (!point.debug && signs.get() && mc.level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            StringBuilder label = new StringBuilder("Sign / observed: ");
            for (int line = 0; line < 4 && label.length() < 96; line++) {
                String value = sign.getText(true).getMessage(line, true).getString().strip();
                if (!value.isEmpty()) {
                    if (label.length() > 17) label.append(" | ");
                    label.append(value, 0, Math.min(value.length(), Math.max(0, 96 - label.length())));
                }
            }
            return label.toString();
        }
        return point.label;
    }
    @Override public String getInfoString() { return points.size() + " POIs / " + structures.values().stream().mapToInt(s -> s.boxes.size()).sum() + " server bounds"; }
}
