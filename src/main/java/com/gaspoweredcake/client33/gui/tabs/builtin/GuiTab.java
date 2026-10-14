/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.GuiThemes;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import net.minecraft.client.gui.screens.Screen;

import static com.gaspoweredcake.client33.Client33.mc;

public class GuiTab extends Tab {
    public GuiTab() {
        super("GUI");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new GuiScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof GuiScreen;
    }

    private static class GuiScreen extends WindowTabScreen {
        public GuiScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);

            theme.settings.onActivated();
        }

        @Override
        public void initWidgets() {
            WHorizontalList opts = add(theme.horizontalList()).expandX().widget();

            opts.add(theme.label("Theme:"));
            WDropdown<String> themeW = opts.add(theme.dropdown(GuiThemes.getNames(), GuiThemes.get().name)).widget();
            themeW.action = () -> {
                GuiThemes.select(themeW.get());

                mc.gui.setScreen(null);
                tab.openScreen(GuiThemes.get());
            };

            WButton resetLayout = opts.add(theme.button("Reset Layout")).expandX().widget();
            resetLayout.action = theme::clearWindowConfigs;

            WButton reset = opts.add(theme.button("Reset Colors")).right().widget();
            reset.action = () -> {
                theme.settings.reset();
                mc.gui.setScreen(null);
                tab.openScreen(GuiThemes.get());
            };

            WButton copyButton = opts.add(theme.button(GuiRenderer.COPY)).widget();
            copyButton.action = this::toClipboard;
            copyButton.tooltip = "Copy config";

            WButton pasteButton = opts.add(theme.button(GuiRenderer.PASTE)).right().widget();
            pasteButton.action = this::fromClipboard;
            pasteButton.tooltip = "Paste config";

            if (theme instanceof com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme client) {
                var style = com.gaspoweredcake.client33.Client33.FOLDER.toPath().resolve("theme.json");
                var status = add(theme.label("Editable theme: " + style, 560)).expandX().widget();
                var files = add(theme.horizontalList()).expandX().widget();
                files.add(theme.button("Export JSON")).widget().action = () -> {
                    try { com.gaspoweredcake.client33.gui.runtime.ThemeStyle.export(style, client); status.set("Exported " + style); }
                    catch (Exception failure) { status.set("Export failed: " + failure.getMessage()); }
                };
                files.add(theme.button("Load JSON")).widget().action = () -> {
                    try {
                        String json;
                        try (var stream = java.nio.file.Files.newInputStream(style)) {
                            byte[] bytes = stream.readNBytes(65537);
                            if (bytes.length > 65536) throw new IllegalArgumentException("Style file exceeds 64 KiB.");
                            json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                        com.gaspoweredcake.client33.gui.runtime.ThemeStyle.parse(json).apply(client);
                        taskAfterRender = this::reload;
                    } catch (Exception failure) { status.set("Style rejected: " + failure.getMessage()); }
                };
            }
            add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(theme.settings, "")).expandX();
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(theme);
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(theme);
        }
    }
}
