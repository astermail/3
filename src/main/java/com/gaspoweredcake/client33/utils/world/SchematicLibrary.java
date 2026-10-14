package com.gaspoweredcake.client33.utils.world;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Library presentation without parsing schematic payloads or touching the render thread. */
public final class SchematicLibrary {
    private SchematicLibrary() {}

    public record Entry(String name, long bytes, long modifiedMillis) {
        public String format() {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "Unknown" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
        }
        public String sizeText() {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024));
        }
    }

    public enum Sort { Name, Newest, Largest }

    public static List<Entry> filter(List<Entry> entries, String query, Sort sort) {
        String search = normalize(query);
        Comparator<Entry> names = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name);
        Comparator<Entry> order = switch (sort) {
            case Name -> names;
            case Newest -> Comparator.comparingLong(Entry::modifiedMillis).reversed().thenComparing(names);
            case Largest -> Comparator.comparingLong(Entry::bytes).reversed().thenComparing(names);
        };
        return entries.stream().filter(entry -> normalize(entry.name()).contains(search)).sorted(order).toList();
    }

    private static String normalize(String text) { return text.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", ""); }
}
