package com.gaspoweredcake.client33.gui.runtime;

import com.gaspoweredcake.client33.settings.*;
import org.meteordev.starscript.Starscript;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.value.Value;
import org.meteordev.starscript.value.ValueMap;
import java.util.*;

/** Transactional UI declarations using the client's existing Starscript engine. */
public final class RuntimeUi {
    public static final RuntimeUi INSTANCE = new RuntimeUi();
    private Settings settings = new Settings();
    private Map<String, Setting<?>> controls = new LinkedHashMap<>();
    private long revision;
    public Settings settings() { return settings; }
    public long revision() { return revision; }

    public Value value(String tab, String key) {
        var control = controls.get(tab + "/" + key);
        if (control == null) return Value.null_();
        Object value = control.get();
        if (value instanceof Boolean b) return Value.bool(b);
        if (value instanceof Number n) return Value.number(n.doubleValue());
        return Value.string(value.toString());
    }

    public void exposeValues(Starscript engine) {
        engine.set("ui", new ValueMap().set("value", (ss, count) -> {
            if (count != 2) ss.error("ui.value(tab, key) requires two arguments.");
            String key = ss.popString("Expected a control key."), tab = ss.popString("Expected a tab name.");
            return value(tab, key);
        }));
    }

    public void load(String source) {
        if (source.length() > 262144) throw new IllegalArgumentException("UI scripts must be under 256 KiB.");
        var nextSettings = new Settings();
        Map<String, Setting<?>> next = new LinkedHashMap<>();
        Starscript engine = new Starscript();
        var api = new ValueMap();
        api.set("checkbox", (ss, count) -> {
            if (count != 4) ss.error("ui.checkbox(tab, key, label, default) requires four arguments.");
            boolean initial = ss.popBool("Expected a boolean default.");
            String label = ss.popString("Expected a label."), key = ss.popString("Expected a key."), tab = ss.popString("Expected a tab.");
            var setting = new BoolSetting.Builder().name(key).description(label).defaultValue(initial).build();
            add(nextSettings, next, tab, key, label, setting);
            return Value.string("");
        });
        api.set("textbox", (ss, count) -> {
            if (count != 4) ss.error("ui.textbox(tab, key, label, default) requires four arguments.");
            String initial = ss.popString("Expected default text."), label = ss.popString("Expected a label."), key = ss.popString("Expected a key."), tab = ss.popString("Expected a tab.");
            add(nextSettings, next, tab, key, label, new StringSetting.Builder().name(key).description(label).defaultValue(initial).build());
            return Value.string("");
        });
        api.set("slider", (ss, count) -> {
            if (count != 6 && count != 7) ss.error("ui.slider(tab, key, label, default, min, max[, parentCheckbox]) requires six or seven arguments.");
            String parent = count == 7 ? ss.popString("Expected parent checkbox key.") : "";
            double max = ss.popNumber("Expected max."), min = ss.popNumber("Expected min."), initial = ss.popNumber("Expected default.");
            if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(initial) || min >= max || initial < min || initial > max) ss.error("Invalid slider bounds/default.");
            String label = ss.popString("Expected a label."), key = ss.popString("Expected a key."), tab = ss.popString("Expected a tab.");
            if (!parent.isEmpty() && !(next.get(tab + "/" + parent) instanceof BoolSetting)) ss.error("Declare the parent checkbox before its slider.");
            var builder = new DoubleSetting.Builder().name(key).description(label).defaultValue(initial).range(min, max).sliderRange(min, max);
            if (!parent.isEmpty()) builder.visible(() -> Boolean.TRUE.equals(next.get(tab + "/" + parent).get()));
            add(nextSettings, next, tab, key, label, builder.build());
            return Value.string("");
        });
        engine.set("ui", api);
        var parsed = Parser.parse(source);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Invalid UI script: " + parsed.errors.getFirst());
        engine.run(Compiler.compile(parsed));
        if (next.isEmpty()) throw new IllegalArgumentException("Script declares no controls. Use {ui.checkbox(...)} expressions.");
        settings = nextSettings; controls = next; revision++;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void add(Settings target, Map<String, Setting<?>> next, String tab, String key, String label, Setting setting) {
        if (tab.isBlank() || tab.length() > 40 || !key.matches("[a-zA-Z0-9_-]{1,48}") || label.length() > 160) throw new IllegalArgumentException("Use short tab names, alphanumeric control keys and labels under 160 characters.");
        if (next.size() >= 256) throw new IllegalArgumentException("A UI script may declare at most 256 controls.");
        String id = tab + "/" + key;
        if (next.containsKey(id)) throw new IllegalArgumentException("Duplicate control: " + id);
        var old = controls.get(id);
        if (old != null && old.getClass() == setting.getClass()) setting.set(old.get());
        // The label is presentation only; stable keys preserve values across reloads.
        setting.setDisplayTitle(label);
        var group = target.getGroup(tab);
        if (group == null) {
            if (target.sizeGroups() >= 16) throw new IllegalArgumentException("A UI script may declare at most 16 tabs.");
            group = target.createGroup(tab, true);
        }
        group.add(setting); next.put(id, setting);
    }
}
