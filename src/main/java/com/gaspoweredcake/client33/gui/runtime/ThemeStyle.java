package com.gaspoweredcake.client33.gui.runtime;

import com.google.gson.*;
import com.gaspoweredcake.client33.gui.themes.client33.Client33GuiTheme;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import java.nio.file.*;
import java.io.IOException;

/** Small editable style file; validate all values before changing the live theme. */
public record ThemeStyle(double scale, double fontScale, double rounding, boolean gradient,
                         SettingColor accent, SettingColor gradientEnd, SettingColor border, SettingColor surface) {
    public static ThemeStyle parse(String json) {
        var object = JsonParser.parseString(json).getAsJsonObject();
        return new ThemeStyle(number(object, "scale", .75, 4), number(object, "fontScale", .75, 1.5), number(object, "rounding", 0, 16), object.get("gradient").getAsBoolean(),
            color(object, "accent"), color(object, "gradientEnd"), color(object, "border"), color(object, "surface"));
    }
    private static double number(JsonObject object, String key, double min, double max) {
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key + " (" + min + "–" + max + ").");
        return value;
    }
    private static SettingColor color(JsonObject object, String key) {
        var array = object.getAsJsonArray(key);
        if (array.size() != 4) throw new IllegalArgumentException(key + " needs [r,g,b,a].");
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) {
            double value = array.get(i).getAsDouble();
            if (!Double.isFinite(value) || value < 0 || value > 255 || value != Math.rint(value)) throw new IllegalArgumentException("Invalid " + key + " channel.");
            c[i] = (int) value;
        }
        return new SettingColor(c[0], c[1], c[2], c[3]);
    }
    public void apply(Client33GuiTheme theme) {
        theme.appearance.set(Client33GuiTheme.Appearance.Custom);
        theme.cornerRadius.set(rounding); theme.accentGradient.set(gradient);
        theme.accentColor.set(accent); theme.gradientEnd.set(gradientEnd);
        theme.outlineColor.get().set(border); theme.backgroundColor.get().set(surface);
        theme.fontScale.set(fontScale); theme.scale.set(scale);
    }
    public static void export(Path path, Client33GuiTheme theme) throws IOException {
        var object = new JsonObject();
        object.addProperty("scale", theme.scale.get()); object.addProperty("fontScale", theme.fontScale.get());
        object.addProperty("rounding", theme.cornerRadius.get()); object.addProperty("gradient", theme.accentGradient.get());
        put(object, "accent", theme.accent()); put(object, "gradientEnd", theme.gradientEnd.get());
        put(object, "border", theme.outline(false, false)); put(object, "surface", theme.surface());
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".theme-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(object));
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void put(JsonObject json, String name, com.gaspoweredcake.client33.utils.render.color.Color color) {
        var array = new JsonArray(); array.add(color.r); array.add(color.g); array.add(color.b); array.add(color.a); json.add(name, array);
    }
}
