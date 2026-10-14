package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.*;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;

public class AutoHeal extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> health = sg.add(new DoubleSetting.Builder().name("health").description("Use healing items at or below this health (two points per heart).").defaultValue(10).range(1, 20).build());
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ms").description("Minimum time between uses; allows server health updates to arrive.").defaultValue(1000).range(250, 5000).build());
    private final PotionUse use = new PotionUse();
    private long next;
    public AutoHeal() { super(Categories.Combat, "auto-heal", "Uses healing potions or golden apples from your hotbar when health is low.", "AutoHeal"); }
    @Override public void onActivate() { next = 0; }
    @Override public void onDeactivate() { use.stop(); }
    @EventHandler private void leave(GameLeftEvent event) { use.stop(); next = 0; }
    @EventHandler private void tick(TickEvent.Pre event) {
        use.tick();
        if (mc.player == null || mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.get()
            || CombatTelemetry.now() < next || use.busy() || mc.player.isUsingItem() || mc.gui.screen() != null) return;
        var found = InvUtils.findInHotbar(PotionUse::healing);
        if (!found.found()) return;
        var stack = found.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(found.slot());
        if (stack.is(Items.SPLASH_POTION) && !mc.player.onGround()) return;
        if (use.start(found, mc.player.getYRot(), stack.is(Items.SPLASH_POTION) ? 90 : mc.player.getXRot())) next = CombatTelemetry.now() + delay.get();
    }
    @Override public String getInfoString() { return use.busy() ? "Healing" : null; }
}
