package com.maxleiter.tilefinder.scan;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Collects the loaded block entities around a point, on either side. */
public final class Scanner {
    private Scanner() {
    }

    /**
     * Every block entity within {@code radius} blocks of {@code center} (a sphere) in chunks that are loaded now.
     * Reads each chunk's block entity map instead of probing every block, so a radius of 128 costs a few hundred map
     * walks, not millions of lookups. Unloaded chunks are skipped, never loaded.
     */
    public static List<BlockEntity> around(Level level, BlockPos center, int radius) {
        long radiusSq = (long) radius * radius;
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        List<BlockEntity> found = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.isRemoved()) continue;
                    if (blockEntity.getBlockPos().distSqr(center) > radiusSq) continue;
                    found.add(blockEntity);
                }
            }
        }
        return found;
    }
}
