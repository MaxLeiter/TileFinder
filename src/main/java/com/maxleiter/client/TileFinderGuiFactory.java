package com.maxleiter.client;

import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import java.util.Set;
import com.maxleiter.common.TileFinderConfig;

public class TileFinderGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft mc) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        IConfigElement root = new ConfigElement(TileFinderConfig.getConfig().getCategory("beam"));
        return new GuiConfig(parent, root.getChildElements(), "tilefinder", false, false, "TileFinder Configuration");
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}