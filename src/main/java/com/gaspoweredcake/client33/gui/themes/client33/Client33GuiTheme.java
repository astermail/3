/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.themes.client33;

import com.gaspoweredcake.client33.gui.DefaultSettingsWidgetFactory;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WidgetScreen;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.renderer.packer.GuiTexture;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.*;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33Dropdown;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33Slider;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.input.WClient33TextBox;
import com.gaspoweredcake.client33.gui.themes.client33.widgets.pressable.*;
import com.gaspoweredcake.client33.gui.utils.AlignmentX;
import com.gaspoweredcake.client33.gui.utils.CharFilter;
import com.gaspoweredcake.client33.gui.widgets.*;
import com.gaspoweredcake.client33.gui.widgets.containers.WSection;
import com.gaspoweredcake.client33.gui.widgets.containers.WView;
import com.gaspoweredcake.client33.gui.widgets.containers.WWindow;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.input.WSlider;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.gui.widgets.pressable.*;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.accounts.Account;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import com.mojang.blaze3d.platform.MacosUtil;

import static com.gaspoweredcake.client33.Client33.mc;

public class Client33GuiTheme extends GuiTheme {
    private static final Color READABLE_TEXT = new Color(240, 242, 248);
    private static final Color READABLE_SECONDARY = new Color(190, 194, 207);
    private static final Color SCREEN_TINT = new Color(7, 7, 10, 200);
    private static final String SCREEN_MARK = "33";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgTextColors = settings.createGroup("Text");
    private final SettingGroup sgBackgroundColors = settings.createGroup("Background");
    private final SettingGroup sgOutline = settings.createGroup("Outline");
    private final SettingGroup sgSeparator = settings.createGroup("Separator");
    private final SettingGroup sgScrollbar = settings.createGroup("Scrollbar");
    private final SettingGroup sgSlider = settings.createGroup("Slider");
    private final SettingGroup sgStarscript = settings.createGroup("Starscript");

