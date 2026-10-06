package com.maxleiter.tilefinder.fabric;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.gametest.GameTests;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
//? if >=26 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else
/*import net.minecraft.gametest.framework.GameTestRegistry;*/
import com.maxleiter.tilefinder.server.FinderCommand;
import com.maxleiter.tilefinder.server.Waypoints;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class TileFinderFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // GameTests: test functions in a registry from 26.x (their instances are data), a registered class before.
        //? if >=26 {
        GameTests.all().forEach((name, body) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, TileFinder.id(name), body));
        //?} else
        /*if (System.getProperty("fabric-api.gametest") != null) GameTestRegistry.register(GameTests.class);*/
        // The /tilefinder command and its particle trails run on the server, for players with or without the mod.
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> FinderCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Waypoints::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Waypoints.clear(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Waypoints.clearAll());
    }
}
