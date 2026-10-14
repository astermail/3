package com.gaspoweredcake.client33.systems.modules.misc;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner;
import com.gaspoweredcake.client33.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestSteal extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ticks").description("Ticks between transfer attempts.").defaultValue(2).range(1, 40).build());
    private final Setting<List<Item>> items = sg.add(new ItemListSetting.Builder().name("items").description("Items to include or exclude. An empty list takes all items.").build());
    private final Setting<ContainerTransferPlanner.Filter> filter = sg.add(new EnumSetting.Builder<ContainerTransferPlanner.Filter>()
        .name("filter-mode").description("Take only listed items, or take everything except the list.").defaultValue(ContainerTransferPlanner.Filter.Include).build());
    private final Setting<Integer> retryDelay = sg.add(new IntSetting.Builder().name("retry-delay-ticks")
        .description("Minimum wait before retrying the same slot after a partial or rejected transfer. Other slots remain eligible.").defaultValue(20).range(1, 200).build());
    private final Map<Integer, Long> retries = new HashMap<>();
    private int timer;
    private long ticks;
    private Object menu, world;
    private String status = "Open a chest";
    public ChestSteal() { super(Categories.Misc, "chest-steal", "Transfers fitting chest and shulker items with include/exclude filters and per-slot retries.", "ChestSteal"); }
    private void clear() { timer = 0; ticks = 0; menu = world = null; retries.clear(); status = "Open a chest"; }
    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void leave(GameLeftEvent event) { clear(); }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) { clear(); return; }
        ticks++;
        var current = mc.player.containerMenu;
        if (menu != current || world != mc.level) { menu = current; world = mc.level; retries.clear(); timer = delay.get(); }
        if (mc.gui.screen() == null || !(current instanceof ChestMenu || current instanceof ShulkerBoxMenu)) { status = "Open a chest"; return; }
        if (!current.getCarried().isEmpty()) { status = "Cursor holds an item"; return; }
        if (timer > 0) { timer--; return; }
        var decision = ContainerTransferPlanner.choose(current.slots, current.slots.size() - 36,
            stack -> ContainerTransferPlanner.matches(stack, items.get(), filter.get()),
            slot -> current.slots.get(slot).isActive() && current.slots.get(slot).mayPickup(mc.player) && ticks >= retries.getOrDefault(slot, 0L));
        if (decision.slot() >= 0) {
            retries.put(decision.slot(), ticks + retryDelay.get());
            InvUtils.shiftClick().slotId(decision.slot());
            timer = delay.get() - 1;
            status = "Transfer requested";
        } else status = decision.matching() == 0 ? "No matching items" : decision.waiting() ? "Waiting for a slot" : "No inventory room";
    }
    @Override public String getInfoString() { return status; }
}
