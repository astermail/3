package com.gaspoweredcake.client33.gui.widgets.pressable;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BooleanSupplier;

/** An action whose current availability is both visible and checked again on release. */
public class WActionButton extends WButton {
    private final BooleanSupplier available;
    private final boolean primary;

    public WActionButton(String text, BooleanSupplier available, Runnable action) {
        this(text, available, action, false);
    }

    public WActionButton(String text, BooleanSupplier available, Runnable action, boolean primary) {
        super(text, null);
        this.available = available;
        this.primary = primary;
        this.action = () -> { if (available.getAsBoolean()) action.run(); };
    }

    @Override public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        return available.getAsBoolean() && super.onMouseClicked(click, doubled);
    }

    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean enabled = available.getAsBoolean();
        var client = theme instanceof Client33GuiTheme value ? value : null;
        Color accent = client == null ? theme.textColor() : client.accent();
        Color surface = client == null ? new Color(40, 44, 58) : new Color(client.surface(pressed && enabled, mouseOver && enabled));
        if (primary && enabled) surface = new Color(accent).a(45);
        else if (enabled && mouseOver) surface = new Color(accent).a(22);
        renderer.roundedQuad(x, y, width, height, theme.scale(client == null ? 3 : client.cornerRadius.get()), surface);
        if (enabled && (primary || mouseOver)) renderer.quad(x + theme.scale(6), y + height - theme.scale(1), width - theme.scale(12), theme.scale(1), accent);
        renderer.text(text, x + (width - textWidth) / 2, y + pad(),
            enabled ? theme.textColor() : new Color(theme.textSecondaryColor()).a(95), false);
    }
}
