/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.WTooltip;

public class WClient33Tooltip extends WTooltip implements Client33Widget {
    public WClient33Tooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, theme().surface());
        double line = theme.scale(1);
        renderer.quad(x, y, width, line, theme().accent());
        renderer.quad(x, y, line, height, theme().outlineColor.get());
    }
}
