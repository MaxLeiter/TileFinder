package com.maxleiter.tilefinder.neoforge;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.gametest.GameTests;
import com.maxleiter.tilefinder.server.FinderCommand;
import com.maxleiter.tilefinder.server.Waypoints;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
//? if >=26 {
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.RegisterEvent;
//?} else
/*import net.neoforged.neoforge.event.RegisterGameTestsEvent;*/
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TileFinder.MOD_ID)
public final class TileFinderNeoForge {
    public TileFinderNeoForge(IEventBus modBus, ModContainer container) {
        // GameTests: test functions in a registry from 26.x (their instances are data), a registered class before.
        //? if >=26 {
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.TEST_FUNCTION,
                helper -> GameTests.all().forEach((name, body) -> helper.register(TileFinder.id(name), body))));
        //?} else
        /*modBus.addListener(RegisterGameTestsEvent.class, event -> event.register(GameTests.class));*/
        // The /tilefinder command and its particle trails run on the server, for players with or without the mod.
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> FinderCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> Waypoints.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> Waypoints.clear(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> Waypoints.clearAll());
    }
}
