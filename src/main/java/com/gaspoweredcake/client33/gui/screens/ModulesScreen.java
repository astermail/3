/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.screens;

import com.mojang.blaze3d.platform.MacosUtil;
import com.gaspoweredcake.client33.events.client33.ActiveModulesChangedEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.Tabs;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.WVisualPreview;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.systems.modules.render.ESP;
import com.gaspoweredcake.client33.systems.modules.render.CombatVisuals;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.WModuleCard;
import com.gaspoweredcake.client33.gui.widgets.pressable.WNavigationItem;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.config.Config;
import com.gaspoweredcake.client33.systems.modules.Category;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Locale;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;
import static com.gaspoweredcake.client33.utils.Utils.getWindowWidth;
import static com.mojang.blaze3d.platform.InputConstants.*;

/** A single workspace for discovering, configuring and switching modules. */
public class ModulesScreen extends TabScreen {
    private WTextBox search;
    private WView results;
    private WVerticalList navigation;
    private WLabel summary;
    private Category category;
    private Filter filter = Filter.All;
    private WDropdown<Filter> libraryFilter;
    private WDropdown<CategoryChoice> categoryFilter;
    private double contentWidth;
    private boolean compact;
    private String query = "";

    public ModulesScreen(GuiTheme theme) {
        super(theme, Tabs.get().getFirst());
    }

    @Override
    public void initWidgets() {
        libraryFilter = null;
        categoryFilter = null;
        compact = CardLayout.compact(getWindowWidth() / theme.scale(1));
        contentWidth = Math.max(180, Math.min(1060, getWindowWidth() / theme.scale(1) - (compact ? 72 : 272)));
        WPanel window = add(new WPanel()).center().widget();
        window.spacing = 0;
        WHorizontalList body = window.add(theme.horizontalList()).pad(18).widget();
        body.spacing = 20;
        double viewHeight = Math.max(theme.scale(60), getWindowHeight() - theme.scale(compact ? 252 : 192));
        navigation = null;
        if (!compact) {
            WView sidebar = body.add(theme.view()).top().widget();
            sidebar.maxHeight = Math.max(theme.scale(80), getWindowHeight() - theme.scale(80));
            navigation = sidebar.add(theme.verticalList()).minWidth(154).expandX().widget();
            navigation.spacing = 2;
            refreshNavigation();
        }
        WAdaptiveContent content = body.add(new WAdaptiveContent()).top().minWidth(contentWidth).widget();
        content.availableHeight = Math.max(theme.textHeight(), getWindowHeight() - theme.scale(72));
        content.spacing = 12;
        WHorizontalList heading = content.add(theme.horizontalList()).expandX().widget();
        var titles = heading.add(theme.verticalList()).expandCellX().widget();
        titles.add(theme.label(compact ? "33 / Discover" : "Discover", true));
        titles.add(theme.label("A space for your play style.").color(theme.textSecondaryColor()));
        heading.add(theme.button("Close")).right().widget().action = this::onClose;

        if (compact) {
            WHorizontalList filters = content.add(theme.horizontalList()).expandX().widget();
            libraryFilter = filters.add(theme.dropdown(Filter.values(), filter)).expandX().widget();
            libraryFilter.action = () -> select(null, libraryFilter.get());
            var choices = new java.util.ArrayList<CategoryChoice>();
            choices.add(new CategoryChoice(null));
            for (Category value : Modules.loopCategories()) choices.add(new CategoryChoice(value));
            categoryFilter = filters.add(theme.dropdown(choices.toArray(CategoryChoice[]::new), new CategoryChoice(category))).expandX().widget();
            categoryFilter.action = () -> select(categoryFilter.get().category(), filter == Filter.Studio ? Filter.All : filter);
        }
        if (compact) content.add(new WPageNavigation(tab)).expandX();
        WHorizontalList toolbar = content.add(theme.horizontalList()).expandX().widget();
        search = toolbar.add(theme.textBox(query, "Search modules or settings...")).minWidth(Math.min(220, contentWidth - 70)).expandX().widget();
        search.action = () -> {
            query = search.get();
            refreshResults();
        };
        toolbar.add(theme.button("Clear")).widget().action = () -> {
            search.set("");
            query = "";
            search.setFocused(true);
            refreshResults();
        };

        summary = content.add(theme.label("")).widget();
        results = content.add(theme.view()).minWidth(contentWidth).expandX().widget();
        results.maxHeight = viewHeight - theme.textHeight() - theme.scale(8);
        results.spacing = 8;
        content.results = results;
        refreshResults();

        if (theme.modulesHelpText()) {
            content.add(theme.label("Click a card for settings. Switch to enable. Ctrl+F to search.", contentWidth)
                .color(theme.textSecondaryColor()));
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshResults();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        reload();
    }

    private void refreshNavigation() {
        if (navigation == null) return;
        navigation.clear();
        navigation.add(theme.label("33", true)).padLeft(12).padTop(4);
        navigation.add(theme.label("MAKE IT YOURS").color(theme.textSecondaryColor())).padLeft(12).padBottom(18);
        for (Filter value : Filter.values()) {
            navigation.add(new WNavigationItem(value.toString(), category == null && filter == value, () -> select(null, value))).expandX();
        }
        navigation.add(theme.label("CATEGORIES").color(theme.textSecondaryColor())).padLeft(12).padTop(18).padBottom(6);
        for (Category value : Modules.loopCategories()) {
            navigation.add(new WNavigationItem(value.name, category == value, () -> select(value, Filter.All))).expandX();
        }
        navigation.add(theme.label("WORKSPACE").color(theme.textSecondaryColor())).padLeft(12).padTop(18).padBottom(6);
        for (var page : Tabs.get()) {
            if (page != tab) navigation.add(new WNavigationItem(page.name, false, () -> page.openScreen(theme))).expandX();
        }
    }

    private void select(Category category, Filter filter) {
        this.category = category;
        this.filter = filter;
        refreshNavigation();
        if (libraryFilter != null) libraryFilter.set(filter);
        if (categoryFilter != null) categoryFilter.set(new CategoryChoice(category));
        refreshResults();
    }

    private void refreshResults() {
        if (results == null) return;
        results.clear();
        results.resetScroll();
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (filter == Filter.Studio && category == null && text.isEmpty()) {
            summary.set("VISUAL STUDIO  /  Build your signature");
            buildStudio();
            return;
        }
        var modules = Modules.get().getAll().stream()
            .filter(module -> !Config.get().hiddenModules.get().contains(module))
            .filter(module -> category == null || module.category == category)
            .filter(module -> filter != Filter.Favorites || module.favorite)
            .filter(module -> filter != Filter.Enabled || module.isActive())
            .filter(module -> matches(module, text))
            .sorted(Comparator.comparingInt((Module module) -> searchRank(module, text))
                .thenComparing(module -> module.title, String.CASE_INSENSITIVE_ORDER))
            .toList();
        summary.set((category == null ? filter.toString() : category.name) + "  /  " + modules.size() + " modules");

        if (modules.isEmpty()) {
            results.add(theme.label("No modules found.")).pad(8);
            results.add(theme.label("Try another category or clear your search.").color(theme.textSecondaryColor()));
            return;
        }

        WTable grid = results.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 12;
        grid.verticalSpacing = 12;
        int columns = CardLayout.columns(contentWidth);
        double cardWidth = (contentWidth - 20 - (columns - 1) * 12) / columns;
        int index = 0;
        for (Module module : modules) {
            grid.add(new WModuleCard(module, cardWidth, () -> {
                if (filter == Filter.Favorites) taskAfterRender = this::refreshResults;
            })).expandX().top();
            if (++index % columns == 0) grid.row();
        }
    }

    private void buildStudio() {
        ESP esp = Modules.get().get(ESP.class);
        CombatVisuals combat = Modules.get().get(CombatVisuals.class);
        WTable grid = results.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 12;
        grid.verticalSpacing = 12;
        boolean wide = contentWidth >= 660;
        WPanel entity = studioCard(grid, esp, "01 / ENTITY SIGNATURE", false);
        WDropdown<ESP.Mode> mode = entity.add(theme.dropdown(esp.mode.get())).expandX().padHorizontal(12).widget();
        mode.action = () -> esp.mode.set(mode.get());
        toggle(entity, "Animated scans", esp.animate);
        toggle(entity, "Ground markers", esp.groundMarker);
        toggle(entity, "Target tether", esp.tether);
        entity.add(theme.button("All ESP settings")).expandX().pad(12).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(esp));
        if (!wide) grid.row();
        WPanel effects = studioCard(grid, combat, "02 / COMBAT CHOREOGRAPHY", true);
        toggle(effects, "Third-person camera", combat.thirdPerson);
        toggle(effects, "Attack bursts", combat.attackBursts);
        toggle(effects, "Movement ribbon", combat.trails);
        toggle(effects, "Target crown", combat.crown);
        effects.add(theme.button("All combat visuals")).expandX().pad(12).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(combat));
        grid.row();
        WPanel looks = grid.add(new WPanel()).expandX().top().widget();
        looks.add(theme.label("03 / LOOK PRESETS")).pad(12);
        for (String name : new String[]{"Euphoric aurora", "Crimson hologram", "Arctic constellation", "Minimal telemetry"}) {
            looks.add(theme.button(name)).expandX().padHorizontal(12).padBottom(6).widget().action = () -> {
                boolean minimal = name.startsWith("Minimal"), arctic = name.startsWith("Arctic"), euphoric = name.startsWith("Euphoric");
                esp.mode.set(minimal ? ESP.Mode.Telemetry : (arctic || euphoric) ? ESP.Mode.Constellation : ESP.Mode.Hologram);
                combat.euphoricAura.set(euphoric); combat.intensity.set(euphoric ? 0.7 : 0.4);
                if (euphoric) {
                    combat.secondary.set(new com.gaspoweredcake.client33.utils.render.color.SettingColor(192, 135, 255, 190));
                    combat.speed.set(0.8);
                    if (theme instanceof com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme client)
                        client.appearance.set(com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme.Appearance.Euphoric);
                }
                esp.animate.set(!minimal);
                combat.targetOrbit.set(!minimal); combat.scanBand.set(!minimal); combat.crown.set(!minimal);
                combat.attackBursts.set(!minimal); combat.sparks.set(!minimal); combat.trails.set(!minimal);
                combat.primary.set(new com.gaspoweredcake.client33.utils.render.color.SettingColor((arctic || euphoric) ? 92 : 255, (arctic || euphoric) ? 220 : 65, (arctic || euphoric) ? 255 : 95, 220));
                esp.signatureAccent.set(new com.gaspoweredcake.client33.utils.render.color.SettingColor(combat.primary.get()));
                taskAfterRender = this::refreshResults;
            };
        }
        looks.add(theme.label("Changes appearance only. Enable above.", 280).color(theme.textSecondaryColor())).pad(12);
        if (!wide) grid.row();
        WPanel scene = grid.add(new WPanel()).expandX().top().widget();
        scene.add(theme.label("04 / SCENE & CAMERA")).pad(12);
        for (String name : new String[]{"ambience", "fullbright", "camera-tweaks", "free-look", "hand-view"}) {
            Module module = Modules.get().get(name);
            if (module != null) scene.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        scene.add(theme.label("Fine-tune your world, camera and hands.", 280).color(theme.textSecondaryColor())).pad(12);
        grid.row();
        WPanel recon = grid.add(new WPanel()).expandX().top().widget();
        recon.add(theme.label("05 / WORLD INTELLIGENCE")).pad(12);
        for (String name : new String[]{"storage-esp", "faction-intel", "movement-heatmap", "structure-esp"}) {
            Module module = Modules.get().get(name);
            recon.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        recon.add(theme.label("Loaded data, source labels and expiring observations.", 280).color(theme.textSecondaryColor())).pad(12);
        if (!wide) grid.row();
        WPanel combatTools = grid.add(new WPanel()).expandX().top().widget();
        combatTools.add(theme.label("06 / PVP CONTROL")).pad(12);
        for (String name : new String[]{"kill-aura", "shield-predict", "crystal-aura", "auto-totem"}) {
            Module module = Modules.get().get(name);
            combatTools.add(theme.button(module.title + "  >")).expandX().padHorizontal(12).padBottom(6).widget().action = () -> mc.gui.setScreen(theme.moduleScreen(module));
        }
        combatTools.add(theme.label("Server timing, shield estimates and explosion risk controls.", 280).color(theme.textSecondaryColor())).pad(12);
    }

    private WPanel studioCard(WTable grid, Module module, String title, boolean combat) {
        WPanel card = grid.add(new WPanel()).expandX().top().widget();
        card.add(theme.label(title)).pad(12);
        card.add(new WVisualPreview(combat)).expandX().padHorizontal(8);
        card.add(theme.module(module)).expandX().pad(8);
        return card;
    }

    private void toggle(WPanel card, String title, Setting<Boolean> setting) {
        WHorizontalList row = card.add(theme.horizontalList()).expandX().padHorizontal(12).padVertical(4).widget();
        row.add(theme.label(title)).expandCellX();
        WCheckbox checkbox = row.add(theme.checkbox(setting.get())).widget();
        checkbox.action = () -> setting.set(checkbox.checked);
        checkbox.tooltip = setting.description;
    }

    private int searchRank(Module module, String query) {
        String title = module.title.toLowerCase(Locale.ROOT);
        if (Modules.normalizeName(module.name).equals(Modules.normalizeName(query))) return 0;
        for (String alias : module.aliases) if (Modules.normalizeName(alias).equals(Modules.normalizeName(query))) return 0;
        if (title.equals(query)) return 0;
        if (title.startsWith(query)) return 1;
        if (title.contains(query)) return 2;
        return 3;
    }

    private boolean matches(Module module, String query) {
        if (query.isEmpty()) return true;
        if (Modules.normalizeName(module.name).contains(Modules.normalizeName(query))) return true;
        for (String alias : module.aliases) if (Modules.normalizeName(alias).contains(Modules.normalizeName(query))) return true;
        if ((module.title + " " + module.name + " " + module.description).toLowerCase(Locale.ROOT).contains(query)) return true;
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                if (setting.title.toLowerCase(Locale.ROOT).contains(query)) return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (filter == Filter.Enabled) taskAfterRender = this::refreshResults;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent value) {
        if (locked) return false;
        int modifier = MacosUtil.IS_MACOS ? MOD_SUPER : MOD_CONTROL;
        if ((value.modifiers() & modifier) != 0 && value.key() == KEY_F) {
            search.setFocused(true);
            search.setCursorMax();
            return true;
        }
        return super.keyPressed(value);
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Modules.get());
    }

    @Override
    public boolean fromClipboard() {
        boolean imported = NbtUtils.fromClipboard(Modules.get());
        refreshResults();
        return imported;
    }

    private record CategoryChoice(Category category) {
        @Override public String toString() { return category == null ? "All categories" : category.name; }
    }

    private enum Filter { All, Favorites, Enabled, Studio }
}
