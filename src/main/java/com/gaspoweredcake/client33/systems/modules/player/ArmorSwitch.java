package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import meteordevelopment.orbit.EventHandler;
import java.util.List;

public class ArmorSwitch extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<List<Item>> first = sg.add(new ItemListSetting.Builder().name("set-a").description("Armor item types for the first set.").defaultValue(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS).build());
    private final Setting<List<Item>> second = sg.add(new ItemListSetting.Builder().name("set-b").description("Armor item types for the second set.").defaultValue(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS).build());
    private final Setting<Set> desired = sg.add(new EnumSetting.Builder<Set>().name("set").description("Set to equip on activation. Switch with the key below.").defaultValue(Set.A).build());
    private final Setting<com.gaspoweredcake.client33.utils.misc.Keybind> change = sg.add(new KeybindSetting.Builder().name("switch-set").description("Switch between configured sets while this module is enabled.").action(() -> desired.set(desired.get() == Set.A ? Set.B : Set.A)).build());
    private int timer;
    public ArmorSwitch() { super(Categories.Player, "armor-switch", "Equips a configured armor set one piece at a time. Disable AutoArmor to keep a selected set.", "ArmorSwitch"); }
    @Override public void onActivate() { timer = 0; }
    @EventHandler private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || PotionUse.anyBusy()
            || mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()
            || com.gaspoweredcake.client33.systems.modules.Modules.get().isActive(com.gaspoweredcake.client33.systems.modules.combat.AutoArmor.class)) return;
        if (timer-- > 0) return;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            var equip = stack.get(DataComponents.EQUIPPABLE);
            if (equip == null || !java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET).contains(equip.slot()) || !(desired.get() == Set.A ? first.get() : second.get()).contains(stack.getItem())) continue;
            var worn = mc.player.getItemBySlot(equip.slot());
            if (!worn.isEmpty() && (desired.get() == Set.A ? first.get() : second.get()).contains(worn.getItem())) continue;
            InvUtils.move().from(i).toArmor(equip.slot().getIndex()); timer = 4; return;
        }
    }
    @Override public String getInfoString() { return desired.get().name(); }
    public enum Set { A, B }
}
