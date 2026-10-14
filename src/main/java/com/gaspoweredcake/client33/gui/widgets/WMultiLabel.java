/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.gui.widgets;

import com.gaspoweredcake.client33.gui.utils.TextWrapping;

import java.util.ArrayList;
import java.util.List;

public abstract class WMultiLabel extends WLabel {
    protected List<String> lines = new ArrayList<>(2);

    protected double maxWidth;

    public WMultiLabel(String text, boolean title, double maxWidth) {
        super(text, title);

        this.maxWidth = maxWidth;
    }

    @Override
    protected void onCalculateSize() {
        lines = TextWrapping.wrap(text, theme.scale(maxWidth), line -> theme.textWidth(line, line.length(), title));
        width = lines.stream().mapToDouble(line -> theme.textWidth(line, line.length(), title)).max().orElse(0);
        height = theme.textHeight(title) * lines.size();
    }

    @Override
    public void set(String text) {
        if (!text.equals(this.text)) invalidate();

        this.text = text;
    }
}
