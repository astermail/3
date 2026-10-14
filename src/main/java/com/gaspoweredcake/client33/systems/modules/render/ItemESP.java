package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.world.entity.item.ItemEntity;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;

public class ItemESP extends Module {
    private final Setting<Double> range = settings.getDefaultGroup().add(new DoubleSetting.Builder().name("range").description("Dropped item label range.").defaultValue(64).range(8, 256).build());
    public ItemESP() { super(Categories.Render, "item-esp", "Shows dropped item names and stack counts through walls.", "ItemESP"); }
    @EventHandler private void render(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        var text = TextRenderer.get(); text.begin(event.graphics, 0.8); int count = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || !item.isAlive() || mc.player.distanceTo(item) > range.get()) continue;
            if (++count > 128) break;
            var pos = new Vector3d(item.getX(), item.getY() + 0.5, item.getZ());
            if (!NametagUtils.to2D(pos, 1, false)) continue;
            String label = item.getItem().getHoverName().getString() + " x" + item.getItem().getCount();
            text.render(label, pos.x - text.getWidth(label) / 2, pos.y, Color.WHITE, true);
        }
        text.end();
    }
}
