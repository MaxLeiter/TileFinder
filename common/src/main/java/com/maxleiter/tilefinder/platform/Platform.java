package com.maxleiter.tilefinder.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What differs between NeoForge and Fabric. Each loader provides one through META-INF/services. */
public interface Platform {
    Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
            .orElseThrow(() -> new IllegalStateException("No TileFinder platform: the loader jar is missing its service file"));

    /** The display name of the mod that owns a namespace, or the namespace itself when no mod has that id. */
    String modName(String namespace);

    boolean isModLoaded(String modId);

    Path configDir();

    /**
     * What a block entity exposes through the loader's transfer APIs: items, fluids, energy. Mods attach these to
     * their machines, so this is how a furnace from one mod and a crusher from another both read as item holders even
     * though neither implements vanilla's Container.
     */
    Set<Holds> holds(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity);

    enum Holds {
        ITEMS, FLUIDS, ENERGY
    }
}
