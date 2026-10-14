/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.gaspoweredcake.client33.mixininterface.IGpuDevice;

import java.util.ArrayList;
import java.util.List;

import static com.gaspoweredcake.client33.utils.Utils.getWindowHeight;

public class Scissor {
    public int x, y;
    public int width, height;

    public final List<Runnable> postTasks = new ArrayList<>();

    public Scissor set(double x, double y, double width, double height) {
        if (width < 0) width = 0;
        if (height < 0) height = 0;

        this.x = (int) Math.round(x);
        this.y = (int) Math.round(y);
        this.width = (int) Math.round(width);
        this.height = (int) Math.round(height);

        postTasks.clear();

        return this;
    }

    /** Intersect both edges before rounding; moving the left edge must also shrink the width. */
    public Scissor intersect(double x, double y, double width, double height, Scissor parent) {
        double right = x + Math.max(0, width), bottom = y + Math.max(0, height);
        if (parent != null) {
            x = Math.max(x, parent.x);
            y = Math.max(y, parent.y);
            right = Math.min(right, parent.x + parent.width);
            bottom = Math.min(bottom, parent.y + parent.height);
        }
        return set(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    public void push() {
        ((IGpuDevice) RenderSystem.getDevice()).client33$pushScissor(x, getWindowHeight() - y - height, width, height);
    }

    public void pop() {
        ((IGpuDevice) RenderSystem.getDevice()).client33$popScissor();
    }
}
