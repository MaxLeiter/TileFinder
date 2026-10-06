package com.maxleiter.tilefinder.neoforge;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.server.FinderCommand;
import com.maxleiter.tilefinder.server.Waypoints;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TileFinder.MOD_ID)
public final class TileFinderNeoForge {
    public TileFinderNeoForge(IEventBus modBus, ModContainer container) {
        // The /tilefinder command and its particle trails run on the server, for players with or without the mod.
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> FinderCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> Waypoints.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> Waypoints.clear(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> Waypoints.clearAll());
    }
}
