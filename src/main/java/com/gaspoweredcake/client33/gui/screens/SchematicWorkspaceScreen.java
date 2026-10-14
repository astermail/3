package com.gaspoweredcake.client33.gui.screens;

import com.gaspoweredcake.client33.commands.Commands;
import com.gaspoweredcake.client33.commands.commands.SchematicCommand;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.world.MaterialReport;
import com.gaspoweredcake.client33.utils.world.SchematicFiles;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import net.minecraft.core.BlockPos;
import java.nio.file.Path;
import java.util.List;
import static com.gaspoweredcake.client33.Client33.mc;

/** Guided file, origin and build workflow. All actions use the same controller as chat commands. */
public class SchematicWorkspaceScreen extends WindowTabScreen {
    private final SchematicCommand command;
    private final Path folder;
    private WTextBox file, search, originX, originY, originZ;
    private WLabel status, notice, totals;
    private WView rows, files;
    private String query = "", selected = "", fileMessage = "Finding files...";
    private List<String> filenames = List.of();
    private boolean listing, shortagesOnly = true;
    private MaterialReport.Sort sort = MaterialReport.Sort.Missing;
    private int ticks, page;
    private boolean previouslyReady;
    private BlockPos previousOrigin;
    private double contentWidth;
    private List<MaterialReport.Entry> snapshot = List.of();
    public SchematicWorkspaceScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
        command = (SchematicCommand) Commands.get("schematic");
        folder = mc.gameDirectory.toPath().resolve("schematics");
    }
    @Override public void initWidgets() {
        contentWidth = Math.max(200, Math.min(880, Utils.getWindowWidth() / theme.scale(1) - 90));
        window.padding = 14;
        if (command == null) { add(theme.label("Schematic controller is not ready. Reopen after joining a world.")); return; }
        add(theme.label("Choose a file. Place the origin. Build when ready.", contentWidth)).expandX();
        status = add(theme.label(command.statusText(), contentWidth)).expandX().widget();
        notice = add(theme.label(command.notice(), contentWidth)).expandX().widget();
        var tabs = add(theme.horizontalList()).expandX().widget();
        tabs.add(theme.button("Modules")).widget().action = () -> com.gaspoweredcake.client33.gui.tabs.Tabs.get().getFirst().openScreen(theme);
        tabs.add(theme.button("Refresh files")).widget().action = this::listFiles;
        tabs.add(theme.button("Materials CSV")).widget().action = command::export;
        var steps = add(theme.table()).expandX().widget(); steps.horizontalSpacing = 14; steps.verticalSpacing = 14;
        boolean wide = contentWidth >= 660;
        double stepWidth = wide ? (contentWidth - 14) / 2 : contentWidth;
        var load = steps.add(new WPanel()).minWidth(stepWidth).expandX().top().widget();
        load.add(theme.label("01  /  CHOOSE SCHEMATIC")).pad(12);
        load.add(theme.label("Files in your Minecraft schematics folder.", stepWidth - 28).color(theme.textSecondaryColor())).padHorizontal(12);
        files = load.add(theme.view()).minWidth(stepWidth - 28).expandX().padHorizontal(12).widget(); files.maxHeight = theme.scale(95);
        renderFiles();
        file = load.add(theme.textBox(selected, "Filename, including extension...")).expandX().pad(12).widget();
        file.action = () -> selected = file.get();
        load.add(theme.button("Load selected file")).expandX().padHorizontal(12).padBottom(12).widget().action = () -> command.load(file.get());
        if (!wide) steps.row();
        var build = steps.add(new WPanel()).minWidth(stepWidth).expandX().top().widget();
        build.add(theme.label("02  /  SET ORIGIN & BUILD")).pad(12);
        build.add(theme.label("Origin is the schematic's reference corner.", stepWidth - 28).color(theme.textSecondaryColor())).padHorizontal(12);
        var coordinates = build.add(theme.horizontalList()).expandX().pad(12).widget();
        BlockPos origin = command.origin() != null ? command.origin() : mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        coordinates.add(theme.label("X")); originX = coordinates.add(theme.textBox(Integer.toString(origin.getX()))).minWidth(45).expandX().widget();
        coordinates.add(theme.label("Y")); originY = coordinates.add(theme.textBox(Integer.toString(origin.getY()))).minWidth(45).expandX().widget();
        coordinates.add(theme.label("Z")); originZ = coordinates.add(theme.textBox(Integer.toString(origin.getZ()))).minWidth(45).expandX().widget();
        var positions = build.add(theme.horizontalList()).expandX().padHorizontal(12).widget();
        positions.add(theme.button("Apply coordinates")).expandX().widget().action = () -> {
            try { command.setOrigin(SchematicFiles.origin(originX.get(), originY.get(), originZ.get())); }
            catch (IllegalArgumentException failure) { command.error("Enter whole numbers for X, Y and Z."); }
        };
        positions.add(theme.button("Use my position")).expandX().widget().action = () -> {
            if (mc.player != null) { command.setOrigin(mc.player.blockPosition()); syncOrigin(); }
        };
        var controls = build.add(theme.table()).expandX().pad(12).widget(); controls.horizontalSpacing = 8; controls.verticalSpacing = 8;
        controls.add(theme.button("Build")).expandX().widget().action = () -> { command.startBuild(); if (!command.noticeError() && command.isBuilding()) mc.gui.setScreen(null); };
        controls.add(theme.button("Pause")).expandX().widget().action = command::pauseBuild; controls.row();
        controls.add(theme.button("Resume")).expandX().widget().action = () -> { command.resumeBuild(); if (!command.noticeError() && command.isBuilding()) mc.gui.setScreen(null); };
        controls.add(theme.button("Stop build")).expandX().widget().action = command::stopBuild;
        add(theme.label("03  /  MATERIALS", true)).padTop(8);
        search = add(theme.textBox(query, "Search materials...")).minWidth(contentWidth).expandX().widget();
        search.action = () -> { query = search.get(); page = 0; refreshMaterials(); };
        var filters = add(theme.horizontalList()).expandX().widget();
        var shortages = filters.add(theme.checkbox(shortagesOnly)).widget(); filters.add(theme.label("Only missing items"));
        shortages.action = () -> { shortagesOnly = shortages.checked; page = 0; refreshMaterials(); };
        var order = filters.add(theme.dropdown(MaterialReport.Sort.values(), sort)).expandX().widget();
        order.action = () -> { sort = order.get(); page = 0; refreshMaterials(); };
        totals = add(theme.label("", contentWidth)).expandX().widget();
        rows = add(theme.view()).minWidth(contentWidth).expandX().widget(); rows.maxHeight = theme.scale(180);
        var pages = add(theme.horizontalList()).expandX().widget();
        pages.add(theme.button("Previous")).widget().action = () -> { page = Math.max(0, page - 1); refreshMaterials(); };
        pages.add(theme.button("Next")).widget().action = () -> { if ((page + 1L) * 16 < filtered().size()) page++; refreshMaterials(); };
        pages.add(theme.label("Source totals; built blocks and scaffolding differ.", contentWidth - 170).color(theme.textSecondaryColor())).expandX();
        refreshMaterials(); update(); listFiles();
    }
    private void syncOrigin() {
        BlockPos pos = command.origin(); if (pos == null) return;
        originX.set(Integer.toString(pos.getX())); originY.set(Integer.toString(pos.getY())); originZ.set(Integer.toString(pos.getZ()));
    }
    private void listFiles() {
        if (listing) return; listing = true; fileMessage = "Finding files..."; renderFiles();
        Client33Executor.execute(() -> {
            List<String> found; String message;
            try { found = SchematicFiles.list(folder, baritone.api.BaritoneAPI.getProvider().getSchematicSystem().getFileExtensions()); message = found.isEmpty() ? "No files yet. Add files to " + folder : ""; }
            catch (Exception failure) { found = List.of(); message = "Cannot read folder: " + failure.getMessage(); }
            var result = found; var resultMessage = message;
            mc.execute(() -> { filenames = result; fileMessage = resultMessage; listing = false; if (files != null) renderFiles(); });
        });
    }
    private void renderFiles() {
        if (files == null) return; files.clear();
        if (!fileMessage.isEmpty()) files.add(theme.label(fileMessage, Math.max(160, contentWidth / 2 - 30))).expandX();
        for (String name : filenames) {
            var button = files.add(theme.button(com.gaspoweredcake.client33.gui.utils.CardLayout.ellipsize(name, theme.scale(Math.max(160, contentWidth >= 660 ? (contentWidth - 14) / 2 - 56 : contentWidth - 56)), theme::textWidth))).expandX().widget();
            button.tooltip = name; button.action = () -> { selected = name; file.set(name); };
        }
    }
    private List<MaterialReport.Entry> filtered() { return MaterialReport.filter(snapshot, query, shortagesOnly, sort); }
    private void refreshMaterials() {
        if (rows == null) return; rows.clear();
        if (!command.hasReadySchematic()) { totals.set("Load a schematic to see required items and shortages."); return; }
        var entries = filtered(); int pages = Math.max(1, (entries.size() + 15) / 16); page = Math.min(page, pages - 1);
        long missing = snapshot.stream().filter(e -> !e.manual()).mapToLong(MaterialReport.Entry::missing).sum();
        totals.set("%,d missing items  /  %d types shown  /  page %d of %d".formatted(missing, entries.size(), page + 1, pages));
        if (entries.isEmpty()) rows.add(theme.label(shortagesOnly ? "No matching shortages. Turn off Only missing items to see all totals." : "No matching materials.", contentWidth)).expandX();
        for (var entry : entries.subList(page * 16, Math.min(entries.size(), (page + 1) * 16))) {
            var item = rows.add(new WPanel()).expandX().widget();
            var name = item.add(theme.label(entry.name(), true, contentWidth - 30)).padHorizontal(12).padTop(10).widget(); name.tooltip = entry.id();
            item.add(theme.label(entry.manual() ? "Manual placement: %,d positions".formatted(entry.required()) : "Need %,d   •   Have %,d   •   Missing %,d   •   %d stacks".formatted(entry.required(), entry.inventory(), entry.missing(), entry.stacks()), contentWidth - 30))
                .padHorizontal(12).padTop(4).padBottom(10);
        }
    }
    private void update() {
        status.set(command.statusText()); notice.set(command.notice()); notice.color(command.noticeError() ? new Color(255, 156, 160) : theme.textSecondaryColor());
        boolean ready = command.hasReadySchematic();
        var current = ready ? List.copyOf(command.reportEntries()) : List.<MaterialReport.Entry>of();
        if (ready != previouslyReady || !current.equals(snapshot)) { snapshot = current; previouslyReady = ready; refreshMaterials(); }
        if (!java.util.Objects.equals(previousOrigin, command.origin())) { previousOrigin = command.origin(); syncOrigin(); }
    }
    @Override public void tick() { super.tick(); if (command != null && ++ticks % 10 == 0) update(); }
}
