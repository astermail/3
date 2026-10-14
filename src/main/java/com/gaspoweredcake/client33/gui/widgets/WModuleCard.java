package com.gaspoweredcake.client33.gui.widgets;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.input.MouseButtonEvent;
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
        tooltip = description + "\nClick row or right-click for settings. Use the switch to enable.";
    }
    @Override protected void onCalculateSize() {
        width = theme.scale(logicalWidth);
        height = theme.scale(16) + theme.textHeight();
    }
    private CardLayout.Action at(MouseButtonEvent click) {
        double localX = click.x() - x;
        if (localX >= width - theme.scale(66)) return CardLayout.Action.Toggle;
        if (localX >= width - theme.scale(100)) return CardLayout.Action.Favorite;
        return CardLayout.Action.Settings;
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
        Color surface = client == null ? new Color(18, 23, 37) : client.surface(captured != null, mouseOver);
        double pad = theme.scale(12), text = theme.textHeight();
        renderer.roundedQuad(x, y, width, height, theme.scale(3), surface);
        if (enabled.getAsBoolean()) renderer.quad(x, y + theme.scale(8), theme.scale(2), height - theme.scale(16), accent);
        boolean showCategory = width >= theme.scale(430);
        double available = Math.max(1, width - theme.scale(showCategory ? 230 : 124));
        renderer.text(CardLayout.ellipsize(title, available, theme::textWidth), x + pad, y + (height - text) / 2, theme.textColor(), false);
        if (showCategory) renderer.text(CardLayout.ellipsize(category, theme.scale(92), theme::textWidth), x + width - theme.scale(210), y + (height - text) / 2, theme.textSecondaryColor(), false);
        double icon = theme.scale(14);
        renderer.quad(x + width - theme.scale(92), y + (height - icon) / 2, icon, icon,
            favorite.getAsBoolean() ? GuiRenderer.FAVORITE_YES : GuiRenderer.FAVORITE_NO,
            favorite.getAsBoolean() ? new Color(249, 211, 133) : theme.textSecondaryColor());
        double switchX = x + width - theme.scale(60), switchH = theme.scale(22), switchY = y + (height - switchH) / 2;
        renderer.roundedQuad(switchX, switchY, theme.scale(50), switchH, theme.scale(4), enabled.getAsBoolean() ? new Color(accent).a(45) : new Color(54, 54, 59));
        renderer.text(enabled.getAsBoolean() ? "On" : "Off", switchX + theme.scale(10), y + (height - text) / 2, enabled.getAsBoolean() ? accent : theme.textSecondaryColor(), false);
    }
}
