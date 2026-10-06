package com.maxleiter.tilefinder.fabric.client;

import com.maxleiter.tilefinder.client.BeamRenderer;
import com.maxleiter.tilefinder.client.Keys;
import com.maxleiter.tilefinder.client.TileFinderClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=26 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
*///?}

public final class TileFinderFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TileFinderClient.init();
        //? if >=26 {
        // Vanilla only sorts registered categories; Fabric has no event for it, so register it here.
        KeyMapping.Category.register(Keys.CATEGORY.id());
        for (KeyMapping key : Keys.ALL) KeyMappingHelper.registerKeyMapping(key);
        //?} else
        /*for (KeyMapping key : Keys.ALL) KeyBindingHelper.registerKeyBinding(key);*/

        ClientTickEvents.END_CLIENT_TICK.register(TileFinderClient::tick);

        //? if >=26 {
        LevelRenderEvents.BEFORE_GIZMOS.register(context ->
                BeamRenderer.submitGizmos(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
        //?} else {
        /*WorldRenderEvents.LAST.register(context -> BeamRenderer.renderImmediate(context.matrixStack(),
                context.camera().getPosition(), context.tickCounter().getGameTimeDeltaPartialTick(false)));
        *///?}

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            //? if >=26 {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !(Keys.OPEN.matches(event) && TileFinderClient.onScreenKey(s)));
            //?} else
            /*ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> !(Keys.OPEN.matches(key, scancode) && TileFinderClient.onScreenKey(s)));*/
        });
    }
}
