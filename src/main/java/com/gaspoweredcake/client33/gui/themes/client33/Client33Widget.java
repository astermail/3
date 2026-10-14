/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.utils.BaseWidget;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.utils.render.color.Color;

public interface Client33Widget extends BaseWidget {
    default Client33GuiTheme theme() {
        return (Client33GuiTheme) getTheme();
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        Client33GuiTheme theme = theme();
        double line = theme.scale(1), radius = theme.scale(theme.cornerRadius.get());
        renderer.roundedQuad(widget.x, widget.y, widget.width, widget.height, radius, outlineColor);
        renderer.roundedQuad(widget.x + line, widget.y + line, widget.width - line * 2, widget.height - line * 2, Math.max(0, radius - line), backgroundColor);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        Client33GuiTheme theme = theme();
        renderBackground(renderer, widget, theme.outline(pressed, mouseOver), theme.surface(pressed, mouseOver));
    }
}
