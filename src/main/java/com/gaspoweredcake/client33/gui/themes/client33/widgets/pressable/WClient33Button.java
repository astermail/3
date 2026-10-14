/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets.pressable;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.renderer.packer.GuiTexture;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;

public class WClient33Button extends WButton implements Client33Widget {
    public WClient33Button(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Client33GuiTheme theme = theme();
        double pad = pad();

        var surface = new com.gaspoweredcake.client33.utils.render.color.Color(theme.surface(pressed, mouseOver));
        int lift = pressed ? 6 : mouseOver ? 22 : 12;
        surface.set(Math.min(255, surface.r + lift), Math.min(255, surface.g + lift), Math.min(255, surface.b + lift), surface.a);
        renderer.roundedQuad(x, y, width, height, theme.scale(theme.cornerRadius.get()), surface);
        if (mouseOver) renderer.quad(x + theme.scale(6), y + height - theme.scale(1), width - theme.scale(12), theme.scale(1), theme.accent());

        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, y + pad, theme.textColor(), false);
        }
        else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, theme.textColor());
        }
    }
}
