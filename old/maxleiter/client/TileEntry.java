package com.maxleiter.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

class TileEntry {
    String blockName;
    final BlockPos pos;
    final double distance;
    final int count;
    final ItemStack icon;
    final String modid;
    final String modName;
    final String registry;

    TileEntry(TileEntity te, int count, String forced) {
        this.blockName = forced != null ? forced : GuiUtils.getDisplayName(te);
        this.pos = te.getPos();
        this.distance = Math.sqrt(Minecraft.getMinecraft().player.getPosition().distanceSq(pos));
        this.count = count;
        this.registry = te.getBlockType().getRegistryName().toString();
        this.modid = te.getBlockType().getRegistryName().getNamespace();
        this.modName = GuiUtils.lookupModName(modid);
        if (te instanceof TileEntityChest) {
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                BlockPos adj = pos.offset(f);
                if (te.getWorld().getTileEntity(adj) instanceof TileEntityChest) {
                    this.blockName = "Large Chest";
                    break;
                }
            }
        }
        Item i = Item.getItemFromBlock(te.getBlockType());
        this.icon = (i != null) ? new ItemStack(i) : ItemStack.EMPTY;
    }
}