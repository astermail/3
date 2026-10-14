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
import com.gaspoweredcake.client33.gui.utils.FeatureSearch;
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
    private WHorizontalList navigation;
    private int page, pageCount = 1;
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
        compact = getWindowWidth() / theme.scale(1) < 700;
        double workspaceWidth = Math.min(1080, getWindowWidth() / theme.scale(1) - 40);
        contentWidth = Math.max(180, workspaceWidth - 24);
        WPanel window = add(new WPanel(true)).center().widget();
        window.spacing = 0;
        WHorizontalList body = window.add(theme.horizontalList()).pad(12).widget();
        body.spacing = 12;
        double viewHeight = Math.min(theme.scale(540), getWindowHeight() - theme.scale(100));
        WAdaptiveContent content = body.add(new WAdaptiveContent()).top().minWidth(contentWidth).widget();
        content.availableHeight = viewHeight;
        content.spacing = 8;
        WHorizontalList heading = content.add(theme.horizontalList()).expandX().widget();
        heading.add(theme.label("33 / Modules", true)).expandCellX();
        var schematics = Tabs.get(com.gaspoweredcake.client33.gui.tabs.builtin.SchematicsTab.class);
        if (schematics != null) heading.add(theme.button("Schematics")).widget().action = () -> schematics.openScreen(theme);
        var pages = Tabs.get().stream().map(WPageNavigation.Choice::new).toArray(WPageNavigation.Choice[]::new);
        var destinationPicker = heading.add(theme.dropdown(pages, new WPageNavigation.Choice(tab))).widget();
        destinationPicker.action = () -> { if (destinationPicker.get().tab() != tab) destinationPicker.get().tab().openScreen(theme); };
        heading.add(theme.button("Close")).widget().action = this::onClose;

        navigation = content.add(theme.horizontalList()).expandX().widget();
        navigation.spacing = 2;
        refreshNavigation();
        WHorizontalList filters = content.add(theme.horizontalList()).expandX().widget();
        libraryFilter = filters.add(theme.dropdown(Filter.values(), filter)).widget();
        libraryFilter.action = () -> select(category, libraryFilter.get());
        if (compact) {
            var choices = new java.util.ArrayList<CategoryChoice>(); choices.add(new CategoryChoice(null));
            for (Category value : Modules.loopCategories()) choices.add(new CategoryChoice(value));
            categoryFilter = filters.add(theme.dropdown(choices.toArray(CategoryChoice[]::new), new CategoryChoice(category))).expandX().widget();
            categoryFilter.action = () -> select(categoryFilter.get().category(), filter);
        }
        filters.add(theme.button("Previous")).right().expandCellX().widget().action = () -> { page = Math.max(0, page - 1); refreshResults(); };
        filters.add(theme.button("Next")).widget().action = () -> { page = Math.min(pageCount - 1, page + 1); refreshResults(); };

        WHorizontalList toolbar = content.add(theme.horizontalList()).expandX().widget();
        search = toolbar.add(theme.textBox(query, "Search all features or settings...")).minWidth(Math.min(220, contentWidth - 70)).expandX().widget();
        search.action = () -> {
            query = search.get(); page = 0;
            results.resetScroll();
            refreshResults();
        };
        toolbar.add(theme.button("Clear")).widget().action = () -> {
            search.set("");
            query = ""; page = 0;
            search.setFocused(true);
            results.resetScroll();
            refreshResults();
        };

        summary = content.add(theme.label("")).widget();
        results = content.add(theme.view()).minWidth(contentWidth).expandX().widget();
        results.maxHeight = viewHeight - theme.textHeight() - theme.scale(8);
        results.spacing = 8;
        content.results = results;
        refreshResults();

        if (theme.modulesHelpText()) {
            content.add(theme.label("Click a row for settings  /  Ctrl+F to search", contentWidth)
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
        if (compact) return;
        navigation.add(new com.gaspoweredcake.client33.gui.widgets.pressable.WWorkspaceTab("All", category == null, () -> select(null, filter))).expandX();
        for (Category value : Modules.loopCategories()) {
            navigation.add(new com.gaspoweredcake.client33.gui.widgets.pressable.WWorkspaceTab(value.name, category == value, () -> select(value, filter))).expandX();
        }
    }

    private void select(Category category, Filter filter) {
        page = 0;
        this.category = category;
        this.filter = filter;
        results.resetScroll();
        refreshNavigation();
        if (libraryFilter != null) libraryFilter.set(filter);
        if (categoryFilter != null) categoryFilter.set(new CategoryChoice(category));
        refreshResults();
    }

    private void refreshResults() {
        if (results == null) return;
        results.clear();
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (filter == Filter.Studio && category == null && text.isEmpty()) {
            summary.set("VISUALS  /  Preview and configure");
            buildStudio();
            return;
        }
        var modules = Modules.get().getAll().stream()
            .filter(module -> !text.isEmpty() || !Config.get().hiddenModules.get().contains(module))
            .filter(module -> !text.isEmpty() || category == null || module.category == category)
            .filter(module -> !text.isEmpty() || filter != Filter.Favorites || module.favorite)
            .filter(module -> !text.isEmpty() || filter != Filter.Enabled || module.isActive())
            .filter(module -> matches(module, text))
            .sorted(Comparator.comparingInt((Module module) -> searchRank(module, text))
                .thenComparing(module -> module.title, String.CASE_INSENSITIVE_ORDER))
            .toList();
        summary.set((!text.isEmpty() ? "Search all features" : category == null ? filter.toString() : category.name) + "  /  " + modules.size() + " modules");

        if (modules.isEmpty()) {
            results.add(theme.label("No modules found.")).pad(8);
            results.add(theme.label("Try a module name, alias or setting name.").color(theme.textSecondaryColor()));
            return;
        }

        results.spacing = 3;
        int columns = contentWidth >= 980 ? 3 : contentWidth >= 560 ? 2 : 1;
        int rows = Math.max(2, (int) ((Math.min(theme.scale(540), getWindowHeight() - theme.scale(100)) - theme.scale(180)) / (theme.textHeight() + theme.scale(19))));
        int perPage = rows * columns;
        pageCount = Math.max(1, (modules.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);
        summary.set((!text.isEmpty() ? "Search all features" : category == null ? filter.toString() : category.name) + " / " + modules.size() + " modules  ·  " + (page + 1) + " / " + pageCount);
        var grid = results.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 10; grid.verticalSpacing = 3;
        double rowWidth = (contentWidth - 12 - (columns - 1) * 10) / columns;
        int index = 0;
        for (Module module : modules.subList(page * perPage, Math.min(modules.size(), (page + 1) * perPage))) {
            grid.add(new WModuleCard(module, rowWidth, () -> {
                if (filter == Filter.Favorites) taskAfterRender = this::refreshResults;
            })).expandX();
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
        return FeatureSearch.rank(query, module.name, module.title, module.aliases);
    }

    private boolean matches(Module module, String query) {
        if (query.isEmpty()) return true;
        var fields = new java.util.ArrayList<String>();
        fields.add(module.name); fields.add(module.title); fields.add(module.description);
        java.util.Collections.addAll(fields, module.aliases);
        for (SettingGroup group : module.settings) {
            fields.add(group.name);
            for (Setting<?> setting : group) {
                fields.add(setting.name); fields.add(setting.title); fields.add(setting.description);
            }
        }
        return FeatureSearch.matches(query, fields.toArray(String[]::new));
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
