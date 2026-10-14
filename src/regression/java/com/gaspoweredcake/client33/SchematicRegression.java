package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.world.SchematicMaterials;
import net.minecraft.SharedConstants;
import java.util.List;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Material counts must represent inventory consumption rather than occupied block positions. */
public final class SchematicRegression {
    public static void run() {
        validateWorkspaceInputs();

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        check(SchematicMaterials.units(Blocks.AIR.defaultBlockState()) == 0, "Air requires no supplies");
        check(SchematicMaterials.units(Blocks.STONE.defaultBlockState()) == 1, "Ordinary placement consumes one item");
        var door = Blocks.OAK_DOOR.defaultBlockState();
        check(SchematicMaterials.units(door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER))
            + SchematicMaterials.units(door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)) == 1,
            "A door occupies two cells but requires one door item");
        var bed = Blocks.BED.red().defaultBlockState();
        check(SchematicMaterials.units(bed.setValue(BedBlock.PART, BedPart.HEAD))
            + SchematicMaterials.units(bed.setValue(BedBlock.PART, BedPart.FOOT)) == 1, "A bed requires one item");
        check(SchematicMaterials.units(Blocks.SUNFLOWER.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)) == 0,
            "Tall flowers must not double the materials list");
        check(SchematicMaterials.units(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE)) == 2,
            "Double slabs require two inventory slabs");
        check(SchematicMaterials.units(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4)) == 4,
            "Grouped candles require each candle");
        check(SchematicMaterials.units(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 8)) == 8,
            "Layered snow requires each layer");
        check(SchematicMaterials.units(Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 3)) == 3,
            "Grouped pickles require each item");
        check(SchematicMaterials.stacks(65, 64) == 2 && SchematicMaterials.stacks(64, 64) == 1
            && SchematicMaterials.stacks(17, 16) == 2 && SchematicMaterials.stacks(0, 64) == 0, "Stack counts use each item's capacity and round up");
        var supplied = new com.gaspoweredcake.client33.utils.world.MaterialReport.Entry("minecraft:oak_log", "Oak Log", 64, 80, 64, false);
        var missing = new com.gaspoweredcake.client33.utils.world.MaterialReport.Entry("minecraft:stone", "Stone", 65, 1, 64, false);
        var manual = new com.gaspoweredcake.client33.utils.world.MaterialReport.Entry("minecraft:water", "Water", 4, 0, 1, true);
        var report = java.util.List.of(supplied, missing, manual);
        var sort = com.gaspoweredcake.client33.utils.world.MaterialReport.Sort.Missing;
        check(supplied.missing() == 0 && missing.missing() == 64 && missing.stacks() == 2, "Report shortages clamp surplus and use actual stack sizes");
        check(com.gaspoweredcake.client33.utils.world.MaterialReport.filter(report, "OAK log", false, sort).equals(java.util.List.of(supplied)), "Materials search accepts names with spaces/case and registry underscores");
        check(com.gaspoweredcake.client33.utils.world.MaterialReport.filter(report, "minecraft:oak-log", false, sort).equals(java.util.List.of(supplied)), "Registry ID search is normalized");
        check(com.gaspoweredcake.client33.utils.world.MaterialReport.filter(report, "", true, sort).equals(java.util.List.of(missing, manual)), "Shortage view preserves manual requirements and excludes supplied items");
        check(com.gaspoweredcake.client33.utils.world.MaterialReport.filter(java.util.List.of(), "", false, sort).isEmpty(), "Empty schematic reports remain browsable");
        System.out.println("Schematic materials regressions passed.");
    }
    private static void validateWorkspaceInputs() {
        var position = com.gaspoweredcake.client33.utils.world.SchematicFiles.origin(" -12 ", "64", "999");
        check(position.getX() == -12 && position.getY() == 64 && position.getZ() == 999, "Origin controls accept signed whole coordinates");
        boolean rejected = false;
        try { com.gaspoweredcake.client33.utils.world.SchematicFiles.origin("1.5", "64", "0"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Fractional origins do not silently round");
        var offline = new com.gaspoweredcake.client33.utils.world.SchematicBuildState(false, false, true, false, false);
        check(!offline.canLoad() && !offline.canPlace() && !offline.canStop(), "World actions remain unavailable outside a world");
        var ready = new com.gaspoweredcake.client33.utils.world.SchematicBuildState(true, false, true, false, false);
        check(ready.canLoad() && ready.canPlace() && !ready.canPause() && !ready.canResume(), "Ready session exposes placement but no runtime actions");
        var scanning = new com.gaspoweredcake.client33.utils.world.SchematicBuildState(true, true, true, false, false);
        check(!scanning.canLoad() && !scanning.canPlace(), "Material scanning cannot race a load or start");
        var paused = new com.gaspoweredcake.client33.utils.world.SchematicBuildState(true, false, true, true, true);
        check(!paused.canLoad() && !paused.canPlace() && paused.canResume() && paused.canStop() && !paused.canPause(), "Paused builder still owns its file and origin");
        var building = new com.gaspoweredcake.client33.utils.world.SchematicBuildState(true, false, true, true, false);
        check(building.canPause() && building.canStop() && !building.canResume(), "Active build exposes only relevant runtime actions");
        var small = new com.gaspoweredcake.client33.utils.world.SchematicLibrary.Entry("Oak House.SCHEM", 1024, 20);
        var large = new com.gaspoweredcake.client33.utils.world.SchematicLibrary.Entry("Camp.schematic", 2048, 10);
        var libraryEntries = List.of(small, large);
        check(com.gaspoweredcake.client33.utils.world.SchematicLibrary.filter(libraryEntries, "oak_house", com.gaspoweredcake.client33.utils.world.SchematicLibrary.Sort.Name).equals(List.of(small)), "Library search tolerates case and filename separators");
        check(com.gaspoweredcake.client33.utils.world.SchematicLibrary.filter(libraryEntries, "", com.gaspoweredcake.client33.utils.world.SchematicLibrary.Sort.Newest).getFirst().equals(small), "Recent sorting uses observed file times");
        check(com.gaspoweredcake.client33.utils.world.SchematicLibrary.filter(libraryEntries, "", com.gaspoweredcake.client33.utils.world.SchematicLibrary.Sort.Largest).getFirst().equals(large), "Size sort uses byte counts");
        check(small.format().equals("SCHEM") && small.sizeText().equals("1.0 KiB"), "Library metadata shows format and readable size");
        check(com.gaspoweredcake.client33.utils.world.SchematicFiles.externalPath(" \"/tmp/my house.schem\" ").equals(java.nio.file.Path.of("/tmp/my house.schem")), "Quoted path imports preserve spaces");
        try { com.gaspoweredcake.client33.utils.world.SchematicFiles.externalPath("  "); throw new AssertionError("Empty path accepted"); }
        catch (IllegalArgumentException expected) { }
        java.nio.file.Path root = null;
        try {
            root = java.nio.file.Files.createTempDirectory("client33-schematic-files");
            java.nio.file.Files.writeString(root.resolve("House.SCHEM"), "synthetic");
            java.nio.file.Files.writeString(root.resolve("camp.schematic"), "synthetic");
            java.nio.file.Files.writeString(root.resolve("readme.txt"), "synthetic");
            java.nio.file.Files.createDirectory(root.resolve("folder.schem"));
            java.nio.file.Files.createSymbolicLink(root.resolve("alias.schem"), root.resolve("House.SCHEM"));
            var list = com.gaspoweredcake.client33.utils.world.SchematicFiles.list(root, List.of(".schem", "schematic"));
            check(list.equals(List.of("camp.schematic", "House.SCHEM")), "File picker uses supported extensions, case-insensitive names, regular files and sorted results");
            var library = root.resolve("library");
            var extensions = List.of("schem", ".schematic");
            var imported = com.gaspoweredcake.client33.utils.world.SchematicFiles.importFile(root.resolve("House.SCHEM"), library, extensions);
            check(imported.equals("House.SCHEM") && java.nio.file.Files.readString(library.resolve(imported)).equals("synthetic"), "Import preserves bytes and filename");
            java.nio.file.Files.writeString(root.resolve("House.SCHEM"), "second");
            var duplicate = com.gaspoweredcake.client33.utils.world.SchematicFiles.importFile(root.resolve("House.SCHEM"), library, extensions);
            check(duplicate.equals("House (1).SCHEM") && java.nio.file.Files.readString(library.resolve(imported)).equals("synthetic"), "Duplicate import never overwrites the original");
            check(com.gaspoweredcake.client33.utils.world.SchematicFiles.importFile(library.resolve(imported), library, extensions).equals(imported), "Selecting an existing library file does not duplicate it");
            for (String invalid : List.of("readme.txt", "folder.schem", "alias.schem")) {
                boolean invalidRejected = false;
                try { com.gaspoweredcake.client33.utils.world.SchematicFiles.importFile(root.resolve(invalid), library, extensions); }
                catch (java.io.IOException expected) { invalidRejected = true; }
                check(invalidRejected, "Unsupported files, directories and symbolic links are rejected");
            }
            try (var importedFiles = java.nio.file.Files.list(library)) {
                check(importedFiles.count() == 2, "Imports leave no temporary files or rejected entries");
            }
            var metadata = com.gaspoweredcake.client33.utils.world.SchematicFiles.scan(library, extensions);
            check(metadata.size() == 2 && metadata.stream().anyMatch(entry -> entry.name().equals(imported) && entry.bytes() == "synthetic".length()), "Library scans real file metadata without parsing payloads");
            for (int i = 0; i < 258; i++) java.nio.file.Files.writeString(root.resolve("extra-" + i + ".schem"), "fixture");
            check(com.gaspoweredcake.client33.utils.world.SchematicFiles.list(root, extensions).size() == 260, "Files past the old 256-entry limit remain searchable");

        } catch (java.io.IOException e) { throw new AssertionError(e); }
        finally {
            if (root != null) try (var paths = java.nio.file.Files.walk(root)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
