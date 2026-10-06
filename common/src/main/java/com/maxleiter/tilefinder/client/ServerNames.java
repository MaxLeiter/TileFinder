package com.maxleiter.tilefinder.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.scan.Finder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Custom names the client doesn't have. Vanilla never sends a container's name to clients (a renamed chest is just
 * "Chest" client-side), but in singleplayer the integrated server has it: ask the server thread for them, once per
 * scan. On a multiplayer server only names some mods sync are known.
 */
final class ServerNames {
    private static final long TIMEOUT_MS = 250;

    private ServerNames() {
    }

    static Map<BlockPos, String> lookup(List<Finder.Group> groups) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null) return Map.of();
        List<BlockPos> unnamed = new ArrayList<>();
        for (Finder.Group group : groups) {
            for (Finder.Spot spot : group.spots()) {
                for (Finder.Member member : spot.members()) {
                    if (member.customName() == null) unnamed.addAll(member.blocks());
                }
            }
        }
        if (unnamed.isEmpty()) return Map.of();
        var dimension = mc.level.dimension();
        try {
            return server.submit(() -> {
                ServerLevel level = server.getLevel(dimension);
                Map<BlockPos, String> names = new HashMap<>();
                if (level == null) return names;
                for (BlockPos pos : unnamed) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof Nameable nameable && nameable.hasCustomName()) {
                        names.put(pos, nameable.getCustomName().getString());
                    }
                }
                return names;
            }).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            TileFinder.LOG.warn("The integrated server didn't answer within {} ms; listing without custom names", TIMEOUT_MS);
            return Map.of();
        } catch (InterruptedException | ExecutionException e) {
            TileFinder.LOG.error("Couldn't read custom names from the integrated server", e);
            return Map.of();
        }
    }
}
