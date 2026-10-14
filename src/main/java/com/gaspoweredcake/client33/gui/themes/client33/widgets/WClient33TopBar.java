/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.Tabs;
import com.gaspoweredcake.client33.gui.themes.client33.Client33Widget;
import com.gaspoweredcake.client33.gui.widgets.WTopBar;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.render.color.Color;

import static com.gaspoweredcake.client33.Client33.mc;

public class WClient33TopBar extends WTopBar implements Client33Widget {
    private boolean compact;
    private WDropdown<Page> pages;

    private boolean needsMenu() {
        double total = 0;
        for (Tab tab : Tabs.get()) total += theme.textWidth(tab.name) + pad() * 2;
        return total > Utils.getWindowWidth() - theme.scale(16);
    }

    @Override
    protected void onCalculateSize() {
        if (compact != needsMenu()) {
            clear();
            init();
            for (var cell : cells) cell.widget().calculateSize();
        }
        super.onCalculateSize();
    }

    @Override
    public void init() {
        compact = needsMenu();
        pages = null;
        if (compact) {
            Page[] choices = Tabs.get().stream().map(Page::new).toArray(Page[]::new);
            add(theme.label("Page")).padHorizontal(8);
            pages = add(theme.dropdown(choices, choices[0])).widget();
            pages.action = () -> pages.get().tab().openScreen(theme);
            return;
        }
        for (Tab tab : Tabs.get()) {
            add(new WTopBarButton(tab) {
                @Override
                protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                    super.onRender(renderer, mouseX, mouseY, delta);

                    boolean active = mc.gui.screen() instanceof TabScreen screen && screen.tab == tab;
                    double line = WClient33TopBar.this.theme().scale(active ? 2 : 1);
                    Color color = active ? WClient33TopBar.this.theme().accent() : WClient33TopBar.this.theme().outlineColor.get(false, mouseOver);
                    renderer.quad(x, y + height - line, width, line, color);
                }
            });
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (pages != null && mc.gui.screen() instanceof TabScreen screen) pages.set(new Page(screen.tab));
        renderer.quad(this, theme().surface());
        renderer.quad(x, y + height - theme.scale(1), width, theme.scale(1), theme().outlineColor.get());
    }

    private record Page(Tab tab) {
        @Override public String toString() { return tab.name; }
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return theme().surface(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return theme().textColor();
    }
}
