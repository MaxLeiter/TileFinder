package com.maxleiter.tilefinder.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.maxleiter.tilefinder.Config;
import com.maxleiter.tilefinder.TileFinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/**
 * Modern rewrite of the TileFinder GUI for NeoForge 1.21.1.
 */
public class TileFinderScreen extends Screen {
    private static final int PAD = 16;

    // State
    private int radius;
    private final List<TileEntry> tiles = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private EditBox filterField;
    private Map<String, Integer> nameCollisionCount = new HashMap<>();
    private Map<String, Set<String>> nameToMods = new HashMap<>();

    // Search history
    private static final Deque<String> history = new ArrayDeque<>();
    private int histIndex = -1;

    // Suggestion caches
    private Set<String> modSuggestions = new HashSet<>();
    private Set<String> nameSuggestions = new HashSet<>();

    // Enums for modes
    private enum GroupMode {
        NONE, FAV, NAME, MODID
    }

    private enum SortMode {
        DISTANCE, NAME, MODID
    }

    private static SortMode lastSort = SortMode.DISTANCE;
    private SortMode sortMode = lastSort;

    private static GroupMode lastGroupMode = GroupMode.NONE;
    private GroupMode groupMode = lastGroupMode;

    public TileFinderScreen() {
        super(Component.translatable("screen.tilefinder.title"));
        this.radius = Config.defaultRadius;
    }

    @Override
    protected void init() {
        filterField = new EditBox(this.font, PAD, PAD, 120, 15, Component.literal("Filter"));
        filterField.setMaxLength(50);
        filterField.setFocused(false);
        this.addRenderableWidget(filterField);

        refreshTileList();
        buildButtons();
    }

