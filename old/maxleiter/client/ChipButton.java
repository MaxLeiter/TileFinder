package com.maxleiter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

class ChipButton extends GuiButton {
    ChipButton(int id, int x, int y, int w, int h, String s) {
        super(id, x, y, w, h, s);
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my, float pt) {
        if (!visible)
            return;
        hovered = mx >= x && mx < x + width && my >= y && my < y + height;
        int bg = hovered ? 0xFF3C6BFF : 0xFF2E2E2E;
        int fg = 0xFFFFFF;
        GlStateManager.disableTexture2D();
        drawRect(x, y, x + width, y + height, bg);
        GlStateManager.enableTexture2D();
        mc.fontRenderer.drawString(displayString, x + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
                y + (height - 8) / 2, fg);
    }
}