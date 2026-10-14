package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;
import java.util.*;

public class SpawnerFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").description("Loaded spawner discovery range.").defaultValue(128).range(16, 256).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").description("Spawner label and outline color.").defaultValue(new SettingColor(255, 180, 80)).build());
    private final Map<BlockPos, String> spawners = new LinkedHashMap<>();
    private Iterator<BlockEntity> scanning;
    private Object world;
    public SpawnerFinder() { super(Categories.Render, "spawner-finder", "Labels loaded normal and trial mob spawners through walls.", "SpawnerFinder"); }
    private void clear() { spawners.clear(); scanning = null; world = mc.level; }
    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void leave(GameLeftEvent event) { clear(); }
    @EventHandler private void tick(TickEvent.Post event) {
        if (mc.level != world) clear();
        if (mc.player == null) return;
        spawners.keySet().removeIf(pos -> !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !(mc.level.getBlockEntity(pos) instanceof SpawnerBlockEntity || mc.level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity)
            || mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > range.get() * range.get());
        if (scanning == null || !scanning.hasNext()) scanning = Utils.blockEntities().iterator();
        for (int i = 0; i < 256 && scanning.hasNext(); i++) {
            var entity = scanning.next();
            if ((entity instanceof SpawnerBlockEntity || entity instanceof TrialSpawnerBlockEntity)
                && mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(entity.getBlockPos())) <= range.get() * range.get())
                spawners.put(entity.getBlockPos().immutable(), entity instanceof TrialSpawnerBlockEntity ? "Trial spawner" : "Mob spawner");
        }
        while (spawners.size() > 512) spawners.remove(spawners.keySet().iterator().next());
    }
    @EventHandler private void render(Render3DEvent event) {
        if (mc.level != world) return;
        for (var pos : spawners.keySet()) event.renderer.box(pos, color.get(), color.get(), ShapeMode.Lines, 0);
    }
    @EventHandler private void labels(Render2DEvent event) {
        if (mc.level != world) return;
        var text = TextRenderer.get(); text.begin(event.graphics, 0.8);
        int count = 0;
        for (var entry : spawners.entrySet()) {
            if (++count > 64) break;
            var pos = new Vector3d(entry.getKey().getX() + 0.5, entry.getKey().getY() + 1.2, entry.getKey().getZ() + 0.5);
            if (NametagUtils.to2D(pos, 1, false)) text.render(entry.getValue(), pos.x - text.getWidth(entry.getValue()) / 2, pos.y, color.get(), true);
        }
        text.end();
    }
}