    private void buildButtons() {
        // Done button
        this.addRenderableWidget(new ChipButton(width / 2 - 40, height - 30, 80, 18,
                Component.literal("Done"),
                btn -> this.onClose()));

        int btnY = PAD;

        // Radius - / + buttons
        this.addRenderableWidget(new ChipButton(140, btnY, 18, 14,
                Component.literal("-"),
                btn -> {
                    if (radius > 8)
                        radius -= 8;
                    refreshTileList();
                }));

        this.addRenderableWidget(new ChipButton(162, btnY, 18, 14,
                Component.literal("+"),
                btn -> {
                    if (radius < 128)
                        radius += 8;
                    refreshTileList();
                }));

        // Group cycle button
        this.addRenderableWidget(new ChipButton(190, btnY, 70, 14,
                Component.literal("Group: " + groupMode.name()),
                btn -> {
                    groupMode = switch (groupMode) {
                        case NONE -> GroupMode.FAV;
                        case FAV -> GroupMode.NAME;
                        case NAME -> GroupMode.MODID;
                        case MODID -> GroupMode.NONE;
                    };
                    lastGroupMode = groupMode;
                    btn.setMessage(Component.literal("Group: " + groupMode.name()));
                    refreshTileList();
                }));

        // Sort cycle button
        int sortW = font.width("Sort: " + sortMode.name()) + 12;
        this.addRenderableWidget(new ChipButton(270, btnY, sortW, 14,
                Component.literal("Sort: " + sortMode.name()),
                btn -> {
                    sortMode = switch (sortMode) {
                        case DISTANCE -> SortMode.NAME;
                        case NAME -> SortMode.MODID;
                        case MODID -> SortMode.DISTANCE;
                    };
                    lastSort = sortMode;
                    btn.setMessage(Component.literal("Sort: " + sortMode.name()));
                    btn.setWidth(font.width(btn.getMessage()) + 12);
                    refreshTileList();
                }));

        // GitHub link button
        this.addRenderableWidget(new ChipButton(this.width - 70, this.height - 25, 65, 20,
                Component.literal("GitHub"), button -> {
                    try {
                        java.awt.Desktop.getDesktop()
                                .browse(java.net.URI.create("https://github.com/maxleiter/tilefinder"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
    }

    private void refreshTileList() {
        tiles.clear();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        Vec3 playerVec = minecraft.player.position();
        String filterRaw = filterField != null ? filterField.getValue() : "";
        String filter = filterRaw.toLowerCase(Locale.ROOT);
        String modFilter = filter.startsWith("@") ? filter.substring(1) : null;

        List<BlockEntity> raw = new ArrayList<>();

        // Collect all block entities in radius
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        int chunkRadius = (radius >> 4) + 1;

        for (int x = chunkX - chunkRadius; x <= chunkX + chunkRadius; x++) {
            for (int z = chunkZ - chunkRadius; z <= chunkZ + chunkRadius; z++) {
                ChunkAccess chunk = minecraft.level.getChunk(x, z);
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.distSqr(playerPos) > radius * radius)
                        continue;
                    BlockEntity be = minecraft.level.getBlockEntity(pos);
                    if (be == null)
                        continue;
                    if (!isSecondaryChest(be))
                        raw.add(be);
                }
            }
        }

        // Apply grouping
        boolean autoGroupLarge = groupMode == GroupMode.NONE;

        if (groupMode == GroupMode.FAV) {
            // Filter to only favorites
            raw = raw.stream()
                    .filter(be -> Config.favorites.contains(posKey(new TileEntry(be, playerVec))))
                    .collect(Collectors.toList());
        }

        if (groupMode == GroupMode.NAME || groupMode == GroupMode.MODID || autoGroupLarge) {
            Function<BlockEntity, String> keyFunc = groupMode == GroupMode.MODID
                    ? be -> be.getBlockState().getBlock().builtInRegistryHolder().key().location().getNamespace()
                    : GuiUtils::getDisplayName;

            raw.stream().collect(Collectors.groupingBy(keyFunc))
                    .forEach((name, list) -> {
                        BlockEntity closest = list.stream()
                                .min(Comparator.comparingDouble(te -> te.getBlockPos().distSqr(playerPos)))
                                .orElse(null);
                        if (closest != null) {
                            int cnt = list.size();
                            if (autoGroupLarge && cnt < 20 && groupMode == GroupMode.NONE) {
                                // Don't compress small groups
                                list.forEach(be -> {
                                    TileEntry e = new TileEntry(be, playerVec);
                                    if (testFilters(e, filter, modFilter))
                                        tiles.add(e);
                                });
                            } else {
                                // Create grouped entry
                                TileEntry entry = new TileEntry(closest, playerVec) {
                                    final int count = cnt;

                                    @Override
                                    public String toString() {
                                        return super.toString() + " x" + count;
                                    }
                                };
                                if (testFilters(entry, filter, modFilter))
                                    tiles.add(entry);
                            }
                        }
                    });
        } else {
            raw.forEach(be -> {
                TileEntry entry = new TileEntry(be, playerVec);
                if (testFilters(entry, filter, modFilter))
                    tiles.add(entry);
            });
        }

        // Apply sorting
        switch (sortMode) {
            case DISTANCE -> tiles.sort(Comparator.comparingDouble(e -> e.distance));
            case NAME -> tiles.sort(Comparator.comparing(e -> e.blockName.toLowerCase(Locale.ROOT)));
            case MODID -> tiles.sort(Comparator.comparing(e -> e.modId));
        }

        // Build collision maps
        nameCollisionCount.clear();
        nameToMods.clear();
        for (TileEntry e : tiles) {
            nameCollisionCount.merge(e.blockName, 1, Integer::sum);
            nameToMods.computeIfAbsent(e.blockName, k -> new HashSet<>()).add(e.modId);
        }

        // Calculate max scroll
        int listTop = PAD + 24;
        int listBottom = this.height - 50;
        int listHeight = listBottom - listTop;
        this.maxScroll = Math.max(0, tiles.size() * 20 - listHeight);
        this.scrollOffset = Math.min(scrollOffset, maxScroll);

        // Build suggestions
        modSuggestions.clear();
        nameSuggestions.clear();
        raw.forEach(be -> {
            modSuggestions.add(be.getBlockState().getBlock().builtInRegistryHolder().key().location().getNamespace());
            nameSuggestions.add(GuiUtils.getDisplayName(be));
        });
    }

    private boolean testFilters(TileEntry entry, String text, String mod) {
        if (mod != null) {
            return entry.modId.toLowerCase(Locale.ROOT).contains(mod);
        }
        return entry.blockName.toLowerCase(Locale.ROOT).contains(text);
    }

    private boolean isSecondaryChest(BlockEntity be) {
        if (!(be instanceof ChestBlockEntity chest))
            return false;

        // Check adjacent positions for double chest
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = chest.getBlockPos().relative(facing);
            BlockEntity other = chest.getLevel().getBlockEntity(adjacent);
            if (other instanceof ChestBlockEntity) {
                // If there's a chest to the west or north, consider this the secondary
                if (facing == Direction.WEST || facing == Direction.NORTH) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);

        // Draw labels
        gfx.drawString(this.font, "Filter:", PAD, PAD - 10, 0xFFFFFF);
        gfx.drawString(this.font, "Rad: " + radius, 140, PAD - 10, 0xFFFFFF);

        super.render(gfx, mouseX, mouseY, partialTick);

        int listTop = PAD + 24;
        int listBottom = this.height - 50;

        // Shaded list background
        gfx.fill(PAD - 6, listTop - 2, this.width - PAD + 6, listBottom, 0x66000000);

        // Render list items
        int y = listTop - scrollOffset;
        for (TileEntry entry : tiles) {
            int lineHeight = 20;
            if (y + lineHeight > listTop && y < listBottom) {
                int cardLeft = PAD - 4;
                int cardRight = this.width - PAD + 4;
                int cardBottom = y + lineHeight - 2;

                // Hover effect
                boolean hover = mouseX >= cardLeft && mouseX <= cardRight && mouseY >= y && mouseY <= cardBottom;
                int bgColor = hover ? 0xAA3366FF : 0x66000000;
                gfx.fill(cardLeft, y, cardRight, cardBottom, bgColor);

                // Icon
                if (!entry.icon.isEmpty()) {
                    gfx.renderItem(entry.icon, cardLeft + 4, y + 2);
                }

                // Text
                int textX = cardLeft + 24;
                boolean collideAcrossMods = nameToMods.getOrDefault(entry.blockName, Collections.emptySet()).size() > 1;
                String namePart = entry.blockName
                        + (collideAcrossMods ? " (" + GuiUtils.lookupModName(entry.modId) + ")" : "");
                gfx.drawString(this.font, namePart, textX, y + 2, 0xFFFFFF);

                String coordPart = String.format("(%d,%d,%d) %.1fm",
                        entry.pos.getX(), entry.pos.getY(), entry.pos.getZ(), entry.distance);
                gfx.drawString(this.font, coordPart, textX, y + 11, 0xAAAAAA);

                // Star icon
                int starX = cardRight - 18;
                boolean isFav = Config.favorites.contains(posKey(entry));
                gfx.pose().pushPose();
                gfx.pose().translate(starX, y, 0);
                gfx.pose().scale(1.6f, 1.6f, 1);
                gfx.drawString(this.font, isFav ? "★" : "☆", 0, 0, isFav ? 0xFFFF55 : 0xFFFFFF);
                gfx.pose().popPose();
            }
            y += 20;
        }

        // Draw scrollbar
        if (maxScroll > 0) {
            int barHeight = Math.max(10,
                    (listBottom - listTop) * (listBottom - listTop) / (listBottom - listTop + maxScroll));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (listBottom - listTop - barHeight));
            gfx.fill(this.width - PAD / 2, barY, this.width - PAD / 2 + 4, barY + barHeight, 0x99FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0)
            return false; // Left click only

        int listTop = PAD + 24;
        int listBottom = this.height - 50;
        if (mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int relativeY = (int) (mouseY - listTop + scrollOffset);
        if (relativeY < 0)
            return false;
        int index = relativeY / 20;

        if (index >= 0 && index < tiles.size()) {
            TileEntry entry = tiles.get(index);

            // Check if star was clicked
            int cardRight = width - PAD + 4;
            int starStart = cardRight - 20;
            int starEnd = cardRight - 4;
            int rowY = listTop - scrollOffset + index * 20;

            if (mouseX >= starStart && mouseX <= starEnd && mouseY >= rowY && mouseY <= rowY + 16) {
                // Toggle favorite
                String key = posKey(entry);
                if (Config.favorites.contains(key)) {
                    Config.favorites.remove(key);
                } else {
                    Config.favorites.add(key);
                }
                Config.saveFavorites();
                refreshTileList();
                return true;
            }

            // Normal click - highlight and close
            PathHighlighter.setTarget(entry.pos);
            this.onClose();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        if (maxScroll == 0)
            return super.mouseScrolled(mouseX, mouseY, horizontalDelta, verticalDelta);
        scrollOffset -= (int) (verticalDelta * 12);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Up/Down history navigation
        if (keyCode == 264 || keyCode == 265) { // Down/Up arrow keys
            if (!history.isEmpty()) {
                if (histIndex == -1) {
                    histIndex = history.size();
                }
                histIndex += (keyCode == 265 ? -1 : 1);
                histIndex = Math.max(0, Math.min(histIndex, history.size() - 1));
                String val = new ArrayList<>(history).get(histIndex);
                filterField.setValue(val);
                refreshTileList();
                return true;
            }
        }

        // Tab completion
        if (keyCode == 258) { // Tab key
            String txt = filterField.getValue();
            if (txt.startsWith("@")) {
                String sub = txt.substring(1).toLowerCase(Locale.ROOT);
                for (String m : modSuggestions) {
                    if (m.toLowerCase(Locale.ROOT).startsWith(sub)) {
                        filterField.setValue("@" + m);
                        refreshTileList();
                        break;
                    }
                }
            } else {
                for (String n : nameSuggestions) {
                    if (n.toLowerCase(Locale.ROOT).startsWith(txt.toLowerCase(Locale.ROOT))) {
                        filterField.setValue(n);
                        refreshTileList();
                        break;
                    }
                }
            }
            return true;
        }

        // Store history on Enter
        if (keyCode == 257) { // Enter key
            String txt = filterField.getValue();
            if (!txt.isEmpty() && (history.isEmpty() || !history.peekLast().equals(txt))) {
                history.addLast(txt);
                if (history.size() > 20) {
                    history.removeFirst();
                }
            }
            histIndex = -1;
        }

        if (filterField.keyPressed(keyCode, scanCode, modifiers) || filterField.canConsumeInput()) {
            refreshTileList();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (filterField.charTyped(codePoint, modifiers)) {
            refreshTileList();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String posKey(TileEntry e) {
        return minecraft.level.dimension().location() + ":" + e.pos.getX() + ":" + e.pos.getY() + ":" + e.pos.getZ();
    }
}