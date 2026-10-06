package com.maxleiter.tilefinder.client;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Client calls whose shape differs between the Minecraft versions TileFinder builds for. */
public final class McClient {
    private McClient() {
    }

    public static @Nullable Screen screen() {
        //? if >=26 {
        return Minecraft.getInstance().gui.screen();
        //?} else
        /*return Minecraft.getInstance().screen;*/
    }

    public static void setScreen(@Nullable Screen screen) {
        //? if >=26 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else
        /*Minecraft.getInstance().setScreen(screen);*/
    }

    public static double mouseX() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        return mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
    }

    public static double mouseY() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        return mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
    }

    /** The stack in the inventory slot under the pointer, or empty. */
    public static ItemStack hoveredSlotStack(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return ItemStack.EMPTY;
        Slot slot = container.hoveredSlot;
        return slot == null || !slot.hasItem() ? ItemStack.EMPTY : slot.getItem();
    }

    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
