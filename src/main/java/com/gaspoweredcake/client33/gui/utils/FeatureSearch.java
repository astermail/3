package com.gaspoweredcake.client33.gui.utils;

import java.util.Locale;

/** Common matching for names, aliases and setting text, independent of menu filters. */
public final class FeatureSearch {
    private FeatureSearch() {}

    public static String compact(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static boolean matches(String query, String... fields) {
        String normalized = compact(query);
        if (normalized.isEmpty()) return true;
        StringBuilder document = new StringBuilder();
        for (String field : fields) {
            String value = field.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
            if (compact(field).equals(normalized) || value.contains(normalized)) return true;
            document.append(value).append(' ');
        }
        String corpus = document.toString();
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty() && !corpus.contains(token)) return false;
        }
        return true;
    }

    public static int rank(String query, String name, String title, String[] aliases) {
        String needle = compact(query);
        String normalizedName = compact(name), normalizedTitle = compact(title);
        if (normalizedName.equals(needle) || normalizedTitle.equals(needle)) return 0;
        for (String alias : aliases) if (compact(alias).equals(needle)) return 0;
        if (normalizedName.startsWith(needle) || normalizedTitle.startsWith(needle)) return 1;
        if (normalizedName.contains(needle) || normalizedTitle.contains(needle)) return 2;
        return 3;
    }
}
