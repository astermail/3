package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import java.util.List;

public class InvCleaner extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<List<Item>> discard = sg.add(new ItemListSetting.Builder().name("discard-items").description("Only these explicitly selected items are discarded. Empty by default.").build());
    private final Setting<Boolean> hotbar = sg.add(new BoolSetting.Builder().name("include-hotbar").description("Also discard selected items from hotbar slots.").defaultValue(false).build());
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ticks").description("Delay between drops.").defaultValue(5).range(1, 40).build());
    private int timer;
    public InvCleaner() { super(Categories.Player, "inv-cleaner", "Discards an explicit item list one stack at a time; equipped armor is preserved.", "InvCleaner"); }
    @Override public void onActivate() { timer = 0; }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || PotionUse.anyBusy()
            || mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (timer-- > 0) return;
        timer = delay.get();
        for (int i = hotbar.get() ? 0 : 9; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && discard.get().contains(stack.getItem())) { InvUtils.drop().slot(i); return; }
        }
    }
}
