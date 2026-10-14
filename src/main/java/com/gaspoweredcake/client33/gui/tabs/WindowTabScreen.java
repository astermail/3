/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.gui.tabs;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.utils.Cell;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WPanel;
import com.gaspoweredcake.client33.gui.widgets.containers.WView;
import com.gaspoweredcake.client33.gui.widgets.containers.WPageNavigation;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;

/** Consistent, bounded workspace shell shared by utility pages. */
public abstract class WindowTabScreen extends TabScreen {
    protected final WView window;

    public WindowTabScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
        WPanel panel = super.add(new WPanel(true)).center().widget();
        var header = panel.add(theme.horizontalList()).expandX().pad(12).widget();
        header.add(theme.label("33 / " + tab.name, true)).expandCellX();
        var choices = Tabs.get().stream().map(WPageNavigation.Choice::new).toArray(WPageNavigation.Choice[]::new);
        var picker = header.add(theme.dropdown(choices, new WPageNavigation.Choice(tab))).widget();
        picker.action = () -> { if (picker.get().tab() != tab) picker.get().tab().openScreen(theme); };
        header.add(theme.button("Close")).widget().action = this::onClose;
        panel.add(theme.horizontalSeparator()).expandX();
        window = panel.add(theme.view()).expandX().pad(12).widget();
        fit();
    }

    private void fit() {
        window.maxHeight = Math.max(theme.scale(80), Math.min(theme.scale(520), getWindowHeight() - theme.scale(140)));
    }

    @Override public void resize(int width, int height) {
        super.resize(width, height);
        fit();
        reload();
    }

    @Override public <W extends WWidget> Cell<W> add(W widget) { return window.add(widget); }
    @Override public void clear() { window.clear(); }
}
