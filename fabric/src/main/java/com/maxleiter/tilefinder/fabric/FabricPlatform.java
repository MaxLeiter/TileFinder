package com.maxleiter.tilefinder.fabric;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.platform.Platform;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric has no common energy API (Team Reborn Energy is a separate mod), so energy is never reported here. */
public final class FabricPlatform implements Platform {
    private static final Set<BlockEntityType<?>> FAILED = ConcurrentHashMap.newKeySet();

    @Override
    public String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName()).orElse(namespace);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Set<Holds> holds(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        Set<Holds> holds = EnumSet.noneOf(Holds.class);
        if (has(ItemStorage.SIDED, level, pos, state, blockEntity)) holds.add(Holds.ITEMS);
        if (has(FluidStorage.SIDED, level, pos, state, blockEntity)) holds.add(Holds.FLUIDS);
        return holds;
    }

    /**
     * Machines often expose storage on some sides only, so a null (unsided) answer still tries each side. The providers
     * are other mods' code, often written for the server only; one that throws on the client counts as not exposing
     * storage, and is logged once per block entity type.
     */
    private static boolean has(BlockApiLookup<?, Direction> lookup, Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        try {
            if (lookup.find(level, pos, state, blockEntity, null) != null) return true;
            for (Direction side : Direction.values()) {
                if (lookup.find(level, pos, state, blockEntity, side) != null) return true;
            }
        } catch (RuntimeException e) {
            if (FAILED.add(blockEntity.getType())) {
                TileFinder.LOG.warn("{} provider for {} threw; listing it without that storage", lookup.getId(),
                        Mc.typeId(blockEntity.getType()), e);
            }
        }
        return false;
    }
}
