/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.widgets.containers.WContainer;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.systems.hud.Hud;
import com.gaspoweredcake.client33.systems.hud.screens.HudEditorScreen;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import net.minecraft.client.gui.screens.Screen;

import static com.gaspoweredcake.client33.Client33.mc;

public class HudTab extends Tab {
    public HudTab() {
        super("HUD");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new HudScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof HudScreen;
    }

    public static class HudScreen extends WindowTabScreen {
        private WContainer settingsContainer;
        private final Hud hud;

        public HudScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);

            hud = Hud.get();
            hud.settings.onActivated();
        }

        @Override
        public void initWidgets() {
            var style = add(theme.horizontalList()).expandX().widget();
            style.add(theme.button("Euphoric preset")).widget().action = () -> { hud.applyEuphoricStyle(); taskAfterRender = this::reload; };
            style.add(theme.button("Add status monitor")).widget().action = () -> { hud.pinModule("esp"); mc.gui.setScreen(new HudEditorScreen(theme)); };
            add(theme.label("Drag monitors in Edit. Resize using the lower-right corner; right-click to pin.", 560)).expandX();
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(hud.settings, "")).expandX();

            add(theme.horizontalSeparator()).expandX();

            WButton openEditor = add(theme.button("Edit")).expandX().widget();
            openEditor.action = () -> mc.gui.setScreen(new HudEditorScreen(theme));

            WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
            buttons.add(theme.confirmedButton("Clear", "Confirm")).expandX().widget().action = hud::clear;
            buttons.add(theme.confirmedButton("Reset to default elements", "Confirm")).expandX().widget().action = hud::resetToDefaultElements;

            add(theme.horizontalSeparator()).expandX();

            WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();

            bottom.add(theme.label("Active: "));
            WCheckbox active = bottom.add(theme.checkbox(hud.active)).expandCellX().widget();
            active.action = () -> hud.active = active.checked;

            WButton resetSettings = bottom.add(theme.button(GuiRenderer.RESET)).widget();
            resetSettings.action = hud.settings::reset;
            resetSettings.tooltip = "Reset";
        }

        @Override
        public void tick() {
            super.tick();

            for (var cell : settingsContainer.cells) if (cell.widget() instanceof com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace workspace) workspace.refreshIfNeeded();
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(hud);
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(hud);
        }
    }
}
