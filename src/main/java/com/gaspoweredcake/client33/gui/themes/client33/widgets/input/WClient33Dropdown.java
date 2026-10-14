/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets.input;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.utils.render.color.Color;

public class WClient33Dropdown<T> extends WDropdown<T> implements Client33Widget {
    public WClient33Dropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new WRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new WValue();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Client33GuiTheme theme = theme();
        double pad = pad();
        double s = theme.textHeight();

        renderBackground(renderer, this, pressed, mouseOver);

        String text = get().toString();
        double w = theme.textWidth(text);
        renderer.text(text, x + pad + maxValueWidth / 2 - w / 2, y + pad, theme.textColor(), false);

        renderer.rotatedQuad(x + pad + maxValueWidth + pad, y + pad, s, s, 0, GuiRenderer.TRIANGLE, theme.textColor());
    }

    private static class WRoot extends WDropdownRoot implements Client33Widget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Client33GuiTheme theme = theme();
            double s = theme.scale(2);
            Color c = theme.outlineColor.get();

            renderer.quad(this, theme.surface());
            if (canScroll && hasScrollBar) {
                renderer.quad(handleX(), handleY(), handleWidth(), handleHeight(), theme.scrollbarColor.get(focused, handleMouseOver));
            }
            renderer.quad(x, y + height - s, width, s, c);
            renderer.quad(x, y, s, height - s, c);
            renderer.quad(x + width - s, y, s, height - s, c);
        }
    }

    private class WValue extends WDropdownValue implements Client33Widget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            Client33GuiTheme theme = theme();

            Color color = new Color(theme.surface(pressed, mouseOver, true));
            int preA = color.a;
            color.a += color.a / 2;
            color.validate();

            renderer.quad(this, color);

            color.a = preA;

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), theme.textColor(), false);
        }
    }
}
