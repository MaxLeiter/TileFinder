package com.maxleiter.tilefinder.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Light-weight record of a BlockEntity used by the GUI list.
 */
class TileEntry {
    final String blockName;
    final BlockPos pos;
    final double distance;
    final ItemStack icon;
    final String modId;

    TileEntry(BlockEntity be, Vec3 playerPos) {
        this.pos = be.getBlockPos();
        this.distance = Math.sqrt(playerPos.distanceToSqr(Vec3.atCenterOf(this.pos)));

        this.blockName = GuiUtils.getDisplayName(be);

        BlockState state = be.getBlockState();
        Item item = state.getBlock().asItem();
        this.icon = item == ItemStack.EMPTY.getItem() ? ItemStack.EMPTY : new ItemStack(item);

        this.modId = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
    }
}