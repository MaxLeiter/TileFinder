package com.maxleiter.tilefinder.client;

import com.maxleiter.tilefinder.compat.RecipeViewers;

import dev.vellum.mod.client.VellumHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The client half, called by each loader's client entrypoint and events. */
public final class TileFinderClient {
    private TileFinderClient() {
    }

    /** Client setup: settings, and the HUD overlay (Vellum wants overlays registered before the game finishes loading). */
    public static void init() {
        Settings.load();
        VellumHud.register(Tracker.HUD, Tracker.HUD_URL);
    }

    public static void tick(Minecraft mc) {
        while (Keys.OPEN.consumeClick()) {
            if (McClient.screen() == null) FinderPage.open(null, "list");
        }
        while (Keys.CLEAR.consumeClick()) {
            Tracker.clear();
        }
        Tracker.tick(mc);
        DevAutopilot.tick(mc);
    }

    /**
     * The open key pressed over a screen: if the pointer is on an item (a recipe viewer's list, bookmarks or recipe
     * page, or an inventory slot), find that item's block. Returns true when it did, so the screen doesn't also get
     * the key. Key presses meant as typing are left alone.
     */
    public static boolean onScreenKey(Screen screen) {
        if (screen instanceof ChatScreen || screen.getFocused() instanceof EditBox) return false;
        double mouseX = McClient.mouseX();
        double mouseY = McClient.mouseY();
        ItemStack stack = RecipeViewers.hovered(screen, mouseX, mouseY);
        if (stack.isEmpty()) stack = McClient.hoveredSlotStack(screen);
        if (stack.isEmpty()) return false;
        FinderPage.openFor(stack);
        return true;
    }

    /**
     * A screen for the loaders' config buttons (NeoForge's mod list, Mod Menu) that opens the settings tab. The page is
     * opened from the stand-in's first tick rather than from inside the mod list's setScreen call.
     */
    public static Screen settingsScreen(@Nullable Screen parent) {
        return new Screen(Component.translatable("tilefinder.gui.settings")) {
            @Override
            public void tick() {
                FinderPage.openSettings(parent);
            }
        };
    }
}
