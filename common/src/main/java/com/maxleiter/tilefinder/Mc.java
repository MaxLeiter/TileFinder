package com.maxleiter.tilefinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The few calls whose shape differs between the Minecraft versions TileFinder builds for (sides both). */
public final class Mc {
    private Mc() {
    }

    public static int minY(Level level) {
        //? if >=26 {
        return level.getMinY();
        //?} else
        /*return level.getMinBuildHeight();*/
    }

    /** The highest block Y, inclusive. */
    public static int maxY(Level level) {
        //? if >=26 {
        return level.getMaxY();
        //?} else
        /*return level.getMaxBuildHeight() - 1;*/
    }

    /** What pick-block gives for this block: the item a wall sign or a wall head comes from, without its data. */
    @SuppressWarnings("deprecation") // 1.21.1's Block#getCloneItemStack; NeoForge's replacement needs a Player
    public static ItemStack pickStack(Level level, BlockPos pos, BlockState state) {
        //? if >=26 {
        return state.getCloneItemStack(level, pos, false);
        //?} else
        /*return state.getBlock().getCloneItemStack(level, pos, state);*/
    }

    public static Identifier blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static Identifier typeId(BlockEntityType<?> type) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    }
}
