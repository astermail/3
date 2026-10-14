/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui;

import com.mojang.blaze3d.platform.MacosUtil;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.gui.renderer.GuiDebugRenderer;
import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.tabs.TabScreen;
import com.gaspoweredcake.client33.gui.utils.Cell;
import com.gaspoweredcake.client33.gui.widgets.WRoot;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WContainer;
import com.gaspoweredcake.client33.gui.widgets.input.WTextBox;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.CursorStyle;
import com.gaspoweredcake.client33.utils.misc.input.Input;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;
import static com.gaspoweredcake.client33.utils.Utils.getWindowWidth;
import static com.mojang.blaze3d.platform.InputConstants.*;

public abstract class WidgetScreen extends Screen {
    private static final GuiRenderer RENDERER = new GuiRenderer();
    private static final GuiDebugRenderer DEBUG_RENDERER = new GuiDebugRenderer();

    public Runnable taskAfterRender;
    protected Runnable enterAction;

    public Screen parent;
    private final WContainer root;

    protected final GuiTheme theme;

    public boolean locked, lockedAllowClose;
    private boolean closed;
    private boolean onClose;
    private boolean debug;

    private boolean closing;

    private double lastMouseX, lastMouseY;

    public double animProgress;

    private List<Runnable> onClosed;

    protected boolean firstInit = true;

    public WidgetScreen(GuiTheme theme, String title) {
        super(Component.literal(title));

        this.parent = mc.gui.screen();
        this.root = new WFullScreenRoot();
        this.theme = theme;

        root.theme = theme;

        if (parent != null) {
            animProgress = 1;

            if (this instanceof TabScreen && parent instanceof TabScreen) {
                parent = ((TabScreen) parent).parent;
            }
        }
    }

    public <W extends WWidget> Cell<W> add(W widget) {
        return root.add(widget);
    }

    public void clear() {
        root.clear();
    }

    public void invalidate() {
        root.invalidate();
    }

    @Override
    protected void init() {
        Client33.EVENT_BUS.subscribe(this);

        closed = false;

        if (firstInit) {
            firstInit = false;
            initWidgets();
        }
    }

    public abstract void initWidgets();

    public void reload() {
        clear();
        initWidgets();
    }

    public void onClosed(Runnable action) {
        if (onClosed == null) onClosed = new ArrayList<>(2);
        onClosed.add(action);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent click, boolean doubled) {
        if (locked) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        double s = mc.getWindow().getGuiScale();

        mouseX *= s;
        mouseY *= s;

        // Opening or rebuilding a screen can leave hover state stale without pointer motion.
        root.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);

        // Unfocus all text boxes that are not under the mouse cursor
        loopWidgets(root, widget -> {
            if (widget instanceof WTextBox textBox && textBox.isFocused() && !textBox.mouseOver) {
                textBox.setFocused(false);
            }
        });

