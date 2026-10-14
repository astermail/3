package com.gaspoweredcake.client33.gui.widgets.pressable;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** Quiet sidebar destination with a filled capsule for the current selection. */
public class WNavigationItem extends WPressable {
    private final String text;
    private final boolean selected;
    public WNavigationItem(String text, boolean selected, Runnable action) { this.text = text; this.selected = selected; this.action = action; }
    @Override protected void onCalculateSize() { width = theme.scale(154); height = theme.textHeight() + theme.scale(18); }
    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color accent = theme instanceof Client33GuiTheme client ? client.accent() : new Color(115, 224, 230);
        if (selected || mouseOver) renderer.roundedQuad(x, y, width, height, theme.scale(10), selected ? new Color(accent).a(35) : new Color(77, 87, 118, 40));
        if (selected) renderer.roundedQuad(x + theme.scale(10), y + height / 2 - theme.scale(2), theme.scale(4), theme.scale(4), theme.scale(2), accent);
        renderer.text(CardLayout.ellipsize(text, width - theme.scale(36), theme::textWidth), x + theme.scale(22), y + (height - theme.textHeight()) / 2,
            selected ? theme.textColor() : theme.textSecondaryColor(), false);
    }
}
