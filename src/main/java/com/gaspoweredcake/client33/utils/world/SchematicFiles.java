package com.gaspoweredcake.client33.utils.world;

import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;

/** Local file discovery and explicit coordinate parsing for the schematic workspace. */
public final class SchematicFiles {
    private SchematicFiles() {}
    public static List<String> list(Path folder, List<String> extensions) throws IOException {
        Files.createDirectories(folder);
        try (var files = Files.list(folder)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .map(path -> path.getFileName().toString())
                .filter(name -> extensions.stream().anyMatch(ext -> name.toLowerCase(java.util.Locale.ROOT).endsWith("." + ext.replaceFirst("^\\.", "").toLowerCase(java.util.Locale.ROOT))))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }
    public static List<SchematicLibrary.Entry> scan(Path folder, List<String> extensions) throws IOException {
        var entries = new java.util.ArrayList<SchematicLibrary.Entry>();
        for (String name : list(folder, extensions)) {
            try {
                var attributes = Files.readAttributes(folder.resolve(name), java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isRegularFile()) entries.add(new SchematicLibrary.Entry(name, attributes.size(), attributes.lastModifiedTime().toMillis()));
            } catch (NoSuchFileException removedDuringScan) {
                // A refresh can race a user's filesystem changes.
            }
        }
        return List.copyOf(entries);
    }

    public static Path externalPath(String entered) {
        String value = entered.strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        if (value.isBlank()) throw new IllegalArgumentException("Paste the full path of a schematic file.");
        return Path.of(value);
    }

    /** Copies an external file into the library, assigning a new name on collisions. */
    public static String importFile(Path source, Path folder, List<String> extensions) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Select a regular schematic file.");
        String name = source.getFileName().toString();
        if (extensions.stream().noneMatch(ext -> name.toLowerCase(java.util.Locale.ROOT).endsWith("." + ext.replaceFirst("^\\.", "").toLowerCase(java.util.Locale.ROOT))))
            throw new IOException("Unsupported schematic format. Supported: " + String.join(", ", extensions));
        Files.createDirectories(folder);
        if (source.toRealPath().getParent().equals(folder.toRealPath())) return name;
        int dot = name.lastIndexOf('.');
        Path staged = Files.createTempFile(folder, ".import-", ".tmp");
        try {
            Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            for (int index = 0; index < 10000; index++) {
                String candidate = index == 0 ? name : name.substring(0, dot) + " (" + index + ")" + name.substring(dot);
                try {
                    Files.move(staged, folder.resolve(candidate));
                    return candidate;
                } catch (FileAlreadyExistsException collision) {
                    // Preserve existing files and retry under a new name.
                }
            }
            throw new IOException("Too many files with this name. Rename the source and try again.");
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static BlockPos origin(String x, String y, String z) {
        return new BlockPos(Integer.parseInt(x.strip()), Integer.parseInt(y.strip()), Integer.parseInt(z.strip()));
    }
}
