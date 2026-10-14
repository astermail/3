package com.gaspoweredcake.client33.utils.world;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Stable storage identity and explicit UTC display for observed coordinates. */
public final class CoordinateContext {
    public static final String UNKNOWN = "unknown";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private CoordinateContext() {}

    public static String storageKey(String server) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(server.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static String time(long epochMillis) { return epochMillis > 0 ? TIME.format(Instant.ofEpochMilli(epochMillis)) : "Unknown (legacy)"; }
    public static String csvTime(long epochMillis) { return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : ""; }
    public static long parseTime(String value) { return value.isBlank() ? 0 : Instant.parse(value).toEpochMilli(); }
    public static String csv(String value) { return "\"" + value.replace('\r', ' ').replace('\n', ' ').replace("\"", "\"\"") + "\""; }

    /** CSV export fields are single-line; doubled quotes preserve commas and quotes in server labels. */
    public static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else value.append(c);
            } else if (c == ',') { fields.add(value.toString()); value.setLength(0); closed = false; }
            else if (c == '"' && value.isEmpty() && !closed) quoted = true;
            else if (closed || c == '"') throw new IllegalArgumentException("Invalid CSV quoting");
            else value.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unterminated CSV field");
        fields.add(value.toString());
        return fields;
    }
}
