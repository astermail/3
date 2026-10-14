package com.gaspoweredcake.client33.gui.widgets.containers;

import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.Tabs;

/** A compact workspace picker replaces the global top tab strip on utility pages. */
public class WPageNavigation extends WPanel {
    private final Tab current;
    public WPageNavigation(Tab current) { this.current = current; }
    @Override public void init() {
        var row = add(theme.horizontalList()).pad(10).widget();
        row.add(theme.label("33")).padRight(8);
        var choices = Tabs.get().stream().map(Choice::new).toArray(Choice[]::new);
        var picker = row.add(theme.dropdown(choices, new Choice(current))).minWidth(135).widget();
        picker.tooltip = "Open a workspace page";
        picker.action = () -> { if (picker.get().tab != current) picker.get().tab.openScreen(theme); };
    }
    public record Choice(Tab tab) { @Override public String toString() { return tab.name; } }
}
