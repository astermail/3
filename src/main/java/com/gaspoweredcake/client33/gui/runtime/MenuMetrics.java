package com.gaspoweredcake.client33.gui.runtime;

import java.util.Arrays;

/** Bounded, allocation-free sampling; summaries allocate only when explicitly requested. */
public final class MenuMetrics {
    private static final long[] samples = new long[256];
    private static int count, cursor;
    private MenuMetrics() {}
    public static void record(long nanos) { samples[cursor] = Math.max(0, nanos); cursor = (cursor + 1) % samples.length; count = Math.min(count + 1, samples.length); }
    public static String summary() {
        if (count == 0) return "No menu frames recorded.";
        long[] values = Arrays.copyOf(samples, count); Arrays.sort(values);
        double mean = Arrays.stream(values).average().orElse(0) / 1_000_000;
        return "Menu CPU submission: %.2f ms mean / %.2f ms p95 / %.2f ms max (%d frames). GPU time excluded.".formatted(mean, values[(int) Math.ceil(count * .95) - 1] / 1_000_000.0, values[count - 1] / 1_000_000.0, count);
    }
}
