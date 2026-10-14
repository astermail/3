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

    private int rightClickTimer, leftClickTimer;

    public AutoClicker() {
        super(Categories.Player, "auto-clicker", "Automatically clicks.");
    }

    @Override
    public void onActivate() {
        rightClickTimer = 0;
        leftClickTimer = 0;
        releaseHolds();
    }

    @Override
    public void onDeactivate() {
        releaseHolds();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!inScreens.get() && mc.gui.screen() != null) {
            releaseHolds(); leftClickTimer = rightClickTimer = 0; return;
        }

        if (!holdToClick.get() || Input.isPressed(mc.options.keyAttack)) switch (leftClickMode.get()) {
            case Disabled -> {
            }
            case Hold -> mc.options.keyAttack.setDown(true);
            case Press -> {
                leftClickTimer++;
                if (leftClickTimer > leftClickDelay.get()) {
                    Utils.leftClick();
                    leftClickTimer = 0;
                }
            }
        }

        else { if (leftClickMode.get() == Mode.Hold) mc.options.keyAttack.setDown(mc.gui.screen() == null && Input.isPressed(mc.options.keyAttack)); leftClickTimer = 0; }
        if (!holdToClick.get() || Input.isPressed(mc.options.keyUse)) switch (rightClickMode.get()) {
            case Disabled -> {
            }
            case Hold -> mc.options.keyUse.setDown(true);
            case Press -> {
                rightClickTimer++;
                if (rightClickTimer > rightClickDelay.get()) {
                    Utils.rightClick();
                    rightClickTimer = 0;
                }
            }
        }
        else { if (rightClickMode.get() == Mode.Hold) mc.options.keyUse.setDown(mc.gui.screen() == null && Input.isPressed(mc.options.keyUse)); rightClickTimer = 0; }
    }

    private void releaseHolds() {
        if (leftClickMode.get() == Mode.Hold) mc.options.keyAttack.setDown(mc.gui.screen() == null && Input.isPressed(mc.options.keyAttack));
        if (rightClickMode.get() == Mode.Hold) mc.options.keyUse.setDown(mc.gui.screen() == null && Input.isPressed(mc.options.keyUse));
    }

    public enum Mode {
        Disabled,
        Hold,
        Press
    }
}
