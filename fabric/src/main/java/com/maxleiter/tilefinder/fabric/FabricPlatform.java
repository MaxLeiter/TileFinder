package com.maxleiter.tilefinder.fabric;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import com.maxleiter.tilefinder.platform.Platform;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric has no common energy API (Team Reborn Energy is a separate mod), so energy is never reported here. */
public final class FabricPlatform implements Platform {
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

    /** Machines often expose storage on some sides only, so a null (unsided) answer still tries each side. */
    private static boolean has(BlockApiLookup<?, Direction> lookup, Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (lookup.find(level, pos, state, blockEntity, null) != null) return true;
        for (Direction side : Direction.values()) {
            if (lookup.find(level, pos, state, blockEntity, side) != null) return true;
        }
        return false;
    }
}
