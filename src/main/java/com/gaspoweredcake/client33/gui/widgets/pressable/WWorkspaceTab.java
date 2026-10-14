package com.gaspoweredcake.client33.gui.widgets.pressable;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** Compact horizontal destination, measured once when the layout changes. */
public final class WWorkspaceTab extends WPressable {
    private final String title;
    private final boolean selected;
    public WWorkspaceTab(String title, boolean selected, Runnable action) {
        this.title = title; this.selected = selected; this.action = action;
    }
    @Override protected void onCalculateSize() {
        width = theme.textWidth(title) + theme.scale(20);
        height = theme.textHeight() + theme.scale(12);
    }
    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color accent = theme instanceof Client33GuiTheme client ? client.accent() : theme.textColor();
        if (selected || mouseOver) renderer.quad(x, y, width, height, new Color(accent).a(selected ? 28 : 12));
        if (selected) renderer.quad(x, y + height - theme.scale(2), width, theme.scale(2), accent);
        renderer.text(title, x + (width - theme.textWidth(title)) / 2, y + theme.scale(5), selected ? theme.textColor() : theme.textSecondaryColor(), false);
    }
}
