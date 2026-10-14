package com.gaspoweredcake.client33.gui.screens;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.WView;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.world.MaterialReport;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bounded pages keep large material lists responsive; inventory refresh does not steal search focus. */
public class SchematicMaterialsScreen extends WindowScreen {
    private static final int PAGE_SIZE = 24;
    private final String filename;
    private final Supplier<List<MaterialReport.Entry>> source;
    private final BooleanSupplier valid;
    private final Runnable export;
    private String query = "";
    private boolean shortagesOnly, sessionEnded;
    private MaterialReport.Sort sort = MaterialReport.Sort.Missing;
    private int page, ticks;
    private List<MaterialReport.Entry> snapshot = List.of();
    private WLabel summary;
    private WView rows;
    private double contentWidth;

    public SchematicMaterialsScreen(GuiTheme theme, String filename, Supplier<List<MaterialReport.Entry>> source, BooleanSupplier valid, Runnable export) {
        super(theme, "Schematic Materials");
        this.filename = filename; this.source = source; this.valid = valid; this.export = export;
    }
    @Override public void initWidgets() {
        contentWidth = Math.max(160, Math.min(640, Utils.getWindowWidth() / theme.scale(1) - 70));
        add(theme.label(filename, contentWidth)).expandX();
        add(theme.label("Full source totals. Scaffolding and container contents excluded.", contentWidth)).expandX();
        var search = add(theme.textBox(query, "Find an item or registry ID...")).minWidth(contentWidth).expandX().widget();
        search.action = () -> { query = search.get(); page = 0; refresh(); };
        var controls = add(theme.horizontalList()).expandX().widget();
        var shortages = controls.add(theme.checkbox(shortagesOnly)).widget();
        controls.add(theme.label("Shortages only"));
        shortages.action = () -> { shortagesOnly = shortages.checked; page = 0; refresh(); };
        var order = (contentWidth < 320 ? window : controls).add(theme.dropdown(MaterialReport.Sort.values(), sort)).expandX().widget();
        order.action = () -> { sort = order.get(); page = 0; refresh(); };
        summary = add(theme.label("", contentWidth)).expandX().widget();
        rows = add(theme.view()).minWidth(contentWidth).expandX().widget();
        rows.maxHeight = Math.max(theme.scale(60), Utils.getWindowHeight() - theme.scale(330));
        var buttons = contentWidth < 320 ? add(theme.verticalList()).expandX().widget() : add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("Previous")).widget().action = () -> { if (page > 0) page--; refresh(); };
        buttons.add(theme.button("Next")).widget().action = () -> {
            if (valid.getAsBoolean() && (page + 1L) * PAGE_SIZE < filtered().size()) page++;
            refresh();
        };
        buttons.add(theme.button("Export CSV")).expandX().widget().action = () -> { if (valid.getAsBoolean()) export.run(); };
        sessionEnded = !valid.getAsBoolean();
        snapshot = sessionEnded ? List.of() : List.copyOf(source.get());
        refresh();
    }
    private List<MaterialReport.Entry> filtered() { return MaterialReport.filter(snapshot, query, shortagesOnly, sort); }
    private void refresh() {
        rows.clear();
        if (!valid.getAsBoolean()) {
            summary.set("Schematic session ended. Close this screen and load a schematic again.");
            return;
        }
        var entries = filtered();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        long shortTypes = snapshot.stream().filter(entry -> !entry.manual() && entry.missing() > 0).count();
        summary.set("%d matching types / %d shortages / page %d of %d".formatted(entries.size(), shortTypes, page + 1, pages));
        if (entries.isEmpty()) rows.add(theme.label("No materials match these filters.", contentWidth)).expandX();
        for (var entry : entries.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, entries.size()))) {
            var item = rows.add(theme.verticalList()).expandX().widget();
            var name = item.add(theme.label(entry.name(), true, contentWidth - 20)).expandX().widget();
            name.tooltip = entry.id();
            String details = entry.manual() ? "Manual handling: %,d block positions".formatted(entry.required())
                : "Need %,d (%d stacks) / Have %,d / Missing %,d".formatted(entry.required(), entry.stacks(), entry.inventory(), entry.missing());
            item.add(theme.label(details, contentWidth - 20)).expandX();
            rows.add(theme.horizontalSeparator()).expandX();
        }
    }
    @Override public void tick() {
        super.tick();
        if (++ticks % 10 != 0) return;
        if (!valid.getAsBoolean()) {
            if (!sessionEnded) { sessionEnded = true; snapshot = List.of(); refresh(); }
            return;
        }
        var current = List.copyOf(source.get());
        if (!current.equals(snapshot)) { snapshot = current; refresh(); }
    }
}
