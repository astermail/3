package com.gaspoweredcake.client33.gui.widgets.containers;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.gui.widgets.pressable.WWorkspaceTab;
import com.gaspoweredcake.client33.utils.Utils;
import java.util.*;

/** Bounded control generation: only the selected page's visible settings have widgets. */
public final class WSettingsWorkspace extends WVerticalList {
    private final Settings source;
    private final String filter;
    private int groupIndex, page;
    private long visibility;
    public WSettingsWorkspace(Settings source, String filter) {
        this.source = source; this.filter = filter.strip().toLowerCase(Locale.ROOT);
    }
    @Override public void init() { rebuild(); }

    public void refreshIfNeeded() {
        long current = fingerprint();
        if (visibility != current) rebuild();
    }
    private long fingerprint() {
        long hash = 1;
        for (var group : source) for (var setting : group) hash = 31 * hash + (setting.isVisible() ? System.identityHashCode(setting) : 0);
        return hash;
    }
    private void rebuild() {
        clear(); visibility = fingerprint();
        double available = Math.max(240, Math.min(1400, Utils.getWindowWidth() / theme.scale(1) - 100));
        int columns = available >= 1320 ? 3 : available >= 880 ? 2 : 1;
        var groups = source.groups.stream().filter(g -> {
            for (var s : g) if (s.isVisible()) return true;
            return false;
        }).toList();
        if (groups.isEmpty()) { add(theme.label("No settings available.")); return; }
        groupIndex = Math.min(groupIndex, groups.size() - 1);
        var tabs = add(theme.horizontalList()).expandX().widget();
        // Keep the tab strip bounded even for modules with many setting sections.
        int first = groupIndex / 3 * 3;
        if (first > 0) tabs.add(theme.button("<")).widget().action = () -> { groupIndex = first - 1; page = 0; rebuild(); };
        for (int i = first; i < Math.min(first + 3, groups.size()); i++) {
            int index = i;
            tabs.add(new WWorkspaceTab(groups.get(i).name, i == groupIndex, () -> { groupIndex = index; page = 0; rebuild(); })).expandX();
        }
        if (first + 3 < groups.size()) tabs.add(theme.button(">")).widget().action = () -> { groupIndex = first + 3; page = 0; rebuild(); };
        var selected = groups.get(groupIndex);
        List<Setting<?>> visible = new ArrayList<>();
        for (var group : groups) {
            if (filter.isEmpty() && group != selected) continue;
            for (var s : group) if (s.isVisible() && (filter.isEmpty() || (s.title + " " + s.name + " " + s.description + " " + group.name).toLowerCase(Locale.ROOT).contains(filter))) visible.add(s);
        }
        int rows = Math.max(3, Math.min(8, (int) ((Utils.getWindowHeight() / theme.scale(1) - 280) / 40)));
        // Keep small groups readable instead of squeezing short text fields beside wide sliders.
        columns = Math.min(columns, Math.max(1, (visible.size() + rows - 1) / rows));
        int perPage = columns * rows;
        int pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        var bar = add(theme.horizontalList()).expandX().widget();
        bar.add(theme.label(visible.size() + " controls · " + (page + 1) + " / " + pages)).expandCellX();
        bar.add(theme.button("Previous")).widget().action = () -> { page = Math.max(0, page - 1); rebuild(); };
        bar.add(theme.button("Next")).widget().action = () -> { page = Math.min(pages - 1, page + 1); rebuild(); };
        if (visible.isEmpty()) { add(theme.label("No matching controls.")); return; }
        var grid = add(theme.table()).expandX().widget(); grid.horizontalSpacing = 12;
        int end = Math.min(visible.size(), (page + 1) * perPage);
        int perColumn = (end - page * perPage + columns - 1) / columns;
        for (int column = 0; column < columns; column++) {
            int start = page * perPage + column * perColumn;
            if (start >= end) break;
            var subset = new Settings(); var group = subset.createGroup(filter.isEmpty() ? selected.name : "Search results", true);
            for (int i = start; i < Math.min(start + perColumn, end); i++) group.add(visible.get(i));
            grid.add(theme.settings(subset)).top().expandX();
        }
    }
}
