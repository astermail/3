package com.gaspoweredcake.client33.systems.hud.elements;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.hud.*;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.utils.render.color.Color;

/** A detachable status monitor with persistent bounds and a drag-resize handle. */
public final class ModuleStatusHud extends HudElement implements ResizableHud {
    public static final HudElementInfo<ModuleStatusHud> INFO = new HudElementInfo<>(Hud.GROUP, "module-status", "Movable, resizable module status monitor. Pin its position in settings.", ModuleStatusHud::new);
    private final SettingGroup general = settings.getDefaultGroup();
    public final Setting<String> moduleName = general.add(new StringSetting.Builder().name("module").defaultValue("esp").build());
    private final Setting<Integer> panelWidth = general.add(new IntSetting.Builder().name("width").defaultValue(240).range(120, 800).sliderRange(120, 800).build());
    private final Setting<Integer> panelHeight = general.add(new IntSetting.Builder().name("height").defaultValue(76).range(48, 400).sliderRange(48, 400).build());
    private final Setting<Double> fontScale = general.add(new DoubleSetting.Builder().name("font-scale").defaultValue(1).range(.75, 2).build());
    private String title = "Module", detail = "";
    private boolean enabled;
    private int ticks;
    private final Color accentStart = new Color(), accentEnd = new Color();
    private static final Color BACKGROUND = new Color(20, 20, 23, 230), TEXT = new Color(235, 235, 240), MUTED = new Color(166, 168, 181), ACCENT = new Color(224, 48, 54);
    public ModuleStatusHud() { super(INFO); setSize(240, 76); }
    @Override public void resizePanel(int width, int height) {
        if (pinned) return;
        panelWidth.set(Math.max(120, Math.min(800, width)));
        panelHeight.set(Math.max(48, Math.min(400, height)));
        setSize(panelWidth.get(), panelHeight.get());
    }
    @Override public void tick(HudRenderer renderer) {
        setSize(panelWidth.get(), panelHeight.get());
        if (++ticks % 4 != 1) return;
        accentStart.set(com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(ticks / 1600.0, 230));
        accentEnd.set(com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(ticks / 1600.0 + .25, 230));
        var module = Modules.get().get(moduleName.get());
        enabled = module != null && module.isActive();
        title = module == null ? "Unknown module" : module.title + (enabled ? " · On" : " · Off");
        String info = module == null ? moduleName.get() : enabled && com.gaspoweredcake.client33.Client33.mc.player != null ? module.getInfoString() : "";
        detail = info == null || info.isBlank() ? (pinned ? "Pinned" : "Drag to move · corner to resize") : info;
    }
    @Override public void render(HudRenderer renderer) {
        double scale = fontScale.get();
        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);
        renderer.quad(x, y, getWidth(), 2, accentStart, accentEnd, accentEnd, accentStart);
        renderer.text(CardLayout.ellipsize(title, getWidth() - 16, text -> renderer.textWidth(text, false, scale)), x + 8, y + 8, TEXT, false, scale);
        if (getHeight() >= renderer.textHeight(false, scale) * 2 + 20)
            renderer.text(CardLayout.ellipsize(detail, getWidth() - 16, text -> renderer.textWidth(text, false, scale)), x + 8, y + 12 + renderer.textHeight(false, scale), MUTED, false, scale);
        if (isInEditor() && !pinned) renderer.quad(x + getWidth() - 10, y + getHeight() - 10, 8, 8, ACCENT);
    }
}
