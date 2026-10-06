package com.maxleiter.tilefinder.neoforge.compat;

import com.maxleiter.tilefinder.compat.rei.TileFinderReiPlugin;

import me.shedaniel.rei.forge.REIPluginClient;

/** NeoForge finds REI plugins by this annotation; the plugin itself is shared with Fabric. */
@REIPluginClient
public final class NeoForgeReiPlugin extends TileFinderReiPlugin {
}
