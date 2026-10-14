/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.hud;

import com.gaspoweredcake.client33.events.client33.CustomFontChangedEvent;
import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.gui.WidgetScreen;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.System;
import com.gaspoweredcake.client33.systems.Systems;
import com.gaspoweredcake.client33.systems.hud.elements.*;
import com.gaspoweredcake.client33.systems.hud.elements.keyboard.KeyboardHud;
import com.gaspoweredcake.client33.systems.hud.screens.HudEditorScreen;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import com.gaspoweredcake.client33.utils.misc.NbtUtils;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NonNull;

import java.util.*;

import static com.gaspoweredcake.client33.Client33.mc;

public class Hud extends System<Hud> implements Iterable<HudElement> {
    public static final HudGroup GROUP = new HudGroup("Client33");

    public boolean active;
    public Settings settings = new Settings();

    public final Map<String, HudElementInfo<?>> infos = new TreeMap<>();
    private final List<HudElement> elements = new ArrayList<>();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEditor = settings.createGroup("Editor");
    private final SettingGroup sgKeybind = settings.createGroup("Bind");

    // General

    private final Setting<Boolean> customFont = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-font")
        .description("Text will use custom font.")
        .defaultValue(true)
        .onChanged(_ -> {
            for (HudElement element : elements) element.onFontChanged();
        })
        .build()
    );

    private final Setting<Boolean> hideInMenus = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-in-menus")
        .description("Hides the client33 hud when in inventory screens or game menus.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of text if not overridden by the element.")
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    public final Setting<List<SettingColor>> textColors = sgGeneral.add(new ColorListSetting.Builder()
        .name("text-colors")
        .description("Colors used for the Text element.")
        .defaultValue(List.of(new SettingColor(), new SettingColor(173, 182, 204), new SettingColor(109, 214, 199), new SettingColor(238, 137, 172)))
        .build()
    );

    // Editor

    public final Setting<Integer> border = sgEditor.add(new IntSetting.Builder()
        .name("border")
        .description("Space around the edges of the screen.")
        .defaultValue(4)
        .sliderMax(20)
        .build()
    );

    public final Setting<Integer> snappingRange = sgEditor.add(new IntSetting.Builder()
        .name("snapping-range")
        .description("Snapping range in editor.")
        .defaultValue(10)
        .sliderMax(20)
        .build()
    );

    // Keybindings
    @SuppressWarnings("unused")
    private final Setting<Keybind> keybind = sgKeybind.add(new KeybindSetting.Builder()
        .name("bind")
        .defaultValue(Keybind.none())
        .action(() -> active = !active)
        .build()
    );

    private boolean resetToDefaultElements;

    public Hud() {
        super("hud");
    }

    public static Hud get() {
        return Systems.get(Hud.class);
    }

    @Override
    public void init() {
        settings.registerColorSettings(null);

        register(Client33TextHud.INFO);
        register(ItemHud.INFO);
        register(InventoryHud.INFO);
        register(CompassHud.INFO);
        register(ArmorHud.INFO);
        register(HoleHud.INFO);
        register(PlayerModelHud.INFO);
        register(ActiveModulesHud.INFO);
        register(LagNotifierHud.INFO);
        register(PlayerRadarHud.INFO);
        register(RadarHud.INFO);
        register(RearviewHud.INFO);
        register(ReachDisplayHud.INFO);
        register(DuelInfoHud.INFO);
        register(ModuleInfosHud.INFO);
        register(PotionTimersHud.INFO);
        register(CombatHud.INFO);
        register(MapHud.INFO);
        register(KeyboardHud.INFO);
        register(ModuleStatusHud.INFO);

        // Default config
        if (isFirstInit) resetToDefaultElements();
    }

    /** Refresh existing layouts without changing element positions or enabled modules. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void applyEuphoricStyle() {
        customFont.set(true);
        textColors.set(List.of(new SettingColor(232, 237, 248), new SettingColor(164, 179, 206), new SettingColor(113, 215, 215), new SettingColor(210, 163, 238)));
        for (var element : elements) {
            var mode = element.settings.get("color-mode");
            if (element instanceof ActiveModulesHud && mode != null) ((Setting) mode).set(ActiveModulesHud.ColorMode.Euphoric);
            if (element instanceof ActiveModulesHud || element instanceof TextHud) {
                var background = element.settings.get("background", Boolean.class);
                if (background != null) background.set(true);
                var color = element.settings.get("background-color", SettingColor.class);
                if (color != null) color.set(new SettingColor(16, 20, 32, 195));
                var shadow = element.settings.get("shadow", Boolean.class);
                if (shadow != null) shadow.set(false);
            }
            element.onFontChanged();
        }
    }

    public void pinModule(String moduleName) {
        if (hasModulePanel(moduleName)) return;
        var panel = new ModuleStatusHud();
        panel.moduleName.set(moduleName);
        add(panel, 30, 30, XAnchor.Left, YAnchor.Top);
    }

    public boolean hasModulePanel(String moduleName) {
        return elements.stream().anyMatch(element -> element instanceof ModuleStatusHud panel && panel.moduleName.get().equals(moduleName));
    }

    public void reattachModule(String moduleName) {
        for (HudElement element : List.copyOf(elements)) {
            if (element instanceof ModuleStatusHud panel && panel.moduleName.get().equals(moduleName)) panel.remove();
        }
    }

    public void register(HudElementInfo<?> info) {
        infos.put(info.name, info);
    }

    private void add(HudElement element, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
        element.box.setPos(x, y);

        if (xAnchor == null || yAnchor == null) element.box.updateAnchors();
        else {
            element.box.xAnchor = xAnchor;
            element.box.yAnchor = yAnchor;
        }

        element.settings.registerColorSettings(null);

        elements.add(element);
    }

    public void add(HudElementInfo<?> info, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
        add(info.create(), x, y, xAnchor, yAnchor);
    }

    public void add(HudElementInfo<?> info, int x, int y) {
        add(info, x, y, null, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void add(HudElementInfo.@NonNull Preset preset, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
        HudElement element = preset.info.create();
        preset.callback.accept(element);
        add(element, x, y, xAnchor, yAnchor);
    }

    public void add(HudElementInfo<?>.@NonNull Preset preset, int x, int y) {
        add(preset, x, y, null, null);
    }

    void remove(HudElement element) {
        element.settings.unregisterColorSettings();
        elements.remove(element);
    }

    public void clear() {
        elements.clear();
    }

    public void resetToDefaultElements() {
        resetToDefaultElements = true;
    }

    private void resetToDefaultElementsImpl() {
        elements.clear();

        int h = (int) Math.ceil(HudRenderer.INSTANCE.textHeight(true));

        // Top Left
        add(Client33TextHud.WATERMARK, 4, 4, XAnchor.Left, YAnchor.Top);
        add(Client33TextHud.FPS, 4, 4 + h, XAnchor.Left, YAnchor.Top);
        add(Client33TextHud.TPS, 4, 4 + h * 2, XAnchor.Left, YAnchor.Top);
        add(Client33TextHud.PING, 4, 4 + h * 3, XAnchor.Left, YAnchor.Top);
        add(Client33TextHud.SPEED, 4, 4 + h * 4, XAnchor.Left, YAnchor.Top);

        // Top Right
        add(ActiveModulesHud.INFO, -4, 4, XAnchor.Right, YAnchor.Top);

        // Bottom Right
        add(Client33TextHud.POSITION, -4, -4, XAnchor.Right, YAnchor.Bottom);
        add(Client33TextHud.OPPOSITE_POSITION, -4, -4 - h, XAnchor.Right, YAnchor.Bottom);
        add(Client33TextHud.ROTATION, -4, -4 - h * 2, XAnchor.Right, YAnchor.Bottom);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (Utils.isLoading()) return;

        if (resetToDefaultElements) {
            resetToDefaultElementsImpl();
            resetToDefaultElements = false;
        }

        if (!(active || HudEditorScreen.isOpen())) return;

        for (HudElement element : elements) {
            if (element.isActive() || element.isInEditor()) {
                element.tick(HudRenderer.INSTANCE);
            }
        }
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (Utils.isLoading()) return;

        if (!(active || HudEditorScreen.isOpen()) || shouldHideHud()) return;
        if ((mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden || mc.debugEntries.isOverlayVisible()) && !HudEditorScreen.isOpen())
            return;

        HudRenderer.INSTANCE.begin(event.graphics);

        for (HudElement element : elements) {
            element.updatePos();

            if (element.isActive() || element.isInEditor()) {
                element.render(HudRenderer.INSTANCE);
            }
        }

        HudRenderer.INSTANCE.end();
    }

    private boolean shouldHideHud() {
        return hideInMenus.get() && mc.gui.screen() != null && !(mc.gui.screen() instanceof WidgetScreen);
    }

    @EventHandler
    private void onCustomFontChanged(CustomFontChangedEvent event) {
        if (customFont.get()) {
            for (HudElement element : elements) element.onFontChanged();
        }
    }

    public boolean hasCustomFont() {
        return customFont.get();
    }

    public double getTextScale() {
        return textScale.get();
    }

    @NonNull
    @Override
    public Iterator<HudElement> iterator() {
        return elements.iterator();
    }

    // Serialization

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("__version__", 1);

        tag.putBoolean("active", active);
        tag.put("settings", settings.toTag());
        tag.put("elements", NbtUtils.listToTag(elements));

        return tag;
    }

    @Override
    public Hud fromTag(CompoundTag tag) {
        if (!tag.contains("__version__")) {
            resetToDefaultElements();
            return this;
        }

        tag.getBoolean("active").ifPresent(active1 -> active = active1);
        settings.fromTag(tag.getCompoundOrEmpty("settings"));

        // Elements
        elements.clear();

        for (Tag e : tag.getListOrEmpty("elements")) {
            CompoundTag c = (CompoundTag) e;
            if (c.getString("name").isEmpty()) continue;

            HudElementInfo<?> info = infos.get(c.getString("name").get());
            if (info != null) {
                HudElement element = info.create();
                element.fromTag(c);
                element.settings.registerColorSettings(null);
                elements.add(element);
            }
        }

        return this;
    }
}
