package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.entity.player.AttackEntityEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;

public class WTap extends Module {
    private boolean released;
    private int releaseTick;
    public WTap() { super(Categories.Combat, "w-tap", "Briefly releases forward and sprint after an attack while you hold forward.", "WTap"); }
    @EventHandler(priority = -1000) private void attack(AttackEntityEvent event) {
        if (event.isCancelled() || mc.player == null || mc.gui.screen() != null || !(event.entity instanceof LivingEntity)
            || !Input.isPressed(mc.options.keyUp)) return;
        mc.options.keyUp.setDown(false);
        mc.player.setSprinting(false);
        released = true; releaseTick = mc.player.tickCount + 1;
    }
    @EventHandler private void tick(TickEvent.Post event) { if (mc.player == null || mc.player.tickCount > releaseTick) restore(); }
    private void restore() {
        if (released) { mc.options.keyUp.setDown(mc.gui.screen() == null && Input.isPressed(mc.options.keyUp)); released = false; }
    }
    @Override public void onDeactivate() { restore(); }
    @EventHandler private void leave(GameLeftEvent event) { restore(); }
}
