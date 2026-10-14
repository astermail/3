package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.screens.SchematicWorkspaceScreen;
import net.minecraft.client.gui.screens.Screen;

public class SchematicsTab extends Tab {
    public SchematicsTab() { super("Schematics"); }
    @Override public TabScreen createScreen(GuiTheme theme) { return new SchematicWorkspaceScreen(theme, this); }
    @Override public boolean isScreen(Screen screen) { return screen instanceof SchematicWorkspaceScreen; }
}
