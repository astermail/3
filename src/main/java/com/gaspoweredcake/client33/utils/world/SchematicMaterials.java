/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.utils.world;

import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class SchematicMaterials {
    private SchematicMaterials() {}

    /** Items consumed by a placement: paired halves share an item, double slabs consume two. */
    public static int units(BlockState state) {
        if (state.isAir()) return 0;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
            && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        if (state.hasProperty(BlockStateProperties.CANDLES)) return state.getValue(BlockStateProperties.CANDLES);
        if (state.hasProperty(BlockStateProperties.PICKLES)) return state.getValue(BlockStateProperties.PICKLES);
        if (state.hasProperty(BlockStateProperties.LAYERS)) return state.getValue(BlockStateProperties.LAYERS);
        if (state.hasProperty(BlockStateProperties.EGGS)) return state.getValue(BlockStateProperties.EGGS);
        if (state.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) return state.getValue(BlockStateProperties.FLOWER_AMOUNT);
        return 1;
    }

    public static long stacks(long count, int stackSize) {
        return count / stackSize + (count % stackSize == 0 ? 0 : 1);
    }
}
