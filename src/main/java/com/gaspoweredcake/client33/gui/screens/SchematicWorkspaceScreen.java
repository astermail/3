package com.gaspoweredcake.client33.gui.screens;

import baritone.api.BaritoneAPI;
import com.gaspoweredcake.client33.commands.Commands;
import com.gaspoweredcake.client33.commands.commands.SchematicCommand;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.Tab;
import com.gaspoweredcake.client33.gui.tabs.WindowTabScreen;
import com.gaspoweredcake.client33.gui.utils.CardLayout;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.WActionButton;
import com.gaspoweredcake.client33.gui.widgets.pressable.WNavigationItem;
import com.gaspoweredcake.client33.gui.widgets.pressable.WWorkspaceTab;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.world.*;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import static com.gaspoweredcake.client33.Client33.mc;

/** File discovery stays on a worker; all world actions use the shared schematic controller. */
public class SchematicWorkspaceScreen extends WindowTabScreen {
    private enum Page { Library, Build, Materials }
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final SchematicCommand command;
    private final Path folder;
    private Page page = Page.Library;
    private WHorizontalList navigation;
    private WView pageBody, files, rows;
    private WTextBox librarySearch, importPath, materialSearch, originX, originY, originZ;
    private WLabel status, notice, librarySummary, selectedName, selectedMeta, totals, loadedName, dimensions, originHint, buildHint;
    private String libraryQuery = "", materialQuery = "", selected = "", importDraft = "";
    private String draftX, draftY, draftZ;
    private String message = "", fileMessage = "Finding files...";
    private boolean messageError, listing, refreshAgain, importing, choosing, revealSelection, shortagesOnly = true;
    private SchematicLibrary.Sort librarySort = SchematicLibrary.Sort.Name;
    private MaterialReport.Sort materialSort = MaterialReport.Sort.Missing;
    private List<SchematicLibrary.Entry> library = List.of();
    private List<MaterialReport.Entry> snapshot = List.of();
    private List<String> extensions = List.of();
    private int libraryPage, materialPage, pageSize, ticks;
    private double contentWidth, columnWidth;
    private boolean wide, previouslyReady;
    private BlockPos previousOrigin;

