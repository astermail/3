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
                .sorted(String.CASE_INSENSITIVE_ORDER).limit(256).toList();
        }
    }
    public static BlockPos origin(String x, String y, String z) {
        return new BlockPos(Integer.parseInt(x.strip()), Integer.parseInt(y.strip()), Integer.parseInt(z.strip()));
    }
}
