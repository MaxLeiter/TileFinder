package com.maxleiter.tilefinder.neoforge;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import com.maxleiter.tilefinder.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class NeoForgePlatform implements Platform {
    //? if >=26 {
    private static final BlockCapability<?, Direction> ITEMS = Capabilities.Item.BLOCK;
    private static final BlockCapability<?, Direction> FLUIDS = Capabilities.Fluid.BLOCK;
    private static final BlockCapability<?, Direction> ENERGY = Capabilities.Energy.BLOCK;
    //?} else {
    /*private static final BlockCapability<?, Direction> ITEMS = Capabilities.ItemHandler.BLOCK;
    private static final BlockCapability<?, Direction> FLUIDS = Capabilities.FluidHandler.BLOCK;
    private static final BlockCapability<?, Direction> ENERGY = Capabilities.EnergyStorage.BLOCK;
    *///?}

    @Override
    public String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Set<Holds> holds(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        Set<Holds> holds = EnumSet.noneOf(Holds.class);
        if (has(level, pos, state, blockEntity, ITEMS)) holds.add(Holds.ITEMS);
        if (has(level, pos, state, blockEntity, FLUIDS)) holds.add(Holds.FLUIDS);
        if (has(level, pos, state, blockEntity, ENERGY)) holds.add(Holds.ENERGY);
        return holds;
    }

    /** Machines often expose a handler on some sides only, so a null (unsided) answer still tries each side. */
    private static boolean has(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, BlockCapability<?, Direction> capability) {
        if (level.getCapability(capability, pos, state, blockEntity, null) != null) return true;
        for (Direction side : Direction.values()) {
            if (level.getCapability(capability, pos, state, blockEntity, side) != null) return true;
        }
        return false;
    }
}
