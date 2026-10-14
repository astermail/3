/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.widgets.pressable;

import com.gaspoweredcake.client33.gui.widgets.WWidget;
import net.minecraft.client.input.MouseButtonEvent;

import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;

public abstract class WPressable extends WWidget {
    public Runnable action;

    protected boolean pressed;
    private int pressedButton = -1;

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!mouseOver || (click.button() != MOUSE_BUTTON_LEFT && click.button() != MOUSE_BUTTON_RIGHT)) return false;
        pressed = true;
        pressedButton = click.button();
        return true;
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        if (!pressed || click.button() != pressedButton) return false;
        pressed = false;
        pressedButton = -1;
        if (mouseOver) {
            onPressed(click.button());
            if (action != null) action.run();
        }
        return true;
    }

    @Override
    public boolean isFocused() {
        return pressed || super.isFocused();
    }

    protected void onPressed(int button) {
    }
}
