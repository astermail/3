/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.widgets.containers;

import com.gaspoweredcake.client33.gui.renderer.GuiRenderer;
import com.gaspoweredcake.client33.gui.utils.Cell;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.List;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;

public abstract class WContainer extends WWidget {
    public final List<Cell<?>> cells = new ArrayList<>();

    public <T extends WWidget> Cell<T> add(T widget) {
        widget.parent = this;
        widget.theme = theme;

        Cell<T> cell = new Cell<>(widget).centerY();
        cells.add(cell);

        widget.init();
        invalidate();

        return cell;
    }

    public void clear() {
        if (!cells.isEmpty()) {
            cells.clear();
            invalidate();
        }
    }

    public void remove(Cell<?> cell) {
        if (cells.remove(cell)) invalidate();
    }

    @Override
    public void move(double deltaX, double deltaY) {
        super.move(deltaX, deltaY);
        for (Cell<?> cell : cells) cell.move(deltaX, deltaY);
    }

    public void moveCells(double deltaX, double deltaY) {
        for (Cell<?> cell : cells) {
            cell.move(deltaX, deltaY);

            MouseHandler mouse = mc.mouseHandler;
            cell.widget().mouseMoved(mouse.xpos(), mouse.ypos(), mouse.xpos(), mouse.ypos());
        }
    }

    @Override
    public boolean isFocused() {
        if (focused) return true;

        for (Cell<?> cell : cells) {
            if (cell.widget().isFocused())
                return true;
        }

        return false;
    }

    // Layout

    @Override
    public void calculateSize() {
        for (Cell<?> cell : cells) cell.widget().calculateSize();
        super.calculateSize();
    }

    @Override
    protected void onCalculateSize() {
        width = 0;
        height = 0;

        for (Cell<?> cell : cells) {
            width = Math.max(width, cell.padLeft() + cell.widget().width + cell.padRight());
            height = Math.max(height, cell.padTop() + cell.widget().height + cell.padBottom());
        }
    }

    @Override
    public void calculateWidgetPositions() {
        super.calculateWidgetPositions();
        for (Cell<?> cell : cells) cell.widget().calculateWidgetPositions();
    }

    @Override
    protected void onCalculateWidgetPositions() {
        for (Cell<?> cell : cells) {
            cell.x = x + cell.padLeft();
            cell.y = y + cell.padTop();

            cell.width = width - cell.padLeft() - cell.padRight();
            cell.height = height - cell.padTop() - cell.padBottom();

            cell.alignWidget();
        }
    }

    // Rendering

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (super.render(renderer, mouseX, mouseY, delta)) return true;

        WView view = getView();
        double windowHeight = getWindowHeight();

        for (Cell<?> cell : cells) {
            WWidget widget = cell.widget();

            if (widget.y > windowHeight) continue;
            if (widget.y + widget.height <= 0) continue;

            if (shouldRenderWidget(widget, view)) renderWidget(widget, renderer, mouseX, mouseY, delta);
        }

        return false;
    }

    protected void renderWidget(WWidget widget, GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        widget.render(renderer, mouseX, mouseY, delta);
    }

    private boolean shouldRenderWidget(WWidget widget, WView view) {
        if (view == null) return true;
        if (!view.isWidgetInView(widget)) return false;

        if (widget.mouseOver && !view.mouseOver) {
            widget.mouseOver = false;
        }

        return true;
    }

    // Events

    protected boolean propagateEvents(WWidget widget) {
        return true;
    }

    // Focused descendants include open menus and captured drags. Route them before underlying controls.
    private boolean dispatchEvent(Predicate<WWidget> event) {
        List<WWidget> widgets = cells.stream().<WWidget>map(Cell::widget).sorted((left, right) ->
            Boolean.compare(right.isFocused(), left.isFocused())).toList();
        for (WWidget widget : widgets) {
            if (widget.visible && propagateEvents(widget) && event.test(widget)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return dispatchEvent(widget -> widget.mouseClicked(click, doubled)) || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return dispatchEvent(widget -> widget.mouseReleased(click)) || super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        super.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
        for (Cell<?> cell : List.copyOf(cells)) {
            WWidget widget = cell.widget();
            // Also clear stale hover when the pointer leaves a view or a row is clipped.
            if (widget.visible && (propagateEvents(widget) || widget.mouseOver)) {
                widget.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double amount) {
        return dispatchEvent(widget -> widget.mouseScrolled(amount)) || super.mouseScrolled(amount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return dispatchEvent(widget -> widget.keyPressed(input)) || onKeyPressed(input);
    }

    @Override
    public boolean keyRepeated(KeyEvent input) {
        return dispatchEvent(widget -> widget.keyRepeated(input)) || onKeyRepeated(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return dispatchEvent(widget -> widget.charTyped(input)) || super.charTyped(input);
    }
}