    // General

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the GUI.")
        .defaultValue(1)
        .min(0.75)
        .sliderRange(0.75, 4)
        .onSliderRelease()
        .onChanged(aDouble -> {
            if (mc.gui.screen() instanceof WidgetScreen) ((WidgetScreen) mc.gui.screen()).reload();
        })
        .build()
    );

    public final Setting<AlignmentX> moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>()
        .name("module-alignment")
        .description("How module titles are aligned.")
        .defaultValue(AlignmentX.Left)
        .build()
    );

    public final Setting<Boolean> categoryIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("category-icons")
        .description("Adds item icons to module categories.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideHUD = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-HUD")
        .description("Hide HUD when in GUI.")
        .defaultValue(false)
        .onChanged(v -> {
            if (mc.gui.screen() instanceof WidgetScreen) mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden = v;
        })
        .build()
    );

    public final Setting<Boolean> modulesHelpText = sgGeneral.add(new BoolSetting.Builder()
        .name("modules-help-text")
        .description("Toggle help text in the modules screen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> readableText = sgGeneral.add(new BoolSetting.Builder()
        .name("high-contrast-text")
        .description("Use bright, opaque text and placeholders. Disable to use custom text colors.")
        .defaultValue(true)
        .onChanged(value -> {
            if (mc.gui.screen() instanceof WidgetScreen screen) screen.reload();
        })
        .build()
    );

    public enum Appearance { Euphoric, Custom }
    public final Setting<Appearance> appearance = sgGeneral.add(new EnumSetting.Builder<Appearance>()
        .name("appearance").description("Euphoric cyan/violet surfaces, or your custom colors.").defaultValue(Appearance.Euphoric).build());
    public final Setting<Boolean> animatedAtmosphere = sgGeneral.add(new BoolSetting.Builder()
        .name("animated-atmosphere").description("Slowly moving aurora behind panels. Disable for a still background.").defaultValue(true).build());
    public final Setting<Double> atmosphereIntensity = sgGeneral.add(new DoubleSetting.Builder()
        .name("atmosphere-intensity").description("Brightness of decorative lighting; zero removes the lighting.").defaultValue(0.65).range(0, 1).build());
    private double atmosphereClock;
    public boolean euphoric() { return appearance.get() == Appearance.Euphoric; }
    public Color accent() { return euphoric() ? com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(0, 255) : accentColor.get(); }
    public Color surface() { return surface(false, false); }
    public Color surface(boolean pressed, boolean hovered) { return surface(pressed, hovered, false); }
    public Color surface(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
        if (!euphoric()) return backgroundColor.get(pressed, hovered, bypassDisableHoverColor);
        return pressed ? new Color(50, 42, 77, 255) : hovered && (bypassDisableHoverColor || !disableHoverColor)
            ? new Color(31, 34, 57, 252) : new Color(15, 19, 32, 250);
    }
    public Color outline(boolean pressed, boolean hovered) {
        if (!euphoric()) return outlineColor.get(pressed, hovered);
        return pressed || hovered && !disableHoverColor ? accent() : new Color(62, 64, 92);
    }
    public Color moduleSurface() { return euphoric() ? new Color(34, 33, 61, 245) : moduleBackground.get(); }

    // Colors

    public final Setting<SettingColor> accentColor = color("accent", "Main color of the GUI.", new SettingColor(245, 66, 85));
    public final Setting<SettingColor> checkboxColor = color("checkbox", "Color of checkbox.", new SettingColor(245, 66, 85));
    public final Setting<SettingColor> plusColor = color("plus", "Color of plus button.", new SettingColor(245, 100, 110));
    public final Setting<SettingColor> minusColor = color("minus", "Color of minus button.", new SettingColor(255, 91, 105));
    public final Setting<SettingColor> favoriteColor = color("favorite", "Color of checked favorite button.", new SettingColor(224, 216, 105));

    // Text

    public final Setting<SettingColor> textColor = color(sgTextColors, "text", "Color of text.", new SettingColor(230, 237, 247));
    public final Setting<SettingColor> textSecondaryColor = color(sgTextColors, "text-secondary-text", "Color of secondary text.", new SettingColor(157, 154, 166));
    public final Setting<SettingColor> textHighlightColor = color(sgTextColors, "text-highlight", "Color of text highlighting.", new SettingColor(245, 66, 85, 110));
    public final Setting<SettingColor> titleTextColor = color(sgTextColors, "title-text", "Color of title text.", new SettingColor(255, 229, 231));
    public final Setting<SettingColor> loggedInColor = color(sgTextColors, "logged-in-text", "Color of logged in account name.", new SettingColor(245, 66, 85));
    public final Setting<SettingColor> placeholderColor = color(sgTextColors, "placeholder", "Color of placeholder text.", new SettingColor(157, 154, 166, 145));

    // Background

    public final ThreeStateColorSetting backgroundColor = new ThreeStateColorSetting(
            sgBackgroundColors,
            "background",
            new SettingColor(22, 22, 27, 252),
            new SettingColor(38, 36, 43, 252),
            new SettingColor(62, 36, 44, 255)
    );

    public final Setting<SettingColor> moduleBackground = color(sgBackgroundColors, "module-background", "Color of module background when active.", new SettingColor(78, 27, 39, 240));

    // Outline

    public final ThreeStateColorSetting outlineColor = new ThreeStateColorSetting(
            sgOutline,
            "outline",
            new SettingColor(55, 49, 58),
            new SettingColor(151, 59, 75),
            new SettingColor(245, 66, 85)
    );

    // Separator

    public final Setting<SettingColor> separatorText = color(sgSeparator, "separator-text", "Color of separator text", new SettingColor(231, 210, 219));
    public final Setting<SettingColor> separatorCenter = color(sgSeparator, "separator-center", "Center color of separators.", new SettingColor(159, 77, 95, 170));
    public final Setting<SettingColor> separatorEdges = color(sgSeparator, "separator-edges", "Color of separator edges.", new SettingColor(57, 47, 59, 50));

    // Scrollbar

    public final ThreeStateColorSetting scrollbarColor = new ThreeStateColorSetting(
            sgScrollbar,
            "Scrollbar",
            new SettingColor(57, 47, 59, 200),
            new SettingColor(151, 52, 76, 220),
            new SettingColor(245, 66, 85, 235)
    );

    // Slider

    public final ThreeStateColorSetting sliderHandle = new ThreeStateColorSetting(
            sgSlider,
            "slider-handle",
            new SettingColor(245, 66, 85),
            new SettingColor(255, 153, 163),
            new SettingColor(255, 229, 231)
    );

    public final Setting<SettingColor> sliderLeft = color(sgSlider, "slider-left", "Color of slider left part.", new SettingColor(203, 48, 68));
    public final Setting<SettingColor> sliderRight = color(sgSlider, "slider-right", "Color of slider right part.", new SettingColor(48, 42, 51));

    // Starscript

    private final Setting<SettingColor> starscriptText = color(sgStarscript, "starscript-text", "Color of text in Starscript code.", new SettingColor(196, 223, 206));
    private final Setting<SettingColor> starscriptBraces = color(sgStarscript, "starscript-braces", "Color of braces in Starscript code.", new SettingColor(117, 215, 151));
    private final Setting<SettingColor> starscriptParenthesis = color(sgStarscript, "starscript-parenthesis", "Color of parenthesis in Starscript code.", new SettingColor(156, 198, 172));
    private final Setting<SettingColor> starscriptDots = color(sgStarscript, "starscript-dots", "Color of dots in starscript code.", new SettingColor(153, 184, 163));
    private final Setting<SettingColor> starscriptCommas = color(sgStarscript, "starscript-commas", "Color of commas in starscript code.", new SettingColor(153, 184, 163));
    private final Setting<SettingColor> starscriptOperators = color(sgStarscript, "starscript-operators", "Color of operators in Starscript code.", new SettingColor(103, 226, 202));
    private final Setting<SettingColor> starscriptStrings = color(sgStarscript, "starscript-strings", "Color of strings in Starscript code.", new SettingColor(222, 207, 131));
    private final Setting<SettingColor> starscriptNumbers = color(sgStarscript, "starscript-numbers", "Color of numbers in Starscript code.", new SettingColor(134, 195, 224));
    private final Setting<SettingColor> starscriptKeywords = color(sgStarscript, "starscript-keywords", "Color of keywords in Starscript code.", new SettingColor(255, 128, 133));
    private final Setting<SettingColor> starscriptAccessedObjects = color(sgStarscript, "starscript-accessed-objects", "Color of accessed objects (before a dot) in Starscript code.", new SettingColor(167, 245, 190));

    public Client33GuiTheme() {
        super("33");

        settingsFactory = new DefaultSettingsWidgetFactory(this);
    }

    @Override
    public void renderBackground(GuiRenderer renderer, double width, double height) {
        renderer.quad(0, 0, width, height, SCREEN_TINT);

        if (euphoric() && atmosphereIntensity.get() > 0) {
            if (animatedAtmosphere.get()) atmosphereClock += Math.min(com.gaspoweredcake.client33.utils.Utils.frameTime, 0.1);
            double phase = atmosphereClock * 0.09;
            int segments = 24;
            for (int i = 0; i < segments; i++) {
                double px = width * i / segments, piece = width / segments + 1;
                double py = height * (0.34 + Math.sin(i * 0.16 + phase) * 0.1);
                Color glow = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(i / (double) segments + phase * 0.05, (int) (30 * atmosphereIntensity.get()));
                Color clear = new Color(glow).a(0);
                renderer.quad(px, py - height * 0.12, piece, height * 0.28, clear, clear, glow, glow);
                renderer.quad(px, py + height * 0.16, piece, height * 0.34, glow, glow, clear, clear);
            }
        }

        renderer.text(SCREEN_MARK, width - textWidth(SCREEN_MARK) - scale(36), height - textHeight() - scale(9), textSecondaryColor(), false);
    }

    private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
        return group.add(new ColorSetting.Builder()
                .name(name + "-color")
                .description(description)
                .defaultValue(color)
                .build());
    }
    private Setting<SettingColor> color(String name, String description, SettingColor color) {
        return color(sgColors, name, description, color);
    }

    // Widgets

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WClient33Window(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (maxWidth == 0 && !text.contains("\n")) return w(new WClient33Label(text, title));
        return w(new WClient33MultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return w(new WClient33HorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return w(new WClient33VerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WClient33Button(text, texture));
    }

    @Override
    protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
        return w(new WClient33ConfirmedButton(text, confirmText, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WClient33Minus());
    }

    @Override
    public WConfirmedMinus confirmedMinus() {
        return w(new WClient33ConfirmedMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WClient33Plus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WClient33Checkbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WClient33Slider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WClient33TextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WClient33Dropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return w(new WClient33Triangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WClient33Tooltip(text));
    }

    @Override
    public WView view() {
        return w(new WClient33View());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return w(new WClient33Section(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return w(new WClient33Account(screen, account));
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new WClient33Module(module, title));
    }

    @Override
    public WQuad quad(Color color) {
        return w(new WClient33Quad(color));
    }

    @Override
    public WTopBar topBar() {
        return w(new WClient33TopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return w(new WClient33Favorite(checked));
    }

    // Colors

    @Override
    public Color textColor() {
        return readableText.get() ? READABLE_TEXT : textColor.get();
    }

    @Override
    public Color textSecondaryColor() {
        return readableText.get() ? READABLE_SECONDARY : textSecondaryColor.get();
    }

    public Color placeholderColor() {
        return readableText.get() ? READABLE_SECONDARY : placeholderColor.get();
    }

    //     Starscript

    @Override
    public Color starscriptTextColor() {
        return starscriptText.get();
    }

    @Override
    public Color starscriptBraceColor() {
        return starscriptBraces.get();
    }

    @Override
    public Color starscriptParenthesisColor() {
        return starscriptParenthesis.get();
    }

    @Override
    public Color starscriptDotColor() {
        return starscriptDots.get();
    }

    @Override
    public Color starscriptCommaColor() {
        return starscriptCommas.get();
    }

    @Override
    public Color starscriptOperatorColor() {
        return starscriptOperators.get();
    }

    @Override
    public Color starscriptStringColor() {
        return starscriptStrings.get();
    }

    @Override
    public Color starscriptNumberColor() {
        return starscriptNumbers.get();
    }

    @Override
    public Color starscriptKeywordColor() {
        return starscriptKeywords.get();
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return starscriptAccessedObjects.get();
    }

    // Other

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        double scaled = value * scale.get();

        if (MacosUtil.IS_MACOS) {
            scaled /= (double) mc.getWindow().getWidth() / mc.getWindow().getWidth();
        }

        return scaled;
    }

    @Override
    public boolean categoryIcons() {
        return categoryIcons.get();
    }

    @Override
    public boolean modulesHelpText() {
        return modulesHelpText.get();
    }

    @Override
    public boolean hideHUD() {
        return hideHUD.get();
    }

    public class ThreeStateColorSetting {
        private final Setting<SettingColor> normal, hovered, pressed;

        public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
            normal = color(group, name, "Color of " + name + ".", c1);
            hovered = color(group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
            pressed = color(group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
        }

        public SettingColor get() {
            return normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
            if (pressed) return this.pressed.get();
            return (hovered && (bypassDisableHoverColor || !disableHoverColor)) ? this.hovered.get() : this.normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered) {
            return get(pressed, hovered, false);
        }
    }
}
