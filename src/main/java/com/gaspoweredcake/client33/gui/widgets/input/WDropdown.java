/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.widgets.input;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.utils.Cell;
import com.gaspoweredcake.client33.gui.utils.PopupBounds;
import com.gaspoweredcake.client33.gui.widgets.WRoot;
import com.gaspoweredcake.client33.gui.widgets.containers.WView;
import com.gaspoweredcake.client33.gui.widgets.pressable.WPressable;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;

import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;
import static com.mojang.blaze3d.platform.InputConstants.*;

public abstract class WDropdown<T> extends WPressable {
    public Runnable action;

    protected T[] values;
    protected T value;

    protected double maxValueWidth;

    protected WDropdownRoot root;
    protected boolean expanded;
    protected double animProgress;
    private double contentHeight;

    public WDropdown(T[] values, T value) {
        this.values = values;

        set(value);
    }

    @Override
    public void init() {
        root = createRootWidget();
        root.theme = theme;
        root.spacing = 0;
        root.init();

        for (int i = 0; i < values.length; i++) {
            WDropdownValue widget = createValueWidget();
            widget.theme = theme;
            widget.value = values[i];

            Cell<?> cell = root.add(widget).padHorizontal(2).expandWidgetX();
            if (i >= values.length - 1) cell.padBottom(2);
        }
    }

    protected abstract WDropdownRoot createRootWidget();

    protected abstract WDropdownValue createValueWidget();

    @Override
    protected void onCalculateSize() {
        double pad = pad();

        maxValueWidth = 0;
        for (T value : values) {
            double valueWidth = theme.textWidth(value.toString());
            maxValueWidth = Math.max(maxValueWidth, valueWidth);
        }

        root.maxHeight = Double.MAX_VALUE;
        root.calculateSize();
        contentHeight = root.height;

        width = pad + maxValueWidth + pad + theme.textHeight() + pad;
        height = pad + theme.textHeight() + pad;

        root.width = width;
    }

    @Override
    protected void onCalculateWidgetPositions() {
        super.onCalculateWidgetPositions();

        PopupBounds bounds = PopupBounds.fit(y, height, contentHeight, screenHeight(), theme.scale(4));
        root.maxHeight = bounds.height();
        root.calculateSize();
        root.width = width;
        root.x = x;
        root.y = bounds.y();
        root.calculateWidgetPositions();
    }

    @Override
    protected void onPressed(int button) {
        if (button == MOUSE_BUTTON_LEFT) setExpanded(!expanded);
    }

    protected double screenHeight() { return getWindowHeight(); }

    private void setExpanded(boolean value) {
        expanded = value;
        setFocused(value);
        if (!value) root.setFocused(false);
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    @Override
    public void move(double deltaX, double deltaY) {
        super.move(deltaX, deltaY);

        root.move(deltaX, deltaY);
        WView view = getView();
        if (expanded && view != null && !view.isWidgetInView(this)) setExpanded(false);
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean render = super.render(renderer, mouseX, mouseY, delta);

        animProgress += (expanded ? 1 : -1) * delta * 14;
        animProgress = Mth.clamp(animProgress, 0, 1);

        WView view = getView();
        boolean rootInView = view == null || view.isWidgetInView(this);

        if (!render && animProgress > 0 && rootInView) {
            double dropdownY = root.y;
            double scissorHeight = root.height * animProgress;

            renderer.absolutePost(() -> {
                renderer.scissorStart(x, dropdownY, width, scissorHeight);
                root.render(renderer, mouseX, mouseY, delta);
                renderer.scissorEnd();
            });
        }

        if (expanded && root.mouseOver) theme.disableHoverColor = true;

        return render;
    }

    // Events

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        boolean used = false;
        if (expanded && !mouseOver && !root.mouseOver) setExpanded(false);

        if (super.onMouseClicked(click, doubled)) used = true;
        if (expanded && root.mouseClicked(click, doubled)) used = true;

        return used;
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        if (super.onMouseReleased(click)) return true;

        return expanded && root.mouseReleased(click);
    }

    @Override
    public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        super.onMouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);

        if (expanded) root.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        if (super.onMouseScrolled(amount)) return true;

        if (expanded) {
            return root.mouseScrolled(amount);
        }

        return false;
    }

    @Override
    public boolean onKeyPressed(KeyEvent input) {
        if (expanded && input.key() == KEY_ESCAPE) {
            setExpanded(false);
            return true;
        }
        if (expanded && values.length > 0 && (input.key() == KEY_UP || input.key() == KEY_DOWN)) {
            int index = java.util.Arrays.asList(values).indexOf(value);
            int next = Math.floorMod(index + (input.key() == KEY_DOWN ? 1 : -1), values.length);
            set(values[next]);
            if (action != null) action.run();
            return true;
        }
        if (expanded && (input.key() == KEY_RETURN || input.key() == KEY_NUMPADENTER)) {
            setExpanded(false);
            return true;
        }
        if (super.onKeyPressed(input)) return true;
        return expanded && root.keyPressed(input);
    }

    @Override
    public boolean onKeyRepeated(KeyEvent input) {
        if (expanded && (input.key() == KEY_UP || input.key() == KEY_DOWN)) return onKeyPressed(input);
        if (super.onKeyRepeated(input)) return true;
        return expanded && root.keyRepeated(input);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (super.onCharTyped(input)) return true;

        return expanded && root.charTyped(input);
    }

    // Widgets

    protected abstract static class WDropdownRoot extends WView implements WRoot {
        @Override
        public void invalidate() {
        }
    }

    protected abstract class WDropdownValue extends WPressable {
        protected T value;

        @Override
        protected void onPressed(int button) {
            boolean isNew = !WDropdown.this.value.equals(value);

            WDropdown.this.value = value;
            setExpanded(false);

            if (isNew && WDropdown.this.action != null) WDropdown.this.action.run();
        }
    }
}
