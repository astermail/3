/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WWindow;
import com.gaspoweredcake.client33.utils.render.color.Color;

public class WClient33Window extends WWindow implements Client33Widget {
    public WClient33Window(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WClient33Header(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double line = theme.scale(1), radius = theme.scale(theme().cornerRadius.get());
        renderer.roundedQuad(x, y, width, height, radius, theme().outline(false, false));
        renderer.roundedQuad(x + line, y + line, width - line * 2, height - line * 2, Math.max(0, radius - line), theme().surface());
    }

    private class WClient33Header extends WHeader {
        public WClient33Header(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double radius = theme.scale(theme().cornerRadius.get());
            renderer.roundedQuad(x + theme.scale(3), y + theme.scale(3), width - theme.scale(6), height - theme.scale(6), radius,
                theme().euphoric() ? new Color(29, 32, 49) : theme().moduleSurface());
        }
    }
}
