package com.maxleiter.tilefinder.fabric;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.gametest.GameTests;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
//? if >=26 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else
/*import net.minecraft.gametest.framework.GameTestRegistry;*/

public final class TileFinderFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // GameTests: test functions in a registry from 26.x (their instances are data), a registered class before.
        //? if >=26 {
        GameTests.all().forEach((name, body) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, TileFinder.id(name), body));
        //?} else
        /*if (System.getProperty("fabric-api.gametest") != null) GameTestRegistry.register(GameTests.class);*/
    }
}
