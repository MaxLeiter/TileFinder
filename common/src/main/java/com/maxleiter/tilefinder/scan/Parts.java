package com.maxleiter.tilefinder.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * Blocks that are two blocks with a block entity in each but are one thing to a player: double chests, beds, and
 * two-tall blocks. Each part points at the part that stands for the whole, read from the block state alone, so the
 * same answer comes out on the client and the server.
 */
public final class Parts {
    private Parts() {
    }

    /** The position of the part that stands for this block's whole, which is {@code pos} itself for most blocks. */
    public static BlockPos primary(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return min(pos, other);
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            // A bed faces from its foot to its head.
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return pos.relative(facing);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    /** The box around the whole block this part belongs to: both halves of a double chest, a whole bed. */
    public static AABB outline(BlockPos pos, BlockState state) {
        AABB box = new AABB(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return box.minmax(new AABB(pos.relative(ChestBlock.getConnectedDirection(state))));
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            boolean foot = state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
            return box.minmax(new AABB(pos.relative(foot ? facing : facing.getOpposite())));
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            boolean upper = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            return box.minmax(new AABB(upper ? pos.below() : pos.above()));
        }
        return box;
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
