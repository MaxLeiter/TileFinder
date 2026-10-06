package com.maxleiter.tilefinder.neoforge;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.gametest.GameTests;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
//? if >=26 {
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.RegisterEvent;
//?} else
/*import net.neoforged.neoforge.event.RegisterGameTestsEvent;*/

@Mod(TileFinder.MOD_ID)
public final class TileFinderNeoForge {
    public TileFinderNeoForge(IEventBus modBus, ModContainer container) {
        // GameTests: test functions in a registry from 26.x (their instances are data), a registered class before.
        //? if >=26 {
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.TEST_FUNCTION,
                helper -> GameTests.all().forEach((name, body) -> helper.register(TileFinder.id(name), body))));
        //?} else
        /*modBus.addListener(RegisterGameTestsEvent.class, event -> event.register(GameTests.class));*/
    }
}
