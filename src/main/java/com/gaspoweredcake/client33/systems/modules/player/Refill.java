package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.*;
import meteordevelopment.orbit.EventHandler;

public class Refill extends Module {
    private final Setting<Integer> slot = settings.getDefaultGroup().add(new IntSetting.Builder().name("hotbar-slot").description("Refill this empty hotbar slot with a healing item.").defaultValue(8).range(1, 9).sliderRange(1, 9).build());
    private final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder().name("delay-ticks").description("Delay between inventory moves.").defaultValue(5).range(1, 40).build());
    private int timer;
    public Refill() { super(Categories.Player, "refill", "Refills an empty healing hotbar slot from your inventory."); }
    @Override public void onActivate() { timer = 0; }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || PotionUse.anyBusy()
            || mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (timer > 0) { timer--; return; }
        if (!mc.player.getInventory().getItem(slot.get() - 1).isEmpty()) return;
        var found = InvUtils.find(PotionUse::healing, 9, 35);
        if (found.found()) { InvUtils.move().from(found.slot()).toHotbar(slot.get() - 1); timer = delay.get(); }
    }
}
