/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.gui.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class TextWrapping {
    private TextWrapping() {}

    /** Wrap paragraphs and split long identifiers at Unicode code point boundaries. */
    public static List<String> wrap(String text, double maxWidth, ToDoubleFunction<String> width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n", -1)) {
            if (maxWidth <= 0) { lines.add(paragraph); continue; }
            String line = "";
            for (String word : paragraph.strip().split("\\s+")) {
                if (word.isEmpty()) continue;
                String combined = line.isEmpty() ? word : line + " " + word;
                if (width.applyAsDouble(combined) <= maxWidth) { line = combined; continue; }
                if (!line.isEmpty()) { lines.add(line); line = ""; }
                for (int offset = 0; offset < word.length();) {
                    int next = offset + Character.charCount(word.codePointAt(offset));
                    String glyph = word.substring(offset, next);
                    if (!line.isEmpty() && width.applyAsDouble(line + glyph) > maxWidth) {
                        lines.add(line); line = "";
                    }
                    line += glyph;
                    offset = next;
                }
            }
            lines.add(line);
        }
        return lines;
    }
}
