/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.EntityHitResult;
import com.gaspoweredcake.client33.utils.player.ClickCadence;
import com.gaspoweredcake.client33.utils.misc.input.Input;

public class AutoClicker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> inScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("while-in-screens")
        .description("Whether to click while a screen is open.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> holdToClick = sgGeneral.add(new BoolSetting.Builder()
        .name("hold-to-click").description("Only generate clicks while the corresponding physical button is held.").defaultValue(true).build());

    private final Setting<Mode> leftClickMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode-left")
        .description("The method of clicking for left clicks.")
        .defaultValue(Mode.Press)
        .build()
    );

    private final Setting<Integer> leftClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-left")
        .description("The amount of delay between left clicks in ticks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(60)
        .visible(() -> leftClickMode.get() == Mode.Press)
        .build()
    );

    private final Setting<Boolean> combatTiming = sgGeneral.add(new BoolSetting.Builder()
        .name("combat-timing")
        .description("Wait for full weapon cooldown and an entity under the crosshair. Pause while using items.")
        .defaultValue(true)
        .visible(() -> leftClickMode.get() == Mode.Press)
        .build()
    );

    private final Setting<Mode> rightClickMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode-right")
        .description("The method of clicking for right clicks.")
        .defaultValue(Mode.Disabled)
        .build()
    );

    private final Setting<Integer> rightClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-right")
        .description("The amount of delay between right clicks in ticks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(60)
        .visible(() -> rightClickMode.get() == Mode.Press)
        .build()
    );

    private final ClickCadence leftCadence = new ClickCadence();
    private final ClickCadence rightCadence = new ClickCadence();
    private boolean holdingAttack, holdingUse;

    public AutoClicker() {
        super(Categories.Player, "auto-clicker", "Automatically clicks, with weapon cooldown timing for combat.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || (!inScreens.get() && mc.gui.screen() != null)) {
            reset();
            return;
        }

        boolean left = !holdToClick.get() || Input.isPressed(mc.options.keyAttack);
        boolean right = !holdToClick.get() || Input.isPressed(mc.options.keyUse);
        updateHolds(left && leftClickMode.get() == Mode.Hold, right && rightClickMode.get() == Mode.Hold);

        boolean combatReady = !combatTiming.get() || (!mc.player.isUsingItem()
            && mc.hitResult instanceof EntityHitResult hit && hit.getEntity().isAlive()
            && mc.player.getAttackStrengthScale(0) >= 1);
        if (leftCadence.tick(left && leftClickMode.get() == Mode.Press, combatReady, leftClickDelay.get())) {
            // Utils.leftClick clears the attack mapping; preserve physical input (including block mining).
            boolean down = mc.options.keyAttack.isDown();
            try {
                Utils.leftClick();
            } finally {
                mc.options.keyAttack.setDown(down);
            }
        }
        if (rightCadence.tick(right && rightClickMode.get() == Mode.Press, !mc.player.isUsingItem(), rightClickDelay.get())) {
            Utils.rightClick();
        }
    }

    private void updateHolds(boolean attack, boolean use) {
        if (attack || holdingAttack) mc.options.keyAttack.setDown(attack || (mc.gui.screen() == null && Input.isPressed(mc.options.keyAttack)));
        if (use || holdingUse) mc.options.keyUse.setDown(use || (mc.gui.screen() == null && Input.isPressed(mc.options.keyUse)));
        holdingAttack = attack;
        holdingUse = use;
    }

    private void reset() {
        updateHolds(false, false);
        leftCadence.reset();
        rightCadence.reset();
    }

    public enum Mode {
        Disabled,
        Hold,
        Press
    }
}
