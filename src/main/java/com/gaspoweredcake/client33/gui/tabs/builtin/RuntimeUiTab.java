package com.gaspoweredcake.client33.gui.tabs.builtin;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.tabs.*;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.WSettingsWorkspace;
import com.gaspoweredcake.client33.gui.widgets.containers.WVerticalList;
import com.gaspoweredcake.client33.gui.runtime.RuntimeUi;
import com.gaspoweredcake.client33.utils.network.Client33Executor;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.*;
import static com.gaspoweredcake.client33.Client33.mc;

public final class RuntimeUiTab extends Tab {
    public RuntimeUiTab() { super("Scripts"); }
    @Override public TabScreen createScreen(GuiTheme theme) { return new ScriptScreen(theme, this); }
    @Override public boolean isScreen(Screen screen) { return screen instanceof ScriptScreen; }
    private static String path = "";

    private static final class ScriptScreen extends WindowTabScreen {
        private WLabel status;
        private WVerticalList controls;
        private WSettingsWorkspace workspace;
        private boolean loading;
        private long revision = -1;
        ScriptScreen(GuiTheme theme, Tab tab) { super(theme, tab); }
        @Override public void initWidgets() {
            add(theme.label("Runtime controls", true));
            add(theme.label("Load a Starscript UI file. Changes appear without restarting.", 560));
            var input = add(theme.textBox(path, "Path to a .starscript file...")).minWidth(460).expandX().widget();
            input.action = () -> path = input.get();
            var actions = add(theme.horizontalList()).expandX().widget();
            actions.add(theme.button("Browse...")).widget().action = () -> {
                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var filter = stack.mallocPointer(1).put(stack.UTF8("*.starscript")).flip();
                    String selected = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Load UI script", null, filter, "Starscript UI", false);
                    if (selected != null) { path = selected; input.set(path); load(); }
                }
            };
            actions.add(theme.button("Load / Reload")).widget().action = this::load;
            actions.add(theme.button("Example")).widget().action = () -> {
                try {
                    RuntimeUi.INSTANCE.load("{ui.checkbox(\"Practice\", \"enabled\", \"Enable practice controls\", false)}\n{ui.slider(\"Practice\", \"range\", \"Range\", 4, 1, 8, \"enabled\")}\n{ui.textbox(\"Practice\", \"label\", \"Label\", \"Training\")}");
                    refresh(); status.set("Example loaded. Other Starscript expressions can read ui.value(\"Practice\", \"range\").");
                } catch (Exception failure) { status.set(failure.getMessage()); }
            };
            actions.add(theme.button("CPU timing")).widget().action = () -> status.set(com.gaspoweredcake.client33.gui.runtime.MenuMetrics.summary());
            status = add(theme.label("UI declarations use ui.checkbox, ui.slider and ui.textbox.", 560)).expandX().widget();
            controls = add(theme.verticalList()).expandX().widget();
            refresh();
        }
        private void refresh() {
            controls.clear(); revision = RuntimeUi.INSTANCE.revision();
            workspace = controls.add(new WSettingsWorkspace(RuntimeUi.INSTANCE.settings(), "")).expandX().widget();
        }
        private void load() {
            if (loading) return;
            final Path selected;
            try { selected = Path.of(path); } catch (Exception failure) { status.set("Enter a valid file path."); return; }
            loading = true; status.set("Loading script...");
            Client33Executor.execute(() -> {
                try {
                    String source;
                    try (var stream = Files.newInputStream(selected)) {
                        byte[] bytes = stream.readNBytes(262145);
                        if (bytes.length > 262144) throw new IllegalArgumentException("UI script exceeds 256 KiB.");
                        source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    mc.execute(() -> {
                        try { RuntimeUi.INSTANCE.load(source); refresh(); status.set("Loaded " + selected.getFileName() + ". Values are preserved when matching controls reload."); }
                        catch (Exception failure) { status.set("Script rejected; previous controls retained. " + failure.getMessage()); }
                        finally { loading = false; }
                    });
                } catch (Exception failure) { mc.execute(() -> { loading = false; status.set("Cannot load script: " + failure.getMessage()); }); }
            });
        }
        @Override public void tick() {
            super.tick();
            if (controls == null) return;
            if (revision != RuntimeUi.INSTANCE.revision()) refresh();
            if (workspace != null) workspace.refreshIfNeeded();
        }
    }
}
