/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.gui.utils;

/** Keep menus beside their control and inside the screen, using a scroll viewport when necessary. */
public record PopupBounds(double y, double height) {
    public static PopupBounds fit(double controlY, double controlHeight, double contentHeight, double screenHeight, double margin) {
        double below = Math.max(0, screenHeight - margin - controlY - controlHeight);
        double above = Math.max(0, controlY - margin);
        boolean upwards = contentHeight > below && above > below;
        double height = Math.min(contentHeight, upwards ? above : below);
        return new PopupBounds(upwards ? controlY - height : controlY + controlHeight, height);
    }
}
