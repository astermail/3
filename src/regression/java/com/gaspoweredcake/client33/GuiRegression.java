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

        TestDropdown dropdown = new TestDropdown();
        dropdown.theme = THEME; dropdown.init(); dropdown.calculateSize();
        dropdown.y = 180; dropdown.calculateWidgetPositions(); dropdown.open();
        check(dropdown.menuY() < dropdown.y && dropdown.menuBottom() <= 200, "Real dropdown layout flips at the screen edge");
        check(dropdown.isFocused(), "An open menu retains input priority");
        dropdown.keyPressed(new KeyEvent(264, 0, 0));
        check(dropdown.get().equals("B"), "Down arrow chooses the next menu value");
        check(dropdown.keyPressed(new KeyEvent(256, 0, 0)) && !dropdown.isFocused(), "Escape closes a menu and releases focus");

        check(CardLayout.columns(599) == 1 && CardLayout.columns(600) == 2 && CardLayout.columns(940) == 3, "Cards adapt at column boundaries");
        check(CardLayout.compact(759) && !CardLayout.compact(760), "Narrow workspaces use the compact controls");
        check(CardLayout.ellipsize("😀😀😀", 5, String::length).equals("😀..."), "Truncated titles preserve complete Unicode characters");
        AtomicInteger opened = new AtomicInteger(), toggled = new AtomicInteger(), starred = new AtomicInteger();
        WModuleCard card = new WModuleCard("Survival Bot", "Gather wood and prepare your survival kit.", "World", 300,
            () -> false, () -> false, opened::incrementAndGet, toggled::incrementAndGet, starred::incrementAndGet);
        card.theme = THEME; card.calculateSize();
        var body = new MouseButtonEvent(20, 55, new MouseButtonInfo(0, 0));
        var switchClick = new MouseButtonEvent(260, card.height - 22, new MouseButtonInfo(0, 0));
        var starClick = new MouseButtonEvent(278, 20, new MouseButtonInfo(0, 0));
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
        @Override public void moveCells(double x, double y) { }
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

    private static final class TestTheme extends GuiTheme {
        TestTheme() { super("Synthetic GUI"); }
        @Override public double textWidth(String text, int length, boolean title) { return length * 8; }
        @Override public double textHeight(boolean title) { return 16; }
        @Override public WWindow window(WWidget icon, String title) { throw new UnsupportedOperationException(); }
        @Override public WLabel label(String text, boolean title, double maxWidth) { throw new UnsupportedOperationException(); }
        @Override public WHorizontalSeparator horizontalSeparator(String text) { throw new UnsupportedOperationException(); }
        @Override public WVerticalSeparator verticalSeparator() { throw new UnsupportedOperationException(); }
        @Override protected WButton button(String text, GuiTexture texture) { throw new UnsupportedOperationException(); }
        @Override protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) { throw new UnsupportedOperationException(); }
        @Override public WMinus minus() { throw new UnsupportedOperationException(); }
        @Override public WConfirmedMinus confirmedMinus() { throw new UnsupportedOperationException(); }
        @Override public WPlus plus() { throw new UnsupportedOperationException(); }
        @Override public WCheckbox checkbox(boolean checked) { throw new UnsupportedOperationException(); }
        @Override public WSlider slider(double value, double min, double max) { throw new UnsupportedOperationException(); }
        @Override public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) { throw new UnsupportedOperationException(); }
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
