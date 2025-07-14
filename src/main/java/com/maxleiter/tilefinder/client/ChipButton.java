package com.maxleiter.tilefinder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A custom button with a chip-like appearance
 */
public class ChipButton extends Button {
    public ChipButton(int x, int y, int width, int height, Component text, OnPress onPress) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int bg = this.isHovered() ? 0xFF3C6BFF : 0xFF2E2E2E;
        int fg = 0xFFFFFF;

        // Draw background
        gfx.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);

        // Draw text centered
        gfx.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.width / 2,
                this.getY() + (this.height - 8) / 2,
                fg);
    }
}