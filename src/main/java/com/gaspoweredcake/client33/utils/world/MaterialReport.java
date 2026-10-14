package com.gaspoweredcake.client33.utils.world;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Presentation of source-schematic requirements, independent of game and widget lifecycles. */
public final class MaterialReport {
    private MaterialReport() {}
    public record Entry(String id, String name, long required, long inventory, int stackSize, boolean manual) {
        public long missing() { return manual ? 0 : Math.max(0, required - inventory); }
        public long stacks() { return manual ? 0 : SchematicMaterials.stacks(required, stackSize); }
    }
    public enum Sort { Name, Missing, Required }
    public static List<Entry> filter(List<Entry> entries, String query, boolean shortagesOnly, Sort sort) {
        String search = normalize(query);
        Comparator<Entry> names = Comparator.comparing((Entry entry) -> entry.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::id);
        Comparator<Entry> order = switch (sort) {
            case Name -> names;
            case Missing -> Comparator.comparingLong(Entry::missing).reversed().thenComparing(names);
            case Required -> Comparator.comparingLong(Entry::required).reversed().thenComparing(names);
        };
        return entries.stream().filter(entry -> !shortagesOnly || entry.manual() || entry.missing() > 0)
            .filter(entry -> normalize(entry.name()).contains(search) || normalize(entry.id()).contains(search))
            .sorted(order).toList();
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", ""); }
}
