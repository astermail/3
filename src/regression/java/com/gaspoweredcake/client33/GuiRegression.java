package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.gui.*;
import com.gaspoweredcake.client33.gui.renderer.packer.GuiTexture;
import com.gaspoweredcake.client33.gui.utils.*;
import com.gaspoweredcake.client33.gui.widgets.*;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.*;
import com.gaspoweredcake.client33.gui.widgets.pressable.*;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.systems.accounts.Account;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.misc.TemporaryOverrides;
import com.gaspoweredcake.client33.utils.render.color.Color;
import net.minecraft.client.input.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic widget events and geometry; no graphics context or Minecraft session required. */
public final class GuiRegression {
    private static final GuiTheme THEME = new TestTheme();
    public static void run() {
        var parent = new com.gaspoweredcake.client33.gui.renderer.Scissor().set(10, 10, 40, 40);
        var clip = new com.gaspoweredcake.client33.gui.renderer.Scissor().intersect(0, 0, 25, 25, parent);
        check(clip.x == 10 && clip.y == 10 && clip.width == 15 && clip.height == 15, "Clipping the left/top edges also shrinks the content rectangle");
        clip.intersect(0, 0, 80, 80, parent);
        check(clip.width == 40 && clip.height == 40, "A child spanning both edges cannot escape its parent clip");
        clip.intersect(80, 80, 20, 20, parent);
        check(clip.width == 0 && clip.height == 0, "Disjoint children have an empty clip");
        controlRegressions();
        var lower = PopupBounds.fit(20, 20, 80, 200, 4);
        check(lower.y() == 40 && lower.height() == 80, "A menu with room opens below its control");
        var upper = PopupBounds.fit(160, 20, 100, 200, 4);
        check(upper.y() == 60 && upper.height() == 100, "Bottom-edge menus open upwards");
        var longMenu = PopupBounds.fit(70, 20, 500, 200, 4);
        check(longMenu.height() == 106 && longMenu.y() == 90, "Oversized menus use bounded scroll viewports");
        var narrow = PopupBounds.fit(0, 20, 500, 30, 4);
        check(narrow.height() == 6, "Tiny viewports do not get negative menu height");
        check(TextWrapping.wrap("averylongidentifier", 5, String::length).equals(List.of("avery", "longi", "denti", "fier")),
            "Long identifiers wrap without a leading blank line");
        check(TextWrapping.wrap("one two\n\nthree\n", 7, String::length).equals(List.of("one two", "", "three", "")),
            "Paragraph and trailing blank lines survive wrapping");
        var unicode = TextWrapping.wrap("a😀b😀", 2, value -> value.codePointCount(0, value.length()));
        check(unicode.equals(List.of("a😀", "b😀")), "Unicode surrogate pairs are never split");

        AtomicInteger pressed = new AtomicInteger();
        WPressable button = new WPressable() {};
        button.action = pressed::incrementAndGet;
        button.mouseOver = true;
        check(button.mouseClicked(click(0), false) && button.isFocused(), "Press captures pointer release");
        button.mouseOver = false;
        check(button.mouseReleased(click(0)) && pressed.get() == 0 && !button.isFocused(), "Dragging outside cancels activation and releases capture");
        button.mouseOver = true;
        button.mouseClicked(click(0), false);
        check(!button.mouseReleased(click(1)) && pressed.get() == 0, "A different mouse button cannot activate the control");
        button.mouseReleased(click(0));
        check(pressed.get() == 1, "Matching release inside activates once");

        WContainer container = new WContainer() {};
        WWidget underlying = new WWidget() { @Override public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
            pressed.incrementAndGet(); return true;
        }};
        WWidget overlay = new WWidget() { @Override public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) { return true; }};
        overlay.focused = true;
        container.add(underlying); container.add(overlay);
        container.mouseClicked(click(0), false);
        check(pressed.get() == 1, "Focused overlays receive input before controls beneath them");
        overlay.visible = false;
        container.mouseClicked(click(0), false);
        check(pressed.get() == 2, "Invisible overlays cannot steal clicks");

        TestView view = new TestView();
        view.theme = THEME; view.maxHeight = 100;
        WWidget content = view.add(new WWidget() { @Override protected void onCalculateSize() { width = 120; height = 500; }}).widget();
        view.calculateSize();
        view.mouseOver = true;
        check(view.onMouseScrolled(-100), "Wheel movement reaching the bottom is consumed");
        check(!view.onMouseScrolled(-1), "Only later movement beyond an edge bubbles to the parent");
        check(view.onMouseScrolled(100), "Movement reaching the top is consumed");
        check(!view.onMouseScrolled(1), "Already-at-top movement bubbles");
        check(view.thumbHeight() >= 24, "Long content keeps a usable scrollbar thumb");
        view.x = view.y = 0; view.width = 120; view.height = 100;
        content.x = 0; content.y = 80; content.width = 100; content.height = 40;
        check(content.isOver(10, 90) && !content.isOver(10, 110), "A clipped row cannot be clicked outside its viewport");
        content.mouseOver = true;
        view.mouseMoved(200, 200, 10, 90);
        check(!content.mouseOver, "Leaving a view clears descendant hover");

        var workspace = new WAdaptiveContent(); workspace.theme = THEME;
        workspace.availableHeight = 240; workspace.spacing = 8;
        workspace.add(new WWidget() { @Override protected void onCalculateSize() { width = 120; height = 24; }});
        var searchResults = workspace.add(new TestView()).widget(); workspace.results = searchResults;
        double[] resultHeight = {500};
        searchResults.add(new WWidget() { @Override protected void onCalculateSize() { width = 120; height = resultHeight[0]; }});
        workspace.calculateSize();
        check(workspace.height == 240, "Full search results respect the workspace height");
        workspace.calculateSize(); // A second overflowing layout queues a scroll-position update.
        resultHeight[0] = 16; workspace.calculateSize();
        check(workspace.height == 240 && searchResults.height == 208, "Filtering keeps the centered search workspace stationary");
        workspace.calculateWidgetPositions();
        searchResults.animateScroll();
        check(searchResults.movedY == 0, "Filtering a scrollable list cannot push remaining results below a blank gap");
        resultHeight[0] = 0; workspace.calculateSize();
        check(workspace.height == 240, "No-match searches also retain their position");

        TestDropdown dropdown = new TestDropdown();
        dropdown.theme = THEME; dropdown.init(); dropdown.calculateSize();
        dropdown.y = 180; dropdown.calculateWidgetPositions(); dropdown.open();
        check(dropdown.menuY() < dropdown.y && dropdown.menuBottom() <= 200, "Real dropdown layout flips at the screen edge");
        check(dropdown.isFocused(), "An open menu retains input priority");
        dropdown.keyPressed(new KeyEvent(264, 0, 0));
        check(dropdown.get().equals("B"), "Down arrow chooses the next menu value");
        check(dropdown.keyPressed(new KeyEvent(256, 0, 0)) && !dropdown.isFocused(), "Escape closes a menu and releases focus");

        var namedSetting = new com.gaspoweredcake.client33.settings.BoolSetting.Builder().name("allowWaterBucketFall").build();
        check(namedSetting.title.equals("Allow Water Bucket Fall") && namedSetting.name.equals("allowWaterBucketFall"), "Camel-case setting labels are readable without changing saved keys");
        check(CardLayout.columns(599) == 1 && CardLayout.columns(600) == 2 && CardLayout.columns(940) == 3, "Cards adapt at column boundaries");
        check(CardLayout.compact(759) && !CardLayout.compact(760), "Narrow workspaces use the compact controls");
        check(CardLayout.ellipsize("😀😀😀", 5, String::length).equals("😀..."), "Truncated titles preserve complete Unicode characters");
        AtomicInteger opened = new AtomicInteger(), toggled = new AtomicInteger(), starred = new AtomicInteger();
        WModuleCard card = new WModuleCard("Survival Bot", "Gather wood and prepare your survival kit.", "World", 300,
            () -> false, () -> false, opened::incrementAndGet, toggled::incrementAndGet, starred::incrementAndGet);
        card.theme = THEME; card.calculateSize();
        check(card.height <= 64, "Module rows remain compact");
        var body = new MouseButtonEvent(20, 20, new MouseButtonInfo(0, 0));
        var switchClick = new MouseButtonEvent(260, card.height - 22, new MouseButtonInfo(0, 0));
        var starClick = new MouseButtonEvent(215, 20, new MouseButtonInfo(0, 0));
        card.mouseClicked(body, false); card.mouseReleased(body);
        check(opened.get() == 1 && toggled.get() == 0, "Card body opens settings without enabling the module");
        card.mouseClicked(switchClick, false); card.mouseReleased(switchClick);
        check(toggled.get() == 1 && opened.get() == 1, "Card switch exclusively toggles");
        card.mouseClicked(starClick, false); card.mouseReleased(starClick);
        check(starred.get() == 1 && toggled.get() == 1, "Favorite target does not toggle");
        card.mouseClicked(body, false); card.mouseReleased(switchClick);
        check(toggled.get() == 1 && opened.get() == 1 && !card.isFocused(), "Dragging from card body onto its switch cancels activation");
        var right = new MouseButtonEvent(260, card.height - 22, new MouseButtonInfo(1, 0));
        card.mouseClicked(right, false); card.mouseReleased(right);
        check(opened.get() == 2 && toggled.get() == 1, "Right-click opens settings even over the switch");
        card.mouseClicked(switchClick, false); card.mouseReleased(new MouseButtonEvent(400, 300, new MouseButtonInfo(0, 0)));
        check(toggled.get() == 1 && !card.isFocused(), "Releasing outside the card cancels the action");
        var rounded = new RoundedRecorder();
        RoundedRect.draw(rounded, 0, 0, 100, 50, 12, new Color(255,255,255));
        check(Math.abs(rounded.area - (5000 - (4 - Math.PI) * 144)) < 2, "Rounded surface fills its area without overlapping transparent shapes");
        check(rounded.minX >= -0.0001 && rounded.minY >= -0.0001 && rounded.maxX <= 100.0001 && rounded.maxY <= 50.0001, "Rounded vertices stay inside the surface");
        rounded = new RoundedRecorder(); RoundedRect.draw(rounded, 0, 0, 20, 10, 100, new Color(255,255,255));
        check(rounded.area > 170 && rounded.area < 200 && rounded.minY >= -0.0001 && rounded.maxY <= 10.0001, "Large corner radii clamp to the short side");

        Integer[] setting = {3};
        TemporaryOverrides overrides = new TemporaryOverrides();
        overrides.apply(() -> setting[0], value -> setting[0] = value, 1);
        check(setting[0] == 1, "Build scope applies the conservative fall limit");
        overrides.withOriginals(() -> check(setting[0] == 3, "Saving uses original settings while the guard is active"));
        check(setting[0] == 1, "Saving restores the active build override");
        overrides.close(); overrides.close();
        check(setting[0] == 3, "Ending/cancelling the scope restores exactly once");
        overrides.apply(() -> setting[0], value -> setting[0] = value, 1);
        setting[0] = 0;
        overrides.close();
        check(setting[0] == 0, "Cleanup preserves a stricter manual setting changed during building");
        System.out.println("GUI interaction and build settings regressions passed.");
    }

    private static MouseButtonEvent click(int button) { return new MouseButtonEvent(10, 10, new MouseButtonInfo(button, 0)); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class RoundedRecorder implements RoundedRect.Sink {
        double area, minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX, maxY;
        void point(double x, double y) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
        @Override public void quad(double x, double y, double w, double h, Color color) { check(w >= 0 && h >= 0, "No negative rounded rectangles"); area += w * h; point(x, y); point(x + w, y + h); }
        @Override public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
            area += Math.abs((x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1)) / 2;
            point(x1, y1); point(x2, y2); point(x3, y3);
        }
    }

    private static final class TestView extends WView {
        @Override public void init() { maxHeight = 100; }
        @Override protected double spacing() { return 0; }
        double movedY;
        @Override public void moveCells(double x, double y) { movedY += y; }
        void animateScroll() {
            try {
                var animate = WView.class.getDeclaredMethod("updateScroll", double.class);
                animate.setAccessible(true); animate.invoke(this, 1.0 / 60);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        double thumbHeight() { return handleHeight(); }
    }

    private static final class TestDropdown extends WDropdown<String> {
        TestDropdown() { super(new String[]{"A", "B", "C", "D", "E", "F"}, "A"); }
        @Override protected double screenHeight() { return 200; }
        @Override protected WDropdownRoot createRootWidget() {
            return new WDropdownRoot() { @Override public void init() { maxHeight = 100; } };
        }
        @Override protected WDropdownValue createValueWidget() {
            return new WDropdownValue() { @Override protected void onCalculateSize() { width = 50; height = 24; } };
        }
        void open() { onPressed(0); }
        double menuY() { return root.y; }
        double menuBottom() { return root.y + root.height; }
    }

    private static void controlRegressions() {
        AtomicInteger commits = new AtomicInteger();
        var slider = new TestSlider(0.5, 0, 1);
        slider.theme = THEME; slider.width = 200; slider.height = 16;
        slider.actionOnRelease = commits::incrementAndGet;
        slider.hoverHandle();
        slider.onMouseScrolled(1);
        check(slider.get() > 0.5 && commits.get() == 1, "Wheel changes commit release-only settings such as GUI scale");
        slider.mouseOver = true;
        check(!slider.onMouseClicked(new MouseButtonEvent(100, 8, new MouseButtonInfo(1, 0)), false), "Right-click cannot start a slider drag");
        slider.onMouseClicked(new MouseButtonEvent(140, 8, new MouseButtonInfo(0, 0)), false);
        check(!slider.onMouseReleased(new MouseButtonEvent(140, 8, new MouseButtonInfo(1, 0))) && slider.isFocused(), "Unrelated button release preserves a drag");
        slider.onMouseReleased(new MouseButtonEvent(140, 8, new MouseButtonInfo(0, 0)));
        var fixed = new TestSlider(2, 2, 2); fixed.theme = THEME; fixed.width = 16; fixed.height = 16; fixed.mouseOver = true;
        fixed.onMouseClicked(new MouseButtonEvent(8, 8, new MouseButtonInfo(0, 0)), false);
        check(Double.isFinite(fixed.get()) && Double.isFinite(fixed.position()), "Zero-range and zero-travel sliders stay finite");

        boolean[] available = {false};
        var guarded = new WActionButton("Build", () -> available[0], commits::incrementAndGet);
        guarded.theme = THEME; guarded.mouseOver = true;
        int guardedBefore = commits.get();
        check(!guarded.onMouseClicked(click(0), false), "Unavailable build actions cannot capture clicks");
        available[0] = true; guarded.onMouseClicked(click(0), false);
        available[0] = false; guarded.onMouseReleased(click(0));
        check(commits.get() == guardedBefore, "Availability is rechecked when an action is released");
        available[0] = true; guarded.onMouseClicked(click(0), false); guarded.onMouseReleased(click(0));
        check(commits.get() == guardedBefore + 1, "Available action fires exactly once");

        WTextBox text = THEME.textBox("value", "", (_, _) -> true, null);
        text.actionOnUnfocused = commits::incrementAndGet;
        text.setFocused(true);
        int before = commits.get();
        text.keyPressed(new KeyEvent(257, 0, 0));
        check(commits.get() == before + 1 && !text.isFocused(), "Enter commits a field exactly once");
        text.setFocused(true);
        text.actionOnUnfocused = () -> { commits.incrementAndGet(); text.setFocused(false); };
        before = commits.get(); text.setFocused(false);
        check(commits.get() == before + 1, "Focus callbacks can close or reload a screen without recursing");

        WIntEdit integer = THEME.intEdit(4, 1, 10, true); integer.init();
        WTextBox input = (WTextBox) integer.cells.getFirst().widget();
        integer.action = commits::incrementAndGet;
        input.set("99"); input.setFocused(true); input.setFocused(false);
        check(integer.get() == 10 && input.get().equals("10"), "Typed integers clamp and display the value actually applied");
        input.set("invalid"); input.setFocused(true); input.setFocused(false);
        check(integer.get() == 10 && input.get().equals("10"), "Invalid numeric text restores the accepted value");
        WIntEdit limit = THEME.intEdit(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, true); limit.init();
        ((WButton) limit.cells.get(1).widget()).action.run();
        check(limit.get() == Integer.MAX_VALUE, "Incrementing the integer maximum cannot wrap to the minimum");

        WDoubleEdit decimal = THEME.doubleEdit(0.5, 0, 1, 0, 1, 2, true); decimal.init();
        decimal.action = commits::incrementAndGet;
        before = commits.get();
        ((WButton) decimal.cells.get(1).widget()).action.run();
        check(decimal.get() == 1 && commits.get() == before + 1
            && ((WTextBox) decimal.cells.getFirst().widget()).get().equals("1.00"), "Fractional boundary increments update display and setting together");
    }

    private static final class TestSlider extends WSlider {
        TestSlider(double value, double min, double max) { super(value, min, max); }
        void hoverHandle() { handleMouseOver = true; }
        double position() { return valueWidth(); }
    }
    private static final class TestTextBox extends WTextBox {
        TestTextBox(String text, String placeholder, CharFilter filter, Class<? extends Renderer> renderer) { super(text, placeholder, filter, renderer); }
        @Override protected WContainer createCompletionsRootWidget() { throw new UnsupportedOperationException(); }
        @Override protected <T extends WWidget & ICompletionItem> T createCompletionsValueWidth(String completion, boolean selected) { throw new UnsupportedOperationException(); }
    }

    private static final class TestTheme extends GuiTheme {
        TestTheme() { super("Synthetic GUI"); }
        @Override public double textWidth(String text, int length, boolean title) { return length * 8; }
        @Override public double textHeight(boolean title) { return 16; }
        @Override public WWindow window(WWidget icon, String title) { throw new UnsupportedOperationException(); }
        @Override public WLabel label(String text, boolean title, double maxWidth) { throw new UnsupportedOperationException(); }
        @Override public WHorizontalSeparator horizontalSeparator(String text) { throw new UnsupportedOperationException(); }
        @Override public WVerticalSeparator verticalSeparator() { throw new UnsupportedOperationException(); }
        @Override protected WButton button(String text, GuiTexture texture) { return w(new WButton(text, texture) {}); }
        @Override protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) { throw new UnsupportedOperationException(); }
        @Override public WMinus minus() { throw new UnsupportedOperationException(); }
        @Override public WConfirmedMinus confirmedMinus() { throw new UnsupportedOperationException(); }
        @Override public WPlus plus() { throw new UnsupportedOperationException(); }
        @Override public WCheckbox checkbox(boolean checked) { throw new UnsupportedOperationException(); }
        @Override public WSlider slider(double value, double min, double max) { return w(new TestSlider(value, min, max)); }
        @Override public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) { return w(new TestTextBox(text, placeholder, filter, renderer)); }
        @Override public <T> WDropdown<T> dropdown(T[] values, T value) { throw new UnsupportedOperationException(); }
        @Override public WTriangle triangle() { throw new UnsupportedOperationException(); }
        @Override public WTooltip tooltip(String text) { throw new UnsupportedOperationException(); }
        @Override public WView view() { throw new UnsupportedOperationException(); }
        @Override public WSection section(String title, boolean expanded, WWidget headerWidget) { throw new UnsupportedOperationException(); }
        @Override public WAccount account(WidgetScreen screen, Account<?> account) { throw new UnsupportedOperationException(); }
        @Override public WWidget module(Module module, String title) { throw new UnsupportedOperationException(); }
        @Override public WQuad quad(Color color) { throw new UnsupportedOperationException(); }
        @Override public WTopBar topBar() { throw new UnsupportedOperationException(); }
        @Override public WFavorite favorite(boolean checked) { throw new UnsupportedOperationException(); }
        @Override public Color textColor() { throw new UnsupportedOperationException(); }
        @Override public Color textSecondaryColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptTextColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptBraceColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptParenthesisColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptDotColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptCommaColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptOperatorColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptStringColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptNumberColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptKeywordColor() { throw new UnsupportedOperationException(); }
        @Override public Color starscriptAccessedObjectColor() { throw new UnsupportedOperationException(); }
        @Override public TextRenderer textRenderer() { throw new UnsupportedOperationException(); }
        @Override public double scale(double value) { return value; }
        @Override public boolean categoryIcons() { throw new UnsupportedOperationException(); }
        @Override public boolean modulesHelpText() { throw new UnsupportedOperationException(); }
        @Override public boolean hideHUD() { throw new UnsupportedOperationException(); }
    }
}
