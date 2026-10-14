/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.screens;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.client33.ActiveModulesChangedEvent;
import com.gaspoweredcake.client33.events.client33.ModuleBindChangedEvent;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WidgetScreen;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.utils.Cell;
import com.gaspoweredcake.client33.gui.widgets.WKeybind;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.containers.WContainer;
import com.gaspoweredcake.client33.gui.widgets.containers.WHorizontalList;
import com.gaspoweredcake.client33.gui.widgets.containers.WSection;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WFavorite;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import com.gaspoweredcake.client33.utils.render.prompts.OkPrompt;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

import static com.gaspoweredcake.client33.utils.Utils.getWindowWidth;

public class ModuleScreen extends WindowScreen {
    private final Module module;

    private WContainer settingsContainer;
    private com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace workspace;
    private WKeybind keybind;
    private WCheckbox active;
    private WButton statusButton;
    private String settingsQuery = "";
    private boolean configuring;

    public ModuleScreen(GuiTheme theme, Module module) {
        super(theme, theme.favorite(module.favorite), module.title);
        windowCell.top().marginTop(42);
        ((WFavorite) window.icon).action = () -> module.favorite = ((WFavorite) window.icon).checked;

        this.module = module;
    }

    @Override
    public void initWidgets() {
        settingsContainer = null; workspace = null; keybind = null;
        // Description
        add(theme.label(module.description, Math.min(600, getWindowWidth() / theme.scale(1) * 0.65)));

        if (module.addon != null && module.addon != Client33.ADDON) {
            WHorizontalList addon = add(theme.horizontalList()).expandX().widget();
            addon.add(theme.label("From: ").color(theme.textSecondaryColor())).widget();
            addon.add(theme.label(module.addon.name).color(module.addon.color)).widget();
        }

        WHorizontalList controls = add(theme.horizontalList()).expandX().widget();
        controls.add(theme.label("Enabled"));
        active = controls.add(theme.checkbox(module.isActive())).widget();
        active.action = () -> {
            if (module.isActive() != active.checked) module.toggle();
            taskAfterRender = this::reload;
        };
        if (!module.isActive()) {
            controls.add(theme.button(configuring ? "Hide settings" : "Configure")).widget().action = () -> {
                configuring = !configuring;
                taskAfterRender = this::reload;
            };
        }
        var hud = com.gaspoweredcake.client33.systems.hud.Hud.get();
        statusButton = controls.add(theme.button(hud.hasModulePanel(module.name) ? "Reattach status" : "Detach status")).widget();
        statusButton.action = () -> {
            if (hud.hasModulePanel(module.name)) {
                hud.reattachModule(module.name);
                taskAfterRender = this::reload;
                return;
            }
            hud.pinModule(module.name);
            Client33.mc.gui.setScreen(new com.gaspoweredcake.client33.systems.hud.screens.HudEditorScreen(theme));
        };
        controls.add(theme.button("Back to modules")).expandCellX().right().widget().action = this::onClose;

        if (!module.isActive() && !configuring) {
            add(theme.label("Configure settings before enabling, or assign a keybind below.").color(theme.textSecondaryColor()));
        } else {
            // Custom widget
            WWidget widget = module.getWidget(theme);

            if (widget != null) {
                add(theme.horizontalSeparator()).expandX();
                Cell<WWidget> cell = add(widget);
                if (widget instanceof WContainer) cell.expandX();
            }

            if (!module.settings.groups.isEmpty()) {
                WTextBox search = add(theme.textBox(settingsQuery, "Find a setting...")).expandX().widget();
                settingsContainer = add(theme.verticalList()).expandX().widget();
                workspace = settingsContainer.add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(module.settings, settingsQuery)).expandX().widget();
                search.action = () -> {
                    settingsQuery = search.get();
                    settingsContainer.clear();
                    workspace = settingsContainer.add(new com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace(module.settings, settingsQuery)).expandX().widget();
                };
            }
        }

        // Bind
        WSection section = add(theme.section("Keybind & feedback", false)).expandX().widget();

        // Keybind
        WHorizontalList bind = section.add(theme.horizontalList()).expandX().widget();

        bind.add(theme.label("Bind: "));
        keybind = bind.add(theme.keybind(module.keybind)).expandX().widget();
        keybind.actionOnSet = () -> Modules.get().setModuleToBind(module);

        WButton reset = bind.add(theme.button(GuiRenderer.RESET)).expandCellX().right().widget();
        reset.action = keybind::resetBind;
        reset.tooltip = "Reset";

        // Toggle on bind release
        WHorizontalList tobr = section.add(theme.horizontalList()).widget();

        tobr.add(theme.label("Toggle on bind release: "));
        WCheckbox tobrC = tobr.add(theme.checkbox(module.toggleOnBindRelease)).widget();
        tobrC.action = () -> module.toggleOnBindRelease = tobrC.checked;

        // Chat feedback
        WHorizontalList cf = section.add(theme.horizontalList()).widget();

        cf.add(theme.label("Chat Feedback: "));
        WCheckbox cfC = cf.add(theme.checkbox(module.chatFeedback)).widget();
        cfC.action = () -> module.chatFeedback = cfC.checked;

        add(theme.horizontalSeparator()).expandX();

        // Bottom
        WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();

        // Config sharing
        WHorizontalList sharing = bottom.add(theme.horizontalList()).right().widget();
        WButton copy = sharing.add(theme.button(GuiRenderer.COPY)).widget();
        copy.action = () -> {
            if (toClipboard()) {
                OkPrompt.create()
                    .title("Module copied!")
                    .message("The settings for this module are now in your clipboard.")
                    .message("You can also copy settings using Ctrl+C.")
                    .message("Settings can be imported using Ctrl+V or the paste button.")
                    .id("config-sharing-guide")
                    .show();
            }
        };
        copy.tooltip = "Copy config";

        WButton paste = sharing.add(theme.button(GuiRenderer.PASTE)).widget();
        paste.action = this::fromClipboard;
        paste.tooltip = "Paste config";
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !Modules.get().isBinding();
    }

    @Override
    public void tick() {
        super.tick();

        if (workspace != null) workspace.refreshIfNeeded();
        if (statusButton != null) {
            String label = com.gaspoweredcake.client33.systems.hud.Hud.get().hasModulePanel(module.name) ? "Reattach status" : "Detach status";
            if (!label.equals(statusButton.getText())) statusButton.set(label);
        }
    }

    @EventHandler
    private void onModuleBindChanged(ModuleBindChangedEvent event) {
        if (keybind != null) keybind.reset();
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (this.active.checked != module.isActive()) { this.active.checked = module.isActive(); taskAfterRender = this::reload; }
    }

    @Override
    public boolean toClipboard() {
        CompoundTag tag = new CompoundTag();

        tag.putString("name", module.name);

        CompoundTag settingsTag = module.settings.toTag();
        if (!settingsTag.isEmpty()) tag.put("settings", settingsTag);

        return NbtUtils.toClipboard(tag);
    }

    @Override
    public boolean fromClipboard() {
        CompoundTag tag = NbtUtils.fromClipboard();
        if (tag == null) return false;
        if (!tag.getStringOr("name", "").equals(module.name)) return false;

        Optional<CompoundTag> settings = tag.getCompound("settings");

        if (settings.isPresent()) module.settings.fromTag(settings.get());
        else module.settings.reset();

        if (parent instanceof WidgetScreen p) p.reload();
        reload();

        return true;
    }
}