        return root.mouseClicked(new MouseButtonEvent(mouseX, mouseY, click.buttonInfo()), doubled);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent click) {
        if (locked) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        double s = mc.getWindow().getGuiScale();

        mouseX *= s;
        mouseY *= s;

        root.mouseMoved(mouseX, mouseY, mouseX, mouseY);
        if (debug && click.button() == MOUSE_BUTTON_RIGHT)
            DEBUG_RENDERER.mouseReleased(root, new MouseButtonEvent(mouseX, mouseY, click.buttonInfo()), 0);

        return root.mouseReleased(new MouseButtonEvent(mouseX, mouseY, click.buttonInfo()));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (locked) return;

        double s = mc.getWindow().getGuiScale();
        mouseX *= s;
        mouseY *= s;

        root.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (locked) return false;

        double scale = mc.getWindow().getGuiScale();
        root.mouseMoved(mouseX * scale, mouseY * scale, mouseX * scale, mouseY * scale);
        return root.mouseScrolled(verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent input) {
        if (locked) return false;

        if ((input.modifiers() == MOD_CONTROL || input.modifiers() == MOD_SUPER) && input.key() == KEY_9) {
            debug = !debug;
            return true;
        }

        if ((input.key() == KEY_RETURN || input.key() == KEY_NUMPADENTER) && enterAction != null) {
            enterAction.run();
            return true;
        }

        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent input) {
        if (locked) return false;

        boolean shouldReturn = root.keyPressed(input) || super.keyPressed(input);
        if (shouldReturn) return true;

        // Select next text box if TAB was pressed
        if (input.key() == KEY_TAB) {
            AtomicReference<WTextBox> firstTextBox = new AtomicReference<>(null);
            AtomicBoolean done = new AtomicBoolean(false);
            AtomicBoolean foundFocused = new AtomicBoolean(false);

            loopWidgets(root, wWidget -> {
                if (done.get() || !(wWidget instanceof WTextBox textBox)) return;

                if (foundFocused.get()) {
                    textBox.setFocused(true);
                    textBox.setCursorMax();

                    done.set(true);
                } else {
                    if (textBox.isFocused()) {
                        textBox.setFocused(false);
                        foundFocused.set(true);
                    }
                }

                if (firstTextBox.get() == null) firstTextBox.set(textBox);
            });

            if (!done.get() && firstTextBox.get() != null) {
                firstTextBox.get().setFocused(true);
                firstTextBox.get().setCursorMax();
            }

            return true;
        }

        boolean control = MacosUtil.IS_MACOS ? input.modifiers() == MOD_SUPER : input.modifiers() == MOD_CONTROL;

        return (control && input.key() == KEY_C && toClipboard())
            || (control && input.key() == KEY_V && fromClipboard());
    }

    public void keyRepeated(KeyEvent input) {
        if (locked) return;

        root.keyRepeated(input);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent input) {
        if (locked) return false;

        return root.charTyped(input);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, deltaTicks);
        }
    }

    public void renderCustom(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long renderStarted = System.nanoTime();
        int s = mc.getWindow().getGuiScale();
        mouseX *= s;
        mouseY *= s;

        animProgress += (delta / 20 * 14) * (closing ? -1 : 1);
        animProgress = Mth.clamp(animProgress, 0, 1);

        if (closing && (animProgress == 0 || parent != null)) {
            closeInternal();
        }

        GuiKeyEvents.canUseKeys = true;

        // Apply projection without scaling
        Utils.unscaledProjection();

        onRenderBefore(graphics, mouseX, mouseY, delta);

        RENDERER.theme = theme;
        theme.beforeRender();

        RENDERER.begin(graphics);
        RENDERER.setAlpha(animProgress);
        theme.renderBackground(RENDERER, Utils.getWindowWidth(), Utils.getWindowHeight());
        RENDERER.endRender();
        RENDERER.beginRender();
        root.render(RENDERER, mouseX, mouseY, delta / 20);
        RENDERER.setAlpha(1);
        RENDERER.end();

        boolean tooltip = RENDERER.renderTooltip(graphics, mouseX, mouseY, delta / 20);

        if (debug) {
            DEBUG_RENDERER.render(root);
            if (tooltip) DEBUG_RENDERER.render(RENDERER.tooltipWidget);
        }

        Utils.scaledProjection();

        runAfterRenderTasks();
        com.gaspoweredcake.client33.gui.runtime.MenuMetrics.record(System.nanoTime() - renderStarted);
    }

    protected void runAfterRenderTasks() {
        if (taskAfterRender != null) {
            taskAfterRender.run();
            taskAfterRender = null;
        }
    }

    protected void onRenderBefore(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        root.invalidate();
    }

    @Override
    public void onClose() {
        if (!locked || lockedAllowClose) {
            closing = true;
        }
    }

    @Override
    public void removed() {
        if (!closed || lockedAllowClose) {
            closed = true;
            onClosed();

            Input.setCursorStyle(CursorStyle.Default);

            loopWidgets(root, widget -> {
                if (widget instanceof WTextBox textBox && textBox.isFocused()) textBox.setFocused(false);
            });

            Client33.EVENT_BUS.unsubscribe(this);
            GuiKeyEvents.canUseKeys = true;

            if (onClosed != null) {
                for (Runnable action : onClosed) action.run();
            }

            if (onClose) {
                double restoreX = mc.mouseHandler.xpos();
                double restoreY = mc.mouseHandler.ypos();

                taskAfterRender = () -> {
                    locked = true;
                    mc.gui.setScreen(parent);

                    // Restore mouse position to where it was when the screen was closed
                    if (parent != null) {
                        grabOrReleaseMouse(mc.getWindow(), CURSOR_NORMAL, restoreX, restoreY);
                    }
                };
            }
        }
    }

    private void closeInternal() {
        boolean preOnClose = onClose;
        onClose = true;

        super.onClose();
        removed();

        onClose = preOnClose;
    }

    private void loopWidgets(WWidget widget, Consumer<WWidget> action) {
        action.accept(widget);

        if (widget instanceof WContainer wContainer) {
            for (Cell<?> cell : java.util.List.copyOf(wContainer.cells)) loopWidgets(cell.widget(), action);
        }
    }

    protected void onClosed() {
    }

    public boolean toClipboard() {
        return false;
    }

    public boolean fromClipboard() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !locked || lockedAllowClose;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class WFullScreenRoot extends WContainer implements WRoot {
        private boolean valid;

        @Override
        public void invalidate() {
            valid = false;
        }

        @Override
        protected void onCalculateSize() {
            width = getWindowWidth();
            height = getWindowHeight();
        }

        @Override
        protected void onCalculateWidgetPositions() {
            for (Cell<?> cell : cells) {
                cell.x = 0;
                cell.y = 0;

                cell.width = width;
                cell.height = height;

                cell.alignWidget();
            }
        }

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!valid) {
                calculateSize();
                calculateWidgetPositions();

                valid = true;
                mouseMoved(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
            }

            return super.render(renderer, mouseX, mouseY, delta);
        }
    }
}
