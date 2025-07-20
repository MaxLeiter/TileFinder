package com.maxleiter.tilefinder.server;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.Page;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Minimal server-side UI powered by GooeyLibs. Opens a simple chest menu showing all block entity types
 * within a radius around the invoking player. Clicking an entry spawns a burst of particles at each
 * matching location so vanilla clients can visually locate them. Shift-click teleports to the nearest.
 */
@EventBusSubscriber(modid = com.maxleiter.tilefinder.TileFinder.MODID)
public final class TileFinderServerUI {
    private TileFinderServerUI() {}

    public static void open(@NotNull ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        Map<Block, List<BlockPos>> found = scan(level, center, radius);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("tilefinder.server.no_results", radius).withStyle(ChatFormatting.RED), false);
            return;
        }

        Page page = buildPage(player, found, radius);
        UIManager.openUIForcefully(player, page);
    }

    // ----------------------------------------------------------------------------------------------------
    // Scan helpers
    // ----------------------------------------------------------------------------------------------------
    private static Map<Block, List<BlockPos>> scan(ServerLevel level, BlockPos center, int radius) {
        Map<Block, List<BlockPos>> results = new HashMap<>();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be == null) continue;
                    BlockState state = level.getBlockState(cursor);
                    Block block = state.getBlock();
                    results.computeIfAbsent(block, b -> new ArrayList<>()).add(cursor.immutable());
                }
            }
        }
        return results;
    }

    // ----------------------------------------------------------------------------------------------------
    // UI construction
    // ----------------------------------------------------------------------------------------------------
    private static Page buildPage(ServerPlayer player, Map<Block, List<BlockPos>> data, int radius) {
        List<Map.Entry<Block, List<BlockPos>>> entries = new ArrayList<>(data.entrySet());
        entries.sort(Comparator.<Map.Entry<Block, List<BlockPos>>>comparingInt(e -> e.getValue().size()).reversed()
                .thenComparing(e -> getBlockId(player, e.getKey()).toString()));

        int rows = Math.min(6, Math.max(1, (int) Math.ceil(entries.size() / 9.0)));
        ChestTemplate.Builder tmpl = ChestTemplate.builder(rows);

        int slot = 0;
        for (Map.Entry<Block, List<BlockPos>> e : entries) {
            if (slot >= rows * 9) break; // no pagination yet
            tmpl.set(slot++, buildButton(player, e.getKey(), e.getValue()));
        }
        // fill remainder with glass so layout is stable
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (; slot < rows * 9; slot++) {
            tmpl.set(slot, GooeyButton.of(filler));
        }

        return GooeyPage.builder()
                .title(Component.translatable("tilefinder.server.ui_title", radius))
                .template(tmpl.build())
                .build();
    }

    private static GooeyButton buildButton(ServerPlayer player, Block block, List<BlockPos> positions) {
        ItemStack display = new ItemStack(block.asItem());
        int count = positions.size();
        display.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(count + "x " + getPrettyName(block)).withStyle(ChatFormatting.AQUA));

        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            if (action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_LEFT_CLICK || action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_RIGHT_CLICK) {
                teleportToNearest(sp, positions);
            } else {
                ParticleHighlighter.highlight(sp, positions);
            }
        };

        return GooeyButton.builder().display(display).onClick(click).build();
    }

    private static void teleportToNearest(ServerPlayer player, List<BlockPos> positions) {
        BlockPos from = player.blockPosition();
        Optional<BlockPos> nearest = positions.stream().min(Comparator.comparingDouble(p -> p.distSqr(from)));
        nearest.ifPresent(pos -> player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.getYRot(), player.getXRot()));
    }

    private static ResourceLocation getBlockId(ServerPlayer player, Block block) {
        RegistryAccess access = player.registryAccess();
        return access.registryOrThrow(Registries.BLOCK).getKey(block);
    }

    private static String getPrettyName(Block block) {
        return block.getName().getString();
    }

    // ----------------------------------------------------------------------------------------------------
    // Particle highlighting
    // ----------------------------------------------------------------------------------------------------
    public static final class ParticleHighlighter {
        private ParticleHighlighter() {}

        public static void highlight(ServerPlayer source, List<BlockPos> targets) {
            ServerLevel level = source.serverLevel();
            for (BlockPos pos : targets) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 1.1;
                double z = pos.getZ() + 0.5;
                level.sendParticles(source, net.minecraft.core.particles.ParticleTypes.END_ROD, true, x, y, z, 20, 0.2, 0.4, 0.2, 0.0);
            }
            source.displayClientMessage(Component.translatable("tilefinder.server.highlighted", targets.size()), false);
        }
    }
} 