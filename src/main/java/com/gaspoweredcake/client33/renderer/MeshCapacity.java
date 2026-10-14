package com.gaspoweredcake.client33.renderer;

/** Checked sizing before writing native mesh buffers. */
public final class MeshCapacity {
    private static final int MAX_BYTES = Integer.MAX_VALUE - 8;
    private MeshCapacity() {}

    public static int requiredBytes(int used, int additional, int stride) {
        if (used < 0 || additional < 0 || stride <= 0) throw new IllegalArgumentException("Invalid mesh capacity request");
        long bytes = ((long) used + additional) * stride;
        if (bytes > MAX_BYTES) throw new IllegalArgumentException("Mesh exceeds buffer size limit");
        return (int) bytes;
    }

    public static int grow(int current, int required, int minimum) {
        if (current < 0 || required < 0 || minimum < 0 || required > MAX_BYTES || minimum > MAX_BYTES) throw new IllegalArgumentException("Invalid buffer capacity");
        if (current >= required) return current;
        long next = Math.max(required, Math.max(minimum, (long) current * 2));
        return (int) Math.min(MAX_BYTES, next);
    }
}
