package com.gaspoweredcake.client33.gui.utils;

/** Presentation names without loading Minecraft's graphics utilities. */
public final class DisplayNames {
    private DisplayNames() {}
    public static String title(String key) {
        return java.util.Arrays.stream(key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").split("-"))
            .map(org.apache.commons.lang3.StringUtils::capitalize).collect(java.util.stream.Collectors.joining(" "));
    }
}
