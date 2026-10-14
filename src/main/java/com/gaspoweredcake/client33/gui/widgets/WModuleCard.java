package com.gaspoweredcake.client33.gui.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.gui.utils.TextWrapping;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import static com.gaspoweredcake.client33.Client33.mc;

/** A single interaction surface: body opens settings, with independent favorite and enable targets. */
public class WModuleCard extends WWidget {
    private final String title, description, category;
    private final double logicalWidth;
    private final BooleanSupplier enabled, favorite;
    private Runnable open;
    private final Runnable toggle, star;
    private CardLayout.Action captured;
    private int button = -1;
    private List<String> lines = List.of();
    public WModuleCard(Module module, double width, Runnable favoriteChanged) {
        this(module.title, module.description, module.category.name, width, module::isActive, () -> module.favorite,
            () -> {}, module::toggle,
            () -> { module.favorite = !module.favorite; favoriteChanged.run(); });
        open = () -> mc.gui.setScreen(theme.moduleScreen(module));
    }
    public WModuleCard(String title, String description, String category, double width,
        BooleanSupplier enabled, BooleanSupplier favorite, Runnable open, Runnable toggle, Runnable star) {
        this.title = title; this.description = description; this.category = category; logicalWidth = width;
        this.enabled = enabled; this.favorite = favorite; this.open = open; this.toggle = toggle; this.star = star;
        tooltip = description + "\nClick card or right-click for settings. Use the switch to enable.";
    }
    @Override protected void onCalculateSize() {
        width = theme.scale(logicalWidth);
        height = theme.scale(94) + theme.textHeight() * 3;
        lines = TextWrapping.wrap(description, Math.max(1, width - theme.scale(32)), theme::textWidth);
    }
    private CardLayout.Action at(MouseButtonEvent click) {
        return CardLayout.action((click.x() - x) / theme.scale(1), (click.y() - y) / theme.scale(1), width / theme.scale(1), height / theme.scale(1));
    }
    @Override public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!isOver(click.x(), click.y()) || click.button() > 1 || click.button() < 0) return false;
        button = click.button(); captured = button == 1 ? CardLayout.Action.Settings : at(click); return true;
    }
    @Override public boolean onMouseReleased(MouseButtonEvent click) {
        if (captured == null || button != click.button()) return false;
        CardLayout.Action action = captured; captured = null; button = -1;
        if (isOver(click.x(), click.y()) && (click.button() == 1 || at(click) == action)) {
            switch (action) { case Settings -> open.run(); case Toggle -> toggle.run(); case Favorite -> star.run(); }
        }
        return true;
    }
    @Override public boolean isFocused() { return captured != null || super.isFocused(); }
    @Override protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        var client = theme instanceof Client33GuiTheme value ? value : null;
        Color accent = client == null ? new Color(115, 224, 230) : client.accent();
        Color border = enabled.getAsBoolean() ? new Color(accent).a(150) : mouseOver ? new Color(110, 117, 153) : new Color(48, 54, 75);
        Color surface = client == null ? new Color(18, 23, 37) : client.surface(captured != null, mouseOver);
        double radius = theme.scale(16), line = theme.scale(1), pad = theme.scale(16), text = theme.textHeight();
        renderer.roundedQuad(x, y + theme.scale(3), width, height, radius, new Color(0, 0, 0, 50));
        renderer.roundedQuad(x, y, width, height, radius, border);
        renderer.roundedQuad(x + line, y + line, width - line * 2, height - line * 2, radius - line, surface);
        renderer.text(category, x + pad, y + theme.scale(12), theme.textSecondaryColor(), false);
        double icon = theme.scale(16);
        renderer.quad(x + width - theme.scale(29), y + theme.scale(12), icon, icon,
            favorite.getAsBoolean() ? GuiRenderer.FAVORITE_YES : GuiRenderer.FAVORITE_NO,
            favorite.getAsBoolean() ? new Color(249, 211, 133) : theme.textSecondaryColor());
        renderer.text(CardLayout.ellipsize(title, width - pad * 2, theme::textWidth), x + pad, y + theme.scale(32), theme.textColor(), false);
        double descriptionY = y + theme.scale(40) + text;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String value = i == 1 && lines.size() > 2 ? CardLayout.ellipsize(lines.get(i) + " ...", width - pad * 2, theme::textWidth) : lines.get(i);
            renderer.text(value, x + pad, descriptionY + i * text, theme.textSecondaryColor(), false);
        }
        double footer = y + height - theme.scale(34);
        renderer.text("Open settings", x + pad, footer + (theme.scale(24) - text) / 2, theme.textSecondaryColor(), false);
        double switchX = x + width - theme.scale(80), switchW = theme.scale(64), switchH = theme.scale(24);
        renderer.roundedQuad(switchX, footer, switchW, switchH, switchH / 2, enabled.getAsBoolean() ? new Color(accent).a(55) : new Color(54, 60, 79));
        renderer.roundedQuad(switchX + theme.scale(7), footer + theme.scale(7), theme.scale(10), theme.scale(10), theme.scale(5), enabled.getAsBoolean() ? accent : theme.textSecondaryColor());
        renderer.text(enabled.getAsBoolean() ? "On" : "Off", switchX + theme.scale(24), footer + (switchH - text) / 2, enabled.getAsBoolean() ? accent : theme.textColor(), false);
    }
}
