package com.gaspoweredcake.client33.gui.widgets.containers;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** Rounded surface for related controls. Child cells supply their padding. */
public class WPanel extends WVerticalList {
    private final boolean accentHeader;

    public WPanel() { this(false); }
    public WPanel(boolean accentHeader) { this.accentHeader = accentHeader; }

    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        var client = theme instanceof Client33GuiTheme value ? value : null;
        double radius = theme.scale(client == null ? 4 : client.cornerRadius.get()), line = theme.scale(1);
        Color border = client == null ? new Color(50, 53, 68) : client.outline(false, false);
        Color surface = client == null ? new Color(15, 19, 29, 250) : client.surface();
        renderer.roundedQuad(x, y, width, height, radius, border);
        renderer.roundedQuad(x + line, y + line, width - line * 2, height - line * 2, Math.max(0, radius - line), surface);
        if (accentHeader) {
            Color start = client == null ? theme.textColor() : client.accent();
            Color end = client != null && client.accentGradient.get() ? client.gradientEnd.get() : start;
            renderer.quad(x, y, width, theme.scale(3), start, end, end, start);
        }
    }
}
