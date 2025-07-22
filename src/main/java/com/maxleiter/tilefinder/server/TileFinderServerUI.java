package com.maxleiter.tilefinder.server;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

// NBT inspection
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Server-side (vanilla friendly) TileFinder UI backed by GooeyLibs.
 * <p>
 * Shows all discovered block entity types within a radius. Clicking shows a nested list of the
 * specific coordinates for that block type when multiple exist. From the nested list you can
 * highlight or (if OP) teleport to an individual instance. Shift-clicking in the top-level list
 * attempts to teleport to the nearest instance of that block type (falling back to highlight if not OP).
 * <p>
 * Feature set:
 * • Optional filter (substring match on registry name or translated name) supplied via command.
 * • Paginates results (45 entries/page) with Prev/Next nav buttons.
 * • Clear Filter button when a filter is active.
 */
public final class TileFinderServerUI {
    private TileFinderServerUI() {}

    public static void open(@NotNull ServerPlayer player, int radius) {
        open(player, radius, null);
    }

    public static void open(@NotNull ServerPlayer player, int radius, @Nullable String filterRaw) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        Map<Block, List<BlockPos>> found = scan(level, center, radius);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.literal("No block entities found within " + radius + " blocks.").withStyle(ChatFormatting.RED), false);
            return;
        }

        String filter = (filterRaw == null || filterRaw.isBlank()) ? null : filterRaw.trim();
        if (filter != null) {
            found = applyFilter(player, found, filter);
            if (found.isEmpty()) {
                player.displayClientMessage(Component.literal("No block entities found within " + radius + " blocks.").withStyle(ChatFormatting.RED), false);
                return;
            }
        }

        Page first = buildPages(player, found, radius, filter);
        UIManager.openUIForcefully(player, first);
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
    // Filtering
    // ----------------------------------------------------------------------------------------------------
    private static Map<Block, List<BlockPos>> applyFilter(ServerPlayer player, Map<Block, List<BlockPos>> input, String filterRaw) {
        String needle = filterRaw.toLowerCase(Locale.ROOT);
        boolean domainOnly = needle.startsWith("@");
        if (domainOnly) needle = needle.substring(1); // @modid syntax => domain match

        Map<Block, List<BlockPos>> out = new LinkedHashMap<>();
        for (Map.Entry<Block, List<BlockPos>> e : input.entrySet()) {
            Block block = e.getKey();
            ResourceLocation id = getBlockId(player, block);
            String name = getPrettyName(block).toLowerCase(Locale.ROOT);
            String domain = id.getNamespace().toLowerCase(Locale.ROOT);
            String path = id.getPath().toLowerCase(Locale.ROOT);

            boolean match;
            if (domainOnly) {
                match = domain.contains(needle);
            } else {
                match = domain.contains(needle) || path.contains(needle) || name.contains(needle);
            }
            if (match) out.put(block, e.getValue());
        }
        return out;
    }

    // ----------------------------------------------------------------------------------------------------
    // UI construction (paginated top-level list)
    // ----------------------------------------------------------------------------------------------------
    private static Page buildPages(ServerPlayer player, Map<Block, List<BlockPos>> data, int radius, @Nullable String filter) {
        List<Map.Entry<Block, List<BlockPos>>> entries = new ArrayList<>(data.entrySet());
        entries.sort(Comparator.<Map.Entry<Block, List<BlockPos>>>comparingInt(e -> e.getValue().size()).reversed()
                .thenComparing(e -> getBlockId(player, e.getKey()).toString()));

        final int pageSize = 5 * 9; // 5 content rows; row 6 reserved for nav
        int totalPages = (int) Math.ceil(entries.size() / (double) pageSize);
        List<LinkedPage> pages = new ArrayList<>(totalPages);

        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int from = pageIndex * pageSize;
            int to = Math.min(entries.size(), from + pageSize);
            List<Map.Entry<Block, List<BlockPos>>> slice = entries.subList(from, to);

            ChestTemplate.Builder tmpl = ChestTemplate.builder(6); // always 6 rows for nav row

            // content slots 0..44
            int slot = 0;
            for (Map.Entry<Block, List<BlockPos>> e : slice) {
                tmpl.set(slot++, buildTopLevelButton(player, radius, filter, e.getKey(), e.getValue()));
            }
            // fill remainder of content area with filler
            for (; slot < pageSize; slot++) {
                tmpl.set(slot, GooeyButton.of(filler));
            }

            // nav row slots 45..53
            int rowStart = pageSize;
            // prev at 45 (if not first)
            tmpl.set(rowStart + 0, (pageIndex == 0) ? GooeyButton.of(filler) : buildNavButton(LinkType.Previous));
            // summary filter display at middle slot 49
            tmpl.set(rowStart + 4, buildFilterButton(player, radius, filter));
            // next at 52 (if not last)
            tmpl.set(rowStart + 7, (pageIndex == totalPages - 1) ? GooeyButton.of(filler) : buildNavButton(LinkType.Next));
            // help at 53
            tmpl.set(rowStart + 8, buildHelpButtonTop(player, radius, filter));

            // rest nav row filler for any slots we didn't explicitly populate above
            for (int i = 0; i < 9; i++) {
                int navSlot = rowStart + i;
                if (i == 0 || i == 4 || i == 7 || i == 8) continue; // already set
                tmpl.set(navSlot, GooeyButton.of(filler));
            }

            Component title = (totalPages > 1)
                    ? Component.literal("TileFinder (" + radius + ") {current}/{total}")
                    : Component.literal("TileFinder (" + radius + ")");

            LinkedPage page = LinkedPage.builder()
                    .template(tmpl.build())
                    .title(title)
                    .build();
            pages.add(page);
        }

        // link them
        for (int i = 0; i < pages.size(); i++) {
            LinkedPage prev = (i > 0) ? pages.get(i - 1) : null;
            LinkedPage next = (i < pages.size() - 1) ? pages.get(i + 1) : null;
            pages.get(i).setPrevious(prev);
            pages.get(i).setNext(next);
        }

        return pages.get(0);
    }

    private static GooeyButton buildNavButton(LinkType type) {
        ItemStack icon = new ItemStack(type == LinkType.Previous ? Items.ARROW : Items.SPECTRAL_ARROW);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(type == LinkType.Previous ? "Prev" : "Next"));
        return LinkedPageButton.builder().display(icon).linkType(type).build();
    }

    private static GooeyButton buildFilterButton(ServerPlayer opener, int radius, @Nullable String filter) {
        // shows current filter; clicking clears if filter active
        ItemStack icon = new ItemStack(filter == null ? Items.PAPER : Items.BARRIER);
        Component name = (filter == null)
                ? Component.literal("Filter: *").withStyle(ChatFormatting.GRAY)
                : Component.literal("Clear Filter").withStyle(ChatFormatting.RED);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);

        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            // reopen with cleared filter if one was active; otherwise no-op
            if (filter != null) {
                open(sp, radius, null);
            }
        };
        return GooeyButton.builder().display(icon).onClick(click).build();
    }

    private static GooeyButton buildHelpButtonTop(ServerPlayer opener, int radius, @Nullable String filter) {
        ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Help")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA))
                        .append(Component.literal("\nClick block: 3s path")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))
                        .append(Component.literal("\nShift block: keep path (/tilefinder clear)")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal("\nClick multi: open list")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))));
        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            sp.displayClientMessage(Component.literal("TileFinder Help:"), false);
            sp.displayClientMessage(Component.literal(" - Click: 3s path to target"), false);
            sp.displayClientMessage(Component.literal(" - Shift: persistent path until /tilefinder clear"), false);
            sp.displayClientMessage(Component.literal(" - Click multi entry: open list of coords"), false);
            sp.displayClientMessage(Component.literal(" - /tilefinder clear to cancel path"), false);
        };
        return GooeyButton.builder().display(icon).onClick(click).build();
    }

    /**
     * Top-level button representing a block type.
     */
    private static GooeyButton buildTopLevelButton(ServerPlayer player, int radius, @Nullable String filter, Block block, List<BlockPos> positions) {
        ItemStack display = new ItemStack(block.asItem());
        int count = positions.size();
        String label = count + "x " + getPrettyName(block);
        if (count > 1) label += " (click)";
        display.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(label)
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA))
                        .append(statusForComponent(player, positions.isEmpty()?player.blockPosition():positions.get(0)))
        );

        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            if (action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_LEFT_CLICK || action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_RIGHT_CLICK) {
                // Shift from top-level: start a persistent path to nearest instance (no teleport)
                ServerPathHighlighter.highlightPath(sp, getNearest(sp, positions));
            } else {
                if (positions.size() <= 1) {
                    ServerPathHighlighter.highlightPathForDuration(sp, positions.get(0), 60); // temp 3s
                } else {
                    Page first = buildPositionPages(sp, radius, filter, block, positions);
                    UIManager.openUIForcefully(sp, first);
                }
            }
        };

        return GooeyButton.builder().display(display).onClick(click).build();
    }

    // ----------------------------------------------------------------------------------------------------
    // Nested per-position pages
    // ----------------------------------------------------------------------------------------------------
    private static Page buildPositionPages(ServerPlayer opener, int radius, @Nullable String filter, Block block, List<BlockPos> positions) {
        // sort by distance from opener
        BlockPos from = opener.blockPosition();
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(p -> p.distSqr(from)));

        final int pageSize = 5 * 9; // keep same layout
        int totalPages = (int) Math.ceil(sorted.size() / (double) pageSize);
        List<LinkedPage> pages = new ArrayList<>(totalPages);

        ItemStack filler = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
        filler.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int fromIdx = pageIndex * pageSize;
            int toIdx = Math.min(sorted.size(), fromIdx + pageSize);
            List<BlockPos> slice = sorted.subList(fromIdx, toIdx);

            ChestTemplate.Builder tmpl = ChestTemplate.builder(6);
            int slot = 0;
            for (BlockPos pos : slice) {
                tmpl.set(slot++, buildPositionButton(opener, block, pos));
            }
            for (; slot < pageSize; slot++) {
                tmpl.set(slot, GooeyButton.of(filler));
            }

            // nav row: back to main at slot 45; prev/next at 48/50? Actually reuse prev/next outer; we put Back middle
            int rowStart = pageSize;
            tmpl.set(rowStart + 0, buildBackButton(opener, radius, filter));
            tmpl.set(rowStart + 4, (pageIndex == 0) ? GooeyButton.of(filler) : buildNavButton(LinkType.Previous));
            tmpl.set(rowStart + 7, (pageIndex == totalPages - 1) ? GooeyButton.of(filler) : buildNavButton(LinkType.Next));
            tmpl.set(rowStart + 8, buildHelpButtonNested(opener, block, radius, filter));
            for (int i = 0; i < 9; i++) {
                int navSlot = rowStart + i;
                if (i == 0 || i == 4 || i == 7 || i == 8) continue;
                tmpl.set(navSlot, GooeyButton.of(filler));
            }

            Component title = (totalPages > 1)
                    ? Component.literal(getPrettyName(block) + " {current}/{total}")
                    : Component.literal(getPrettyName(block));

            LinkedPage page = LinkedPage.builder().template(tmpl.build()).title(title).build();
            pages.add(page);
        }

        // link pages
        for (int i = 0; i < pages.size(); i++) {
            LinkedPage prev = (i > 0) ? pages.get(i - 1) : null;
            LinkedPage next = (i < pages.size() - 1) ? pages.get(i + 1) : null;
            pages.get(i).setPrevious(prev);
            pages.get(i).setNext(next);
        }

        return pages.get(0);
    }

    private static GooeyButton buildHelpButtonNested(ServerPlayer opener, Block block, int radius, @Nullable String filter) {
        ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Help")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA))
                        .append(Component.literal("\nClick coord: 3s path")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))
                        .append(Component.literal("\nShift coord: keep path (/tilefinder clear)")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW))) );
        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            sp.displayClientMessage(Component.literal("TileFinder Help:"), false);
            sp.displayClientMessage(Component.literal(" - Click coordinate: short path to that block"), false);
            sp.displayClientMessage(Component.literal(" - Shift coordinate: keep path until /tilefinder clear"), false);
            sp.displayClientMessage(Component.literal(" - Back arrow to return"), false);
        };
        return GooeyButton.builder().display(icon).onClick(click).build();
    }

    private static GooeyButton buildBackButton(ServerPlayer opener, int radius, @Nullable String filter) {
        ItemStack icon = new ItemStack(Items.BOOK);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(ChatFormatting.YELLOW));
        return GooeyButton.builder().display(icon).onClick(action -> open(opener, radius, filter)).build();
    }

    private static Component statusForComponent(ServerPlayer player, BlockPos pos) {
        String summary = statusSummary(player, pos);
        if (summary.isEmpty()) return Component.empty();
        return Component.literal("\n" + summary).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY));
    }

    private static String statusSummary(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return "";
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        // heuristics across common vanilla keys
        int cook = getShortOrInt(tag, "cookTime", "cooking_time_spent");
        int cookTotal = getShortOrInt(tag, "cookTimeTotal", "cooking_total_time");
        int lit = getShortOrInt(tag, "BurnTime", "lit_time_remaining", "lit_time");
        int litTotal = getShortOrInt(tag, "CookTime", "lit_total_time");
        if (cookTotal > 0) {
            int pct = (int)Math.round((cook / (double)cookTotal) * 100.0);
            return "Progress: " + pct + "%";
        }
        if (litTotal > 0) {
            int pct = (int)Math.round((lit / (double)litTotal) * 100.0);
            return "Fuel: " + pct + "%";
        }
        // Inventory size fallback
        if (tag.contains("Items")) {
            ListTag items = tag.getList("Items", 10); // compounds
            return "Slots: " + items.size();
        }
        return "";
    }

    private static int getShortOrInt(CompoundTag tag, String... keys) {
        for (String k : keys) {
            if (tag.contains(k)) {
                try { return tag.getInt(k); } catch (Throwable t) {}
                try { return tag.getShort(k); } catch (Throwable t) {}
            }
        }
        return 0;
    }

    private static GooeyButton buildPositionButton(ServerPlayer opener, Block block, BlockPos pos) {
        ItemStack icon = new ItemStack(block.asItem());
        double dist = Math.sqrt(pos.distSqr(opener.blockPosition()));
        String label = String.format(Locale.ROOT, "%s @ %d %d %d (%.0fm)", getPrettyName(block), pos.getX(), pos.getY(), pos.getZ(), dist);
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(label).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD))
                        .append(statusForComponent(opener, pos))
        );

        Consumer<ButtonAction> click = action -> {
            ServerPlayer sp = (ServerPlayer) action.getPlayer();
            if (action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_LEFT_CLICK || action.getClickType() == ca.landonjw.gooeylibs2.api.button.ButtonClick.SHIFT_RIGHT_CLICK) {
                // Shift: make highlight persistent until /tilefinder clear
                ServerPathHighlighter.highlightPath(sp, pos);
            } else {
                // Click: temp 3s path
                ServerPathHighlighter.highlightPathForDuration(sp, pos, 60); // 3s @20tps
            }
        };
        return GooeyButton.builder().display(icon).onClick(click).build();
    }

    private static BlockPos getNearest(ServerPlayer player, List<BlockPos> positions) {
        BlockPos from = player.blockPosition();
        return positions.stream().min(Comparator.comparingDouble(p -> p.distSqr(from))).orElse(positions.isEmpty() ? null : positions.get(0));
    }

    private static ResourceLocation getBlockId(ServerPlayer player, Block block) {
        RegistryAccess access = player.registryAccess();
        return access.registryOrThrow(Registries.BLOCK).getKey(block);
    }

    private static String getPrettyName(Block block) {
        return block.getName().getString();
    }

    // (legacy) ParticleHighlighter removed - using ServerPathHighlighter now
    /* public static final class ParticleHighlighter {
        private ParticleHighlighter() {}

        public static void highlight(ServerPlayer source, List<BlockPos> targets) {
            ServerLevel level = source.serverLevel();
            for (BlockPos pos : targets) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 1.1;
                double z = pos.getZ() + 0.5;
                level.sendParticles(source, net.minecraft.core.particles.ParticleTypes.END_ROD, true, x, y, z, 20, 0.2, 0.4, 0.2, 0.0);
            }
            source.displayClientMessage(Component.literal("Highlighted " + targets.size() + " blocks"), false);
        }
    } */
} 