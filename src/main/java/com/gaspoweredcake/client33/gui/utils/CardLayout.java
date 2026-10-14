package com.gaspoweredcake.client33.gui.utils;

/** Logical (unscaled) workspace geometry and independent card action targets. */
public final class CardLayout {
    private CardLayout() {}
    public enum Action { Settings, Toggle, Favorite }
    public static boolean compact(double viewportWidth) { return viewportWidth < 760; }
    public static int columns(double contentWidth) { return contentWidth >= 940 ? 3 : contentWidth >= 600 ? 2 : 1; }
    public static Action action(double x, double y, double width, double height) {
        if (x >= width - 42 && y <= 40) return Action.Favorite;
        if (x >= width - 92 && y >= height - 44) return Action.Toggle;
        return Action.Settings;
    }
    public static String ellipsize(String text, double width, java.util.function.ToDoubleFunction<String> measure) {
        if (measure.applyAsDouble(text) <= width) return text;
        String suffix = "...";
        if (measure.applyAsDouble(suffix) > width) return "";
        int end = text.length();
        while (end > 0 && measure.applyAsDouble(text.substring(0, end) + suffix) > width) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + suffix;
    }
}
