package com.maxleiter.tilefinder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TileFinder.MODID)
public class TileFinder {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "tilefinder";

    // The constructor for the mod class is the first code that is run when your mod
    // is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and
    // pass them in automatically.
    public TileFinder(IEventBus modEventBus, ModContainer modContainer) {
        // Register the configuration
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
