package com.maxleiter.tilefinder.gametest;

import java.util.List;

import com.maxleiter.tilefinder.scan.Finder;
import com.maxleiter.tilefinder.scan.Kinds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Runs {@link Finder#find} on real blocks. Each test builds its scene in the 8x5x8 empty arena and scans a sphere
 * around its middle that is too small to reach the neighbouring arenas.
 */
public final class TileFinderGameTests {
    private static boolean initialized;
    private static final int RADIUS = 6;

    private TileFinderGameTests() {
    }

    static void init() {
        if (initialized) return;
        initialized = true;
        GameTests.add("double_chest", TileFinderGameTests::doubleChest);
        GameTests.add("chests_separate", TileFinderGameTests::chestsSeparate);
        GameTests.add("bed", TileFinderGameTests::bed);
        GameTests.add("hoppers_merged", h -> hoppers(h, true));
        GameTests.add("hoppers_separate", h -> hoppers(h, false));
        GameTests.add("signs", TileFinderGameTests::signs);
        GameTests.add("custom_name", TileFinderGameTests::customName);
        GameTests.add("furnaces", TileFinderGameTests::furnaces);
    }

    private static List<Finder.Group> scan(GameTestHelper h, boolean merge) {
        BlockPos center = h.absolutePos(new BlockPos(4, 1, 4));
        // 1.21.1 puts the test's structure block and start command block in the arena; they aren't part of the scene.
        return Finder.find(h.getLevel(), center, new Finder.Options(RADIUS, merge, Kinds.defaults())).stream()
                .filter(group -> !group.key().equals("minecraft:structure_block") && !group.key().equals("minecraft:command_block"))
                .toList();
    }

    /** A double chest as {@link BlockPos} {@code left} (its left half, facing north) and the half east of it. */
    private static void doubleChest(GameTestHelper h, BlockPos left) {
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        h.setBlock(left, chest.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT));
        h.setBlock(left.east(), chest.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT));
    }

    private static void single(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void doubleChest(GameTestHelper h) {
        doubleChest(h, new BlockPos(3, 1, 4));
        List<Finder.Group> groups = scan(h, true);
        h.assertValueEqual(groups.size(), 1, "groups");
        Finder.Group group = groups.getFirst();
        h.assertValueEqual(group.key(), "minecraft:chest", "group key");
        h.assertValueEqual(group.spots().size(), 1, "spots");
        Finder.Spot spot = group.spots().getFirst();
        h.assertValueEqual(spot.members().size(), 1, "members of the spot");
        h.assertValueEqual(spot.members().getFirst().blocks().size(), 2, "blocks of the member");
        h.assertValueEqual(group.blocks(), 2, "blocks of the group");
        h.assertTrue(!group.decorative(), "a chest is not decorative");
        // Without merging, the two halves are still one chest.
        h.assertValueEqual(scan(h, false).getFirst().spots().size(), 1, "spots without merging");
        h.succeed();
    }

    private static void chestsSeparate(GameTestHelper h) {
        doubleChest(h, new BlockPos(1, 1, 4));
        single(h, new BlockPos(5, 1, 4));
        for (boolean merge : new boolean[]{true, false}) {
            List<Finder.Group> groups = scan(h, merge);
            h.assertValueEqual(groups.size(), 1, "groups (merge " + merge + ")");
            Finder.Group group = groups.getFirst();
            h.assertValueEqual(group.spots().size(), 2, "spots (merge " + merge + ")");
            h.assertValueEqual(group.members(), 2, "members (merge " + merge + ")");
            h.assertValueEqual(group.blocks(), 3, "blocks (merge " + merge + ")");
            // Nearest first: the single chest (1 block away from the middle) before the double chest (3).
            h.assertValueEqual(group.spots().getFirst().blocks(), 1, "nearest spot is the single chest");
        }
        h.succeed();
    }

    /** 26.3 made the beds a colour collection, so the block comes from the registry. */
    private static Block redBed() {
        //? if >=26 {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_bed"));
        //?} else
        /*return BuiltInRegistries.BLOCK.get(Identifier.withDefaultNamespace("red_bed"));*/
    }

    private static void bed(GameTestHelper h) {
        BlockState foot = redBed().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
        h.setBlock(new BlockPos(3, 1, 4), foot);
        h.setBlock(new BlockPos(4, 1, 4), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        // 26.2 dropped the bed's block entity (the colour is in the block now): a vanilla bed is not found at all there.
        //? if >=26 {
        h.assertValueEqual(scan(h, true).size(), 0, "groups: vanilla beds have no block entity");
        //?} else {
        /*for (boolean merge : new boolean[]{true, false}) {
            List<Finder.Group> groups = scan(h, merge);
            h.assertValueEqual(groups.size(), 1, "groups (merge " + merge + ")");
            Finder.Group group = groups.getFirst();
            h.assertValueEqual(group.key(), "minecraft:red_bed", "group key");
            h.assertValueEqual(group.spots().size(), 1, "spots");
            h.assertValueEqual(group.members(), 1, "members: both halves are one bed");
            h.assertValueEqual(group.blocks(), 2, "blocks");
            h.assertTrue(group.decorative(), "a bed is decorative");
        }
        *///?}
        h.succeed();
    }

    private static void hoppers(GameTestHelper h, boolean merge) {
        for (int x = 1; x <= 5; x++) h.setBlock(new BlockPos(x, 1, 4), Blocks.HOPPER);
        List<Finder.Group> groups = scan(h, merge);
        h.assertValueEqual(groups.size(), 1, "groups");
        Finder.Group group = groups.getFirst();
        h.assertValueEqual(group.key(), "minecraft:hopper", "group key");
        h.assertValueEqual(group.spots().size(), merge ? 1 : 5, "spots");
        h.assertValueEqual(group.members(), 5, "members");
        if (merge) h.assertValueEqual(group.spots().getFirst().members().size(), 5, "members of the one spot");
        h.succeed();
    }

    private static void signs(GameTestHelper h) {
        // A wall sign on stone, and a standing sign on the arena's floor: two blocks, one thing.
        h.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(new BlockPos(4, 0, 5), Blocks.STONE);
        h.setBlock(new BlockPos(4, 1, 5), Blocks.OAK_SIGN);
        List<Finder.Group> groups = scan(h, true);
        h.assertValueEqual(groups.size(), 1, "groups: " + groups.stream().map(Finder.Group::key).toList());
        Finder.Group group = groups.getFirst();
        h.assertValueEqual(group.key(), "minecraft:oak_sign", "group key (the pick-block item)");
        h.assertValueEqual(group.spots().size(), 2, "spots");
        h.assertTrue(group.decorative(), "signs are decorative");
        h.succeed();
    }

    private static void customName(GameTestHelper h) {
        single(h, new BlockPos(3, 1, 4));
        single(h, new BlockPos(5, 1, 4));
        BlockEntity named = h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 1, 4)));
        h.assertTrue(named != null, "the chest has a block entity");
        named.applyComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Diamonds")).build(),
                DataComponentPatch.EMPTY);
        Finder.Group group = scan(h, false).getFirst();
        h.assertValueEqual(group.spots().size(), 2, "spots");
        int namedCount = 0;
        for (Finder.Spot spot : group.spots()) {
            for (Finder.Member member : spot.members()) {
                if (member.customName() != null) {
                    namedCount++;
                    h.assertValueEqual(member.customName().getString(), "Diamonds", "custom name");
                    h.assertValueEqual(member.pos(), h.absolutePos(new BlockPos(3, 1, 4)), "the named member");
                }
            }
        }
        h.assertValueEqual(namedCount, 1, "named members");
        h.succeed();
    }

    private static void furnaces(GameTestHelper h) {
        h.setBlock(new BlockPos(7, 1, 4), Blocks.BLAST_FURNACE);
        h.setBlock(new BlockPos(3, 1, 4), Blocks.FURNACE);
        List<Finder.Group> groups = scan(h, true);
        h.assertValueEqual(groups.size(), 2, "groups");
        h.assertValueEqual(groups.get(0).key(), "minecraft:furnace", "nearest group");
        h.assertValueEqual(groups.get(1).key(), "minecraft:blast_furnace", "second group");
        h.succeed();
    }
}
