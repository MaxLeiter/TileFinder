package com.maxleiter.tilefinder.client;

import org.lwjgl.glfw.GLFW;

import com.maxleiter.tilefinder.TileFinder;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Handles registration and use of all TileFinder key mappings.
 */
@EventBusSubscriber(modid = TileFinder.MODID, value = Dist.CLIENT)
public final class KeyBindings {
    private KeyBindings() {
    }

    public static final KeyMapping OPEN_GUI = new KeyMapping("key.tilefinder.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE, "key.categories.tilefinder");
    public static final KeyMapping CLEAR_PATH = new KeyMapping("key.tilefinder.clear", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O, "key.categories.tilefinder");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
        event.register(CLEAR_PATH);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_GUI.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new TileFinderScreen());
            }
        }

        while (CLEAR_PATH.consumeClick()) {
            PathHighlighter.clear();
        }
    }
}