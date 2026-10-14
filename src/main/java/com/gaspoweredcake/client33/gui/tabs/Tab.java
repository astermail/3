/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.tabs;

import com.gaspoweredcake.client33.gui.GuiTheme;
import net.minecraft.client.gui.screens.Screen;

import static com.gaspoweredcake.client33.Client33.mc;

public abstract class Tab {
    public final String name;

    public Tab(String name) {
        this.name = name;
    }

    public void openScreen(GuiTheme theme) {
        TabScreen screen = this.createScreen(theme);
        if (!(screen instanceof com.gaspoweredcake.client33.gui.screens.ModulesScreen) && !(screen instanceof WindowTabScreen))
            screen.addDirect(new com.gaspoweredcake.client33.gui.widgets.containers.WPageNavigation(this)).bottom().pad(12);
        mc.gui.setScreen(screen);
    }

    public abstract TabScreen createScreen(GuiTheme theme);

    public abstract boolean isScreen(Screen screen);
}
