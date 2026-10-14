/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.pathing.PathManagers;
import net.minecraft.client.gui.screens.Screen;

public class PathManagerTab extends Tab {
    public PathManagerTab() {
        super(PathManagers.get().getName());
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new PathManagerScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof PathManagerScreen;
    }

    private static class PathManagerScreen extends WindowTabScreen {
        public PathManagerScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);

            PathManagers.get().getSettings().get().onActivated();
        }

        private String query = "";
        private boolean advanced;
        private com.gaspoweredcake.client33.gui.widgets.containers.WContainer content;

        @Override
        public void initWidgets() {
            double width = Math.max(220, Math.min(560, com.gaspoweredcake.client33.utils.Utils.getWindowWidth() / theme.scale(1) - 80));
            add(theme.label("Navigation & building", true));
            add(theme.label("Tune movement here. Import and build files in Schematics.", width).color(theme.textSecondaryColor()));
            var actions = add(theme.horizontalList()).expandX().widget();
            actions.add(theme.button("Open schematics...")).expandX().widget().action = () -> {
                for (var destination : com.gaspoweredcake.client33.gui.tabs.Tabs.get())
                    if (destination instanceof SchematicsTab) { destination.openScreen(theme); return; }
            };
            var filter = add(theme.textBox(query, "Search all Baritone settings...")).minWidth(width).expandX().widget();
            filter.action = () -> { query = filter.get(); refresh(); };
            var options = add(theme.horizontalList()).expandX().widget();
            var all = options.add(theme.checkbox(advanced)).widget();
            options.add(theme.label("Show advanced settings"));
            all.action = () -> { advanced = all.checked; refresh(); };
            content = add(theme.verticalList()).expandX().widget();
            refresh();
        }

        private void refresh() {
            content.clear();
            var source = PathManagers.get().getSettings().get();
            if (advanced || !query.isBlank()) {
                content.add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(source, query.strip())).expandX();
                return;
            }
            var curated = new com.gaspoweredcake.client33.settings.Settings();
            addGroup(curated, "Movement", "allowSprint", "allowParkour", "allowParkourPlace", "allowWaterBucketFall", "maxFallHeightNoWater", "allowVines");
            addGroup(curated, "Blocks & building", "allowBreak", "allowPlace", "allowInventory", "buildInLayers", "layerOrder", "buildRepeatCount");
            addGroup(curated, "Path display", "renderPath", "renderGoal", "renderSelectionBoxes");
            content.add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(curated, "")).expandX();
        }

        private void addGroup(com.gaspoweredcake.client33.settings.Settings target, String title, String... names) {
            var group = target.createGroup(title, true);
            for (String name : names) {
                var setting = PathManagers.get().getSettings().get().get(name);
                if (setting != null) group.add(setting);
            }
        }

        @Override
        protected void onClosed() {
            PathManagers.get().getSettings().save();
        }
    }
}
