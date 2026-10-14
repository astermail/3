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
