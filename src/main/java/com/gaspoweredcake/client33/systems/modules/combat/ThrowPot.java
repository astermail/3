package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.player.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;

public class ThrowPot extends Module {
    private final PotionUse use = new PotionUse();
    private boolean started;
    public ThrowPot() { super(Categories.Combat, "throw-pot", "Bind to use one healing potion or golden apple from your hotbar.", "Throwpot"); }
    @Override public void onActivate() { started = false; }
    @Override public void onDeactivate() { use.stop(); }
    @EventHandler private void leave(GameLeftEvent event) { use.stop(); disable(); }
    @EventHandler private void tick(TickEvent.Pre event) {
        use.tick();
        if (started) { if (!use.busy()) disable(); return; }
        if (mc.player == null || mc.gui.screen() != null || mc.player.isUsingItem() || PotionUse.anyBusy()) { disable(); return; }
        var found = InvUtils.findInHotbar(PotionUse::healing);
        if (!found.found()) { warning("No healing item in hotbar or offhand."); disable(); return; }
        var stack = found.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(found.slot());
        if (stack.is(Items.SPLASH_POTION) && !mc.player.onGround()) { warning("Land before throwing a healing potion at your feet."); disable(); return; }
        started = use.start(found, mc.player.getYRot(), stack.is(Items.SPLASH_POTION) ? 90 : mc.player.getXRot());
        if (!started) disable();
    }
}
