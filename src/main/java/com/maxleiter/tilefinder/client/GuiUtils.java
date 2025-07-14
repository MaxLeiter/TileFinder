package com.maxleiter.tilefinder.client;

import java.util.Optional;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;

class GuiUtils {
    static String lookupModName(String modId) {
        Optional<? extends ModContainer> container = ModList.get().getModContainerById(modId);
        return container.map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    static String getDisplayName(BlockEntity be) {
        Item item = be.getBlockState().getBlock().asItem();
        if (item != ItemStack.EMPTY.getItem()) {
            return new ItemStack(item).getHoverName().getString();
        }

        return be.getBlockState().getBlock().getName().getString();
    }
}