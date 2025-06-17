package com.maxleiter.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraft.util.text.ITextComponent;

class GuiUtils {
    static String lookupModName(String id) {
        ModContainer mc = Loader.instance().getIndexedModList().get(id);
        return mc != null ? mc.getName() : id;
    }

    static String getDisplayName(TileEntity te) {
        Item i = Item.getItemFromBlock(te.getBlockType());
        if (i != null && i != Item.getItemById(0))
            return new ItemStack(i).getDisplayName();

        return I18n.translateToLocal(te.getBlockType().getLocalizedName());
    }
}