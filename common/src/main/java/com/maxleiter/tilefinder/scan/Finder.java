package com.maxleiter.tilefinder.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Turns the block entities around a point into what a player thinks of as things: a double chest is one chest, a bed
 * is one bed, a run of cable is one network, and a wall sign sorts with the signs.
 */
public final class Finder {
    private Finder() {
    }

    /**
     * @param mergeConnected join touching block entities of the same block (cables, pipes, storage walls) into one spot
     */
    public record Options(int radius, boolean mergeConnected, Kinds kinds) {
    }

    /** One block entity, or the parts of one block that has a block entity in each (both halves of a double chest). */
    public record Member(BlockPos pos, List<BlockPos> blocks, @Nullable Component customName) {
    }

    /** One place to go: a member, or members of the same block that touch. Members are nearest first. */
    public record Spot(List<Member> members, double distanceSq) {
        public BlockPos pos() {
            return members.getFirst().pos();
        }

        public int blocks() {
            int blocks = 0;
            for (Member member : members) blocks += member.blocks().size();
            return blocks;
        }
    }

    /**
     * Everything of one kind: the spots whose blocks pick to the same item (or are the same block, for blocks without
     * an item). Spots are nearest first.
     */
    public record Group(String key, Block block, Identifier blockId, ItemStack icon, Component name,
                        Set<Platform.Holds> holds, boolean decorative, List<Spot> spots) {
        public double nearestSq() {
            return spots.getFirst().distanceSq();
        }

        public int blocks() {
            int blocks = 0;
            for (Spot spot : spots) blocks += spot.blocks();
            return blocks;
        }

        public int members() {
            int members = 0;
            for (Spot spot : spots) members += spot.members().size();
            return members;
        }
    }

    /** Groups nearest first. */
    public static List<Group> find(Level level, BlockPos center, Options options) {
        List<BlockEntity> found = Scanner.around(level, center, options.radius());
        Map<BlockPos, BlockEntity> byPos = new HashMap<>();
        for (BlockEntity blockEntity : found) byPos.put(blockEntity.getBlockPos(), blockEntity);

        // One entry per primary part. LinkedHashMap keeps the result stable from scan to scan.
        Map<BlockPos, List<BlockEntity>> parts = new LinkedHashMap<>();
        for (BlockEntity blockEntity : found) {
            if (options.kinds().hidden(blockEntity.getType())) continue;
            BlockPos pos = blockEntity.getBlockPos();
            BlockState state = blockEntity.getBlockState();
            BlockPos primary = Parts.primary(pos, state);
            BlockEntity primaryEntity = byPos.get(primary);
            // The other part may be out of range, or the states may disagree after a block update: stand alone then.
            if (primaryEntity == null || primaryEntity.getBlockState().getBlock() != state.getBlock()) primary = pos;
            parts.computeIfAbsent(primary, p -> new ArrayList<>()).add(blockEntity);
        }

        List<Member> members = new ArrayList<>(parts.size());
        Map<BlockPos, Integer> memberAt = new HashMap<>();
        for (Map.Entry<BlockPos, List<BlockEntity>> entry : parts.entrySet()) {
            List<BlockPos> blocks = new ArrayList<>();
            Component customName = null;
            for (BlockEntity part : entry.getValue()) {
                blocks.add(part.getBlockPos());
                if (customName == null && part instanceof Nameable nameable && nameable.hasCustomName()) {
                    customName = nameable.getCustomName();
                }
            }
            for (BlockPos block : blocks) memberAt.put(block, members.size());
            members.add(new Member(entry.getKey(), List.copyOf(blocks), customName));
        }

        // Union-find over members of the same block that share a face.
        int[] root = new int[members.size()];
        for (int i = 0; i < root.length; i++) root[i] = i;
        if (options.mergeConnected()) {
            for (int i = 0; i < members.size(); i++) {
                Block block = byPos.get(members.get(i).pos()).getBlockState().getBlock();
                for (BlockPos pos : members.get(i).blocks()) {
                    for (Direction direction : Direction.values()) {
                        Integer neighbour = memberAt.get(pos.relative(direction));
                        if (neighbour == null || neighbour == i) continue;
                        if (byPos.get(members.get(neighbour).pos()).getBlockState().getBlock() != block) continue;
                        union(root, i, neighbour);
                    }
                }
            }
        }

        Map<Integer, List<Member>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < members.size(); i++) clusters.computeIfAbsent(find(root, i), r -> new ArrayList<>()).add(members.get(i));

        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        for (List<Member> cluster : clusters.values()) {
            cluster.sort(Comparator.comparingDouble(member -> member.pos().distSqr(center)));
            Spot spot = new Spot(List.copyOf(cluster), cluster.getFirst().pos().distSqr(center));
            BlockEntity blockEntity = byPos.get(spot.pos());
            BlockState state = blockEntity.getBlockState();
            ItemStack icon = Mc.pickStack(level, spot.pos(), state);
            String key = icon.isEmpty() ? Mc.blockId(state.getBlock()).toString() : BuiltInRegistries.ITEM.getKey(icon.getItem()).toString();
            groups.computeIfAbsent(key, k -> new GroupBuilder(k, level, blockEntity, icon, options.kinds())).spots.add(spot);
        }

        List<Group> result = new ArrayList<>(groups.size());
        for (GroupBuilder builder : groups.values()) result.add(builder.build());
        result.sort(Comparator.comparingDouble(Group::nearestSq));
        return result;
    }

    private static final class GroupBuilder {
        private final String key;
        private final Block block;
        private final ItemStack icon;
        private final Component name;
        private final Set<Platform.Holds> holds;
        private final boolean decorative;
        private final List<Spot> spots = new ArrayList<>();

        GroupBuilder(String key, Level level, BlockEntity sample, ItemStack icon, Kinds kinds) {
            BlockState state = sample.getBlockState();
            this.key = key;
            this.block = state.getBlock();
            this.icon = icon;
            this.name = icon.isEmpty() ? block.getName() : icon.getHoverName();
            this.holds = Platform.INSTANCE.holds(level, sample.getBlockPos(), state, sample);
            this.decorative = kinds.decorative(sample.getType());
        }

        Group build() {
            spots.sort(Comparator.comparingDouble(Spot::distanceSq));
            return new Group(key, block, Mc.blockId(block), icon, name, holds, decorative, List.copyOf(spots));
        }
    }

    private static int find(int[] root, int i) {
        while (root[i] != i) {
            root[i] = root[root[i]];
            i = root[i];
        }
        return i;
    }

    private static void union(int[] root, int a, int b) {
        int rootA = find(root, a);
        int rootB = find(root, b);
        if (rootA != rootB) root[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
    }
}
