package com.maxleiter.tilefinder.fabric.client;

import com.maxleiter.tilefinder.client.TileFinderClient;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button opens the finder's settings tab. Mod Menu loads this class only when it is installed. */
public final class TileFinderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TileFinderClient::settingsScreen;
    }
}