    public SchematicWorkspaceScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
        command = (SchematicCommand) Commands.get("schematic");
        folder = mc.gameDirectory.toPath().resolve("schematics");
    }

    @Override public void initWidgets() {
        resetPageWidgets();
        contentWidth = Math.max(200, Math.min(860, Utils.getWindowWidth() / theme.scale(1) - 70));
        if (command == null) {
            add(theme.label("Install the matching Baritone version to use the schematic workspace.", contentWidth));
            return;
        }
        extensions = List.copyOf(BaritoneAPI.getProvider().getSchematicSystem().getFileExtensions());
        wide = contentWidth >= 620;
        columnWidth = wide ? (contentWidth - 18) / 2 : contentWidth - 8;
        pageSize = window.maxHeight / theme.scale(1) < 400 ? 4 : 6;
        var workspace = add(new WAdaptiveContent()).minWidth(contentWidth).expandX().widget();
        workspace.availableHeight = Math.min(window.maxHeight, theme.scale(420));
        workspace.spacing = 6;
        status = workspace.add(theme.label("", contentWidth)).expandX().widget();
        navigation = workspace.add(theme.horizontalList()).expandX().widget();
        refreshNavigation();
        notice = workspace.add(theme.label("", contentWidth)).expandX().widget();
        pageBody = workspace.add(theme.view()).minWidth(contentWidth).expandX().widget();
        pageBody.spacing = 6;
        workspace.results = pageBody;
        buildPage();
        update();
        if (library.isEmpty() && !listing) listFiles();
    }

    private void resetPageWidgets() {
        files = rows = null;
        librarySearch = importPath = materialSearch = originX = originY = originZ = null;
        librarySummary = selectedName = selectedMeta = totals = loadedName = dimensions = originHint = buildHint = null;
    }

    private void refreshNavigation() {
        navigation.clear();
        for (Page destination : Page.values()) {
            navigation.add(new WWorkspaceTab(destination.toString(), destination == page, () -> showPage(destination))).expandX();
        }
    }

    private void showPage(Page destination) {
        page = destination;
        taskAfterRender = () -> {
            resetPageWidgets();
            refreshNavigation();
            pageBody.clear();
            pageBody.resetScroll();
            buildPage();
            update();
        };
    }

    private void buildPage() {
        switch (page) {
            case Library -> buildLibrary();
            case Build -> buildPlacement();
            case Materials -> buildMaterials();
        }
    }

    private WPanel card(WTable grid, String heading) {
        WPanel panel = grid.add(new WPanel()).minWidth(columnWidth).expandX().top().widget();
        panel.spacing = 5;
        panel.add(theme.label(heading, true)).pad(8);
        return panel;
    }

    private WTable columns() {
        WTable grid = pageBody.add(theme.table()).expandX().widget();
        grid.horizontalSpacing = 10;
        grid.verticalSpacing = 8;
        return grid;
    }

    private WActionButton action(WContainer container, String title, BooleanSupplier available, Runnable operation, boolean primary) {
        WActionButton button = container.add(new WActionButton(title, available, () -> {
            message = "";
            operation.run();
            update();
        }, primary)).expandX().widget();
        return button;
    }

    private void buildLibrary() {
        WTable grid = columns();
        WPanel libraryCard = card(grid, "Your library");
        var filters = libraryCard.add(theme.horizontalList()).expandX().padHorizontal(8).widget();
        librarySearch = filters.add(theme.textBox(libraryQuery, "Find a schematic...")).minWidth(Math.max(90, columnWidth - 145)).expandX().widget();
        librarySearch.action = () -> { libraryQuery = librarySearch.get(); libraryPage = 0; renderFiles(); };
        var order = filters.add(theme.dropdown(SchematicLibrary.Sort.values(), librarySort)).widget();
        order.action = () -> { librarySort = order.get(); libraryPage = 0; renderFiles(); };
        files = libraryCard.add(theme.view()).minWidth(columnWidth - 20).expandX().padHorizontal(8).widget();
        files.spacing = 2;
        files.maxHeight = pageSize * (theme.textHeight() + theme.scale(12));
        librarySummary = libraryCard.add(theme.label("", columnWidth - 20).color(theme.textSecondaryColor())).padHorizontal(8).widget();
        var pager = libraryCard.add(theme.horizontalList()).expandX().pad(8).widget();
        action(pager, "Previous", () -> libraryPage > 0, () -> { libraryPage--; renderFiles(); }, false);
        action(pager, "Next", () -> (libraryPage + 1L) * pageSize < filteredFiles().size(), () -> { libraryPage++; renderFiles(); }, false);
        action(pager, "Refresh", () -> !listing, this::listFiles, false);
        if (!wide) grid.row();
        WPanel importCard = card(grid, "Import & selection");
        var browse = importCard.add(theme.horizontalList()).expandX().padHorizontal(8).widget();
        action(browse, "Choose file...", () -> !importing && !choosing, this::chooseFile, true).tooltip = "Copy a schematic into your library. Supported: " + String.join(", ", extensions) + ". You can also drop one file onto this screen.";
        action(browse, "Open folder", () -> true, this::openFolder, false);
        importPath = importCard.add(theme.textBox(importDraft, "Or paste the full file path...")).expandX().padHorizontal(8).widget();
        importPath.action = () -> importDraft = importPath.get();
        action(importCard, "Import path", () -> !importing && !importDraft.isBlank(), () -> {
            try { importFile(SchematicFiles.externalPath(importDraft)); }
            catch (IllegalArgumentException failure) { tell(failure.getMessage(), true); }
        }, false);
        selectedName = importCard.add(theme.label("", columnWidth - 20)).expandX().padHorizontal(8).widget();
        selectedMeta = importCard.add(theme.label("", columnWidth - 20).color(theme.textSecondaryColor())).expandX().padHorizontal(8).widget();
        var load = importCard.add(theme.horizontalList()).expandX().pad(8).widget();
        action(load, "Load & continue", () -> state().canLoad() && !selected.isEmpty() && !importing, () -> {
            command.load(selected);
            if (!command.noticeError()) showPage(Page.Build);
        }, true).tooltip = "Load the selected file, then configure its origin in Build. Join a world first.";
        action(load, "Build setup", () -> command.hasReadySchematic(), () -> showPage(Page.Build), false);
        renderFiles();
        updateSelection();
    }

    private void buildPlacement() {
        WTable grid = columns();
        WPanel placement = card(grid, "Placement");
        loadedName = placement.add(theme.label("", columnWidth - 20)).expandX().padHorizontal(8).widget();
        dimensions = placement.add(theme.label("", columnWidth - 20).color(theme.textSecondaryColor())).expandX().padHorizontal(8).widget();
        if (draftX == null) setDraftOrigin(command.origin() != null ? command.origin() : mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO);
        var coordinates = placement.add(theme.table()).expandX().padHorizontal(8).widget();
        coordinates.horizontalSpacing = 6;
        for (String axis : new String[]{"X", "Y", "Z"}) coordinates.add(theme.label(axis)).expandX();
        coordinates.row();
        double inputWidth = Math.max(36, (columnWidth - 32) / 3);
        originX = coordinates.add(theme.textBox(draftX)).minWidth(inputWidth).expandX().widget();
        originY = coordinates.add(theme.textBox(draftY)).minWidth(inputWidth).expandX().widget();
        originZ = coordinates.add(theme.textBox(draftZ)).minWidth(inputWidth).expandX().widget();
        originX.action = () -> { draftX = originX.get(); update(); };
        originY.action = () -> { draftY = originY.get(); update(); };
        originZ.action = () -> { draftZ = originZ.get(); update(); };
        var positioning = placement.add(theme.horizontalList()).expandX().padHorizontal(8).widget();
        action(positioning, "My position", () -> state().canPlace(), () -> setDraftOrigin(mc.player.blockPosition()), false);
        action(positioning, "Apply origin", () -> state().canPlace() && draftValid(), this::applyOrigin, false);
        originHint = placement.add(theme.label("", columnWidth - 20).color(theme.textSecondaryColor())).expandX().padHorizontal(8).padBottom(8).widget();
        if (!wide) grid.row();
        WPanel controls = card(grid, "Build session");
        buildHint = controls.add(theme.label("", columnWidth - 20)).expandX().padHorizontal(8).widget();
        action(controls, "Start build", () -> state().canPlace() && draftValid(), () -> {
            if (applyOrigin()) {
                command.startBuild();
                if (!command.noticeError() && command.isBuilding()) mc.gui.setScreen(null);
            }
        }, true).tooltip = "Apply the entered coordinates and start. Baritone handles existing correct blocks and restocking.";
        var runtime = controls.add(theme.horizontalList()).expandX().padHorizontal(8).widget();
        action(runtime, "Pause", () -> state().canPause(), command::pauseBuild, false);
        action(runtime, "Resume", () -> state().canResume(), () -> {
            command.resumeBuild();
            if (!command.noticeError() && command.isBuilding() && !command.isPaused()) mc.gui.setScreen(null);
        }, false);
        action(controls, "Stop build", () -> state().canStop(), command::stopBuild, false).tooltip = "End the current build. The loaded schematic remains available.";
        var links = controls.add(theme.horizontalList()).expandX().pad(8).widget();
        action(links, "Materials", () -> command.hasReadySchematic(), () -> showPage(Page.Materials), false);
        action(links, "Choose another", () -> !command.isBuilding() && !command.isLoading(), () -> showPage(Page.Library), false);
        updatePlacement();
    }

    private void buildMaterials() {
        var filters = pageBody.add(theme.horizontalList()).expandX().widget();
        materialSearch = filters.add(theme.textBox(materialQuery, "Find an item or registry ID...")).minWidth(contentWidth - 160).expandX().widget();
        materialSearch.action = () -> { materialQuery = materialSearch.get(); materialPage = 0; refreshMaterials(); };
        var order = filters.add(theme.dropdown(MaterialReport.Sort.values(), materialSort)).widget();
        order.action = () -> { materialSort = order.get(); materialPage = 0; refreshMaterials(); };
        var toolbar = pageBody.add(theme.horizontalList()).expandX().widget();
        var shortages = toolbar.add(theme.checkbox(shortagesOnly)).widget();
        toolbar.add(theme.label("Missing only")).expandCellX();
        shortages.action = () -> { shortagesOnly = shortages.checked; materialPage = 0; refreshMaterials(); };
        action(toolbar, "Export CSV", () -> command.hasReadySchematic(), command::export, false);
        totals = pageBody.add(theme.label("", contentWidth)).expandX().widget();
        rows = pageBody.add(theme.view()).minWidth(contentWidth - 12).expandX().widget();
        rows.maxHeight = Math.max(theme.scale(70), window.maxHeight - theme.scale(230));
        rows.spacing = 2;
        var pager = pageBody.add(theme.horizontalList()).expandX().widget();
        action(pager, "Previous", () -> materialPage > 0, () -> { materialPage--; refreshMaterials(); }, false);
        action(pager, "Next", () -> (materialPage + 1L) * pageSize < filteredMaterials().size(), () -> { materialPage++; refreshMaterials(); }, false);
        action(pager, "Build setup", () -> command.hasReadySchematic(), () -> showPage(Page.Build), false);
        pageBody.add(theme.label("Source totals; existing blocks, scaffolding and container contents are not deducted.", contentWidth).color(theme.textSecondaryColor()));
        refreshMaterials();
    }

    private SchematicBuildState state() {
        return new SchematicBuildState(mc.player != null && mc.level != null, command.isLoading(), command.hasReadySchematic(), command.isBuilding(), command.isPaused());
    }

    private boolean draftValid() {
        try { SchematicFiles.origin(draftX, draftY, draftZ); return true; }
        catch (IllegalArgumentException | NullPointerException invalid) { return false; }
    }

    private boolean applyOrigin() {
        if (!state().canPlace() || !draftValid()) return false;
        BlockPos position = SchematicFiles.origin(draftX, draftY, draftZ);
        if (!position.equals(command.origin())) command.setOrigin(position);
        return position.equals(command.origin());
    }

    private void setDraftOrigin(BlockPos position) {
        draftX = Integer.toString(position.getX()); draftY = Integer.toString(position.getY()); draftZ = Integer.toString(position.getZ());
        if (originX != null) { originX.set(draftX); originY.set(draftY); originZ.set(draftZ); }
    }

    private void updatePlacement() {
        if (loadedName == null) return;
        fit(loadedName, command.loadedFilename().isEmpty() ? "Load a file in Library first." : command.loadedFilename(), columnWidth - 20);
        fit(dimensions, command.dimensionsText(), columnWidth - 20);
        String originText = !draftValid() ? "Enter whole numbers for X, Y and Z."
            : command.isBuilding() ? "Stop the build before moving its origin."
            : !command.hasReadySchematic() ? "Origin becomes available after loading."
            : Objects.equals(SchematicFiles.origin(draftX, draftY, draftZ), command.origin()) ? "Origin applied. X / Y / Z reference corner."
            : "Draft origin. Start build applies these coordinates.";
        fit(originHint, originText, columnWidth - 20);
        String hint;
        if (command.isLoading()) hint = "Loading and counting materials...";
        else if (command.isPaused()) hint = "Paused. Resume continues the current build.";
        else if (command.isBuilding()) hint = "Building. Pause keeps the current session.";
        else if (!command.hasReadySchematic()) hint = "Select and load a schematic in Library.";
        else {
            long missing = snapshot.stream().filter(entry -> !entry.manual()).mapToLong(MaterialReport.Entry::missing).sum();
            hint = missing > 0 ? "%,d items short. Check Materials before building.".formatted(missing) : "Inventory covers the source item totals.";
        }
        fit(buildHint, hint, columnWidth - 20);
    }

    private void openFolder() {
        Client33Executor.execute(() -> {
            try { java.nio.file.Files.createDirectories(folder); net.minecraft.util.Util.getPlatform().openFile(folder.toFile()); }
            catch (Exception failure) { mc.execute(() -> tell("Cannot open folder: " + failure.getMessage(), true)); }
        });
    }

    private void chooseFile() {
        if (importing || choosing) return;
        choosing = true;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var filters = stack.mallocPointer(extensions.size());
            for (String extension : extensions) filters.put(stack.UTF8("*." + extension.replaceFirst("^\\.", "")));
            filters.flip();
            String chosen = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Import schematic", folder.toAbsolutePath().toString(), filters, "Schematic files", false);
            if (chosen != null) importFile(Path.of(chosen));
        } catch (Exception failure) { tell("Picker unavailable. Paste a file path or drop a file here. " + failure.getMessage(), true); }
        finally { choosing = false; }
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (command == null) return;
        if (paths.size() != 1) { tell("Drop one schematic at a time.", true); return; }
        importFile(paths.getFirst());
    }

    private void importFile(Path source) {
        if (importing) { tell("An import is already running.", true); return; }
        importing = true;
        tell("Importing " + source.getFileName() + "...", false);
        Client33Executor.execute(() -> {
            try {
                String imported = SchematicFiles.importFile(source, folder, extensions);
                mc.execute(() -> {
                    importing = false; selected = imported; revealSelection = true;
                    libraryQuery = "";
                    if (librarySearch != null) librarySearch.set("");
                    tell("Imported " + imported + ". Load & continue to choose its placement.", false);
                    updateSelection(); listFiles();
                });
            } catch (Exception failure) {
                mc.execute(() -> { importing = false; tell("Import failed: " + failure.getMessage(), true); });
            }
        });
    }

    private void listFiles() {
        if (listing) { refreshAgain = true; return; }
        listing = true; fileMessage = "Finding files..."; renderFiles();
        Client33Executor.execute(() -> {
            List<SchematicLibrary.Entry> found; String failureMessage;
            try { found = SchematicFiles.scan(folder, extensions); failureMessage = ""; }
            catch (Exception failure) { found = List.of(); failureMessage = "Cannot read library: " + failure.getMessage(); }
            var result = found; var resultMessage = failureMessage;
            mc.execute(() -> {
                library = result; fileMessage = resultMessage; listing = false;
                if (revealSelection) {
                    var filtered = filteredFiles();
                    for (int i = 0; i < filtered.size(); i++) if (filtered.get(i).name().equals(selected)) { libraryPage = i / Math.max(1, pageSize); revealSelection = false; break; }
                }
                renderFiles(); updateSelection();
                if (refreshAgain) { refreshAgain = false; listFiles(); }
            });
        });
    }

    private List<SchematicLibrary.Entry> filteredFiles() { return SchematicLibrary.filter(library, libraryQuery, librarySort); }

    private void renderFiles() {
        if (files == null) return;
        files.clear();
        var entries = filteredFiles();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        libraryPage = Math.min(libraryPage, pages - 1);
        fit(librarySummary, "%d / %d files  ·  %d / %d".formatted(entries.size(), library.size(), libraryPage + 1, pages), columnWidth - 20);
        if (!fileMessage.isEmpty()) files.add(theme.label(fileMessage, columnWidth - 22));
        else if (entries.isEmpty()) files.add(theme.label(library.isEmpty() ? "Choose a file, paste its path, or drop it here." : "No files match this search.", columnWidth - 22));
        for (var entry : entries.subList(libraryPage * pageSize, Math.min(entries.size(), (libraryPage + 1) * pageSize))) {
            var row = files.add(new WNavigationItem(entry.name(), selected.equals(entry.name()), () -> {
                selected = entry.name(); renderFiles(); updateSelection();
            })).expandX().widget();
            row.tooltip = entry.name() + "\n" + entry.format() + " · " + entry.sizeText() + " · Modified " + MODIFIED.format(Instant.ofEpochMilli(entry.modifiedMillis()));
        }
    }

    private void updateSelection() {
        if (selectedName == null) return;
        fit(selectedName, selected.isEmpty() ? "No file selected" : selected, columnWidth - 20);
        var entry = library.stream().filter(value -> value.name().equals(selected)).findFirst();
        fit(selectedMeta, entry.map(value -> value.format() + "  /  " + value.sizeText()).orElse("Select a file in your library to load it."), columnWidth - 20);
    }

    private List<MaterialReport.Entry> filteredMaterials() { return MaterialReport.filter(snapshot, materialQuery, shortagesOnly, materialSort); }

    private void refreshMaterials() {
        if (rows == null) return;
        rows.clear();
        if (!command.hasReadySchematic()) { fit(totals, "Load a schematic in Library to see its materials.", contentWidth); return; }
        var entries = filteredMaterials();
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        materialPage = Math.min(materialPage, pages - 1);
        long missing = snapshot.stream().filter(entry -> !entry.manual()).mapToLong(MaterialReport.Entry::missing).sum();
        fit(totals, "%,d missing items  /  %d types  /  %d of %d".formatted(missing, entries.size(), materialPage + 1, pages), contentWidth);
        if (entries.isEmpty()) rows.add(theme.label(shortagesOnly ? "No matching shortages. Disable Missing only to see all materials." : "No matching materials.", contentWidth - 14));
        var table = rows.add(theme.table()).expandX().widget();
        table.horizontalSpacing = 14; table.verticalSpacing = 6;
        table.add(theme.label("Material", true)).expandX();
        table.add(theme.label("Need", true)).right(); table.add(theme.label("Have", true)).right(); table.add(theme.label("Missing", true)).right(); table.row();
        double nameWidth = Math.max(70, contentWidth - 300);
        for (var entry : entries.subList(materialPage * pageSize, Math.min(entries.size(), (materialPage + 1) * pageSize))) {
            var name = table.add(theme.label(CardLayout.ellipsize(entry.name(), theme.scale(nameWidth), theme::textWidth))).expandX().widget();
            name.tooltip = entry.id() + (entry.manual() ? "\nManual handling required" : "\n" + entry.stacks() + " stacks required (" + entry.stackSize() + " per stack)");
            table.add(theme.label("%,d".formatted(entry.required()))).right();
            table.add(theme.label(entry.manual() ? "Manual" : "%,d".formatted(entry.inventory()))).right();
            table.add(theme.label(entry.manual() ? "—" : "%,d".formatted(entry.missing()))
                .color(!entry.manual() && entry.missing() > 0 ? new Color(245, 167, 139) : theme.textSecondaryColor())).right();
            table.row();
        }
    }

    private void fit(WLabel label, String text, double width) {
        if (label == null) return;
        label.set(CardLayout.ellipsize(text, theme.scale(Math.max(60, width)), theme::textWidth));
        label.tooltip = text;
    }

    private void tell(String text, boolean error) {
        message = text; messageError = error;
        if (notice != null) updateNotice();
    }

    private void updateNotice() {
        String text = message.isEmpty() ? command.notice() : message;
        fit(notice, text, contentWidth);
        notice.color((message.isEmpty() ? command.noticeError() : messageError) ? new Color(255, 156, 160) : theme.textSecondaryColor());
    }

    private void update() {
        if (status == null) return;
        fit(status, command.statusText(), contentWidth);
        updateNotice();
        boolean ready = command.hasReadySchematic();
        var current = ready ? List.copyOf(command.reportEntries()) : List.<MaterialReport.Entry>of();
        if (ready != previouslyReady || !current.equals(snapshot)) { snapshot = current; previouslyReady = ready; refreshMaterials(); }
        if (!Objects.equals(previousOrigin, command.origin())) {
            previousOrigin = command.origin();
            if (previousOrigin != null) setDraftOrigin(previousOrigin);
        }
        updatePlacement();
    }

    @Override public void tick() {
        super.tick();
        if (command != null && ++ticks % 5 == 0) update();
    }
}
