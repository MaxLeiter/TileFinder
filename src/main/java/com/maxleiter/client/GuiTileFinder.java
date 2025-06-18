package com.maxleiter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Collections;
import java.io.IOException;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import java.net.URI;
import java.awt.Desktop;
import java.util.HashSet;
import java.util.Set;
import com.maxleiter.common.TileFinderConfig;

/**
 * Tile Finder main GUI – lists nearby tile entities with advanced grouping,
 * filtering (`@modid` supported), scroll-bar, icon preview, and modern UI.
 */
public class GuiTileFinder extends GuiScreen {

    // ---------------- configuration ----------------
    private int radius = 32; // scan radius (modifiable with +- buttons)
    private static final int PAD = 16; // UI padding

    // ---------------- state ----------------
    private final List<TileEntry> tiles = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private GuiTextField filterField;
    private Map<String, Integer> nameCollisionCount = new HashMap<>();
    private Map<String, Set<String>> nameToMods = new HashMap<>();
    // search history
    private static final Deque<String> history = new ArrayDeque<>();
    private int histIndex = -1;

    // suggestion caches
    private Set<String> modSuggestions = new HashSet<>();
    private Set<String> nameSuggestions = new HashSet<>();

    // favourites by block registry name
    private static final Set<String> favorites = TileFinderConfig.favorites; // will store dim:x:y:z

    private enum GroupMode {
        NONE, FAV, NAME, MODID
    }

    private enum SortMode {
        DISTANCE, NAME, MODID
    }

    private static SortMode lastSort = SortMode.DISTANCE;
    private SortMode sortMode = lastSort;

    private static GroupMode lastGroupMode = GroupMode.NONE; // remember between GUI opens
    private GroupMode groupMode = lastGroupMode;

    // ---------------- init ----------------
    @Override
    public void initGui() {
        try {
            Class.forName("org.lwjgl.input.Keyboard")
                    .getMethod("enableRepeatEvents", boolean.class).invoke(null, true);
        } catch (Throwable ignored) {
        }

        filterField = new GuiTextField(100, this.fontRenderer, PAD, PAD, 120, 15);
        filterField.setMaxStringLength(50);
        filterField.setFocused(false);

        refreshTileList();
        buildButtons();
    }

    private void buildButtons() {
        buttonList.clear();
        // Done
        buttonList.add(new ChipButton(0, width / 2 - 40, height - 30, 80, 18, "Done"));
        int btnY = PAD;
        // radius – / +
        buttonList.add(new ChipButton(1, 140, btnY, 18, 14, "-"));
        buttonList.add(new ChipButton(2, 162, btnY, 18, 14, "+"));
        // group cycle
        buttonList.add(new ChipButton(3, 190, btnY, 70, 14, "Group: " + groupMode.name()));
        // sort cycle button
        int sortW = fontRenderer.getStringWidth("Sort: " + sortMode.name()) + 12;
        buttonList.add(new ChipButton(4, 270, btnY, sortW, 14, "Sort: " + sortMode.name()));
        // GitHub link button top right
        String linkLabel = "TileFinder ↗";
        int linkW = fontRenderer.getStringWidth(linkLabel) + 8;
        buttonList.add(new ChipButton(5, width - linkW - PAD, btnY, linkW, 14, linkLabel));
    }

    // ---------------- tile list building ----------------
    private void refreshTileList() {
        tiles.clear();
        if (mc.player == null) {
            return;
        }
        BlockPos playerPos = mc.player.getPosition();
        String filterRaw = filterField != null ? filterField.getText() : "";
        String filter = filterRaw.toLowerCase();
        String modFilter = filter.startsWith("@") ? filter.substring(1).toLowerCase() : null;
        final String modFilterFinal = modFilter; // lambda capture

        List<TileEntity> raw = Minecraft.getMinecraft().world.loadedTileEntityList.stream()
                .filter(te -> te.getPos().distanceSq(playerPos) <= radius * radius)
                .filter(te -> !isSecondaryChest(te))
                .filter(te -> groupMode != GroupMode.FAV
                        || favorites.contains(posKey(new TileEntry(te, 1, null))))
                .collect(Collectors.toList());

        boolean autoGroupLarge = groupMode == GroupMode.NONE;

        if (groupMode == GroupMode.NAME || groupMode == GroupMode.MODID || autoGroupLarge) {
            java.util.function.Function<TileEntity, String> keyFunc = groupMode == GroupMode.MODID
                    ? te -> te.getBlockType().getRegistryName().getNamespace()
                    : GuiUtils::getDisplayName;

            raw.stream().collect(Collectors.groupingBy(keyFunc))
                    .forEach((name, list) -> {
                        TileEntity closest = list.stream()
                                .min(Comparator.comparingDouble(te -> te.getPos().distanceSq(playerPos)))
                                .orElse(null);
                        if (closest != null) {
                            int cnt = list.size();
                            if (autoGroupLarge && cnt < 20 && groupMode == GroupMode.NONE) { // don't compress small
                                                                                             // groups
                                list.forEach(te -> {
                                    TileEntry e = new TileEntry(te, 1, null);
                                    if (testFilters(e, filter, modFilterFinal))
                                        tiles.add(e);
                                });
                            } else {
                                String label = null;
                                if (groupMode == GroupMode.MODID) {
                                    String modFriendly = lookupModNameStatic(name);
                                    label = modFriendly + " (" + name + ")";
                                }
                                TileEntry entry = new TileEntry(closest, cnt, label);
                                if (testFilters(entry, filter, modFilterFinal))
                                    tiles.add(entry);
                            }
                        }
                    });
        } else {
            raw.forEach(te -> {
                TileEntry entry = new TileEntry(te, 1, null);
                if (testFilters(entry, filter, modFilterFinal))
                    tiles.add(entry);
            });
        }

        switch (sortMode) {
            case DISTANCE:
                tiles.sort(Comparator.comparingDouble(e -> e.distance));
                break;
            case NAME:
                tiles.sort(Comparator.comparing(e -> e.blockName.toLowerCase()));
                break;
            case MODID:
                tiles.sort(Comparator.comparing(e -> e.modid));
                break;
        }

        // build collision map for display later
        nameCollisionCount.clear();
        nameToMods.clear();
        for (TileEntry e : tiles) {
            nameCollisionCount.merge(e.blockName, 1, Integer::sum);
            nameToMods.computeIfAbsent(e.blockName, k -> new HashSet<>()).add(e.modid);
        }

        int listTop = PAD + 24;
        int listBottom = this.height - 50;
        int listHeight = listBottom - listTop;
        this.maxScroll = Math.max(0, tiles.size() * 20 - listHeight);
        this.scrollOffset = Math.min(scrollOffset, maxScroll);

        // suggestions
        modSuggestions.clear();
        nameSuggestions.clear();
        raw.forEach(te -> {
            modSuggestions.add(te.getBlockType().getRegistryName().getNamespace());
            nameSuggestions.add(GuiUtils.getDisplayName(te));
        });
    }

    private boolean testFilters(TileEntry entry, String text, String mod) {
        if (mod != null) { // mod filter overrides text filter
            return entry.modid.toLowerCase().contains(mod);
        }
        return entry.blockName.toLowerCase().contains(text);
    }

    // ---------------- event handling ----------------
    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        switch (btn.id) {
            case 0:
                this.mc.displayGuiScreen(null);
                break;
            case 1:
                if (radius > 8)
                    radius -= 8;
                refreshTileList();
                break;
            case 2:
                if (radius < 128)
                    radius += 8;
                refreshTileList();
                break;
            case 3:
                groupMode = (groupMode == GroupMode.NONE) ? GroupMode.FAV
                        : (groupMode == GroupMode.FAV) ? GroupMode.NAME
                                : (groupMode == GroupMode.NAME) ? GroupMode.MODID : GroupMode.NONE;
                lastGroupMode = groupMode;
                btn.displayString = "Group: " + groupMode.name();
                refreshTileList();
                break;
            case 4:
                sortMode = (sortMode == SortMode.DISTANCE) ? SortMode.NAME
                        : (sortMode == SortMode.NAME) ? SortMode.MODID : SortMode.DISTANCE;
                lastSort = sortMode;
                btn.displayString = "Sort: " + sortMode.name();
                btn.width = fontRenderer.getStringWidth(btn.displayString) + 12;
                refreshTileList();
                break;
            case 5:
                mc.displayGuiScreen(new GuiConfirmOpenLink((result, id) -> {
                    mc.displayGuiScreen(result ? this : null);
                    if (result)
                        try {
                            Desktop.getDesktop().browse(URI.create("https://github.com/maxleiter/tilefinder"));
                        } catch (Exception ignored) {
                        }
                }, "https://github.com/maxleiter/tilefinder", 0, true));
                break;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = 0;
        try {
            delta = (Integer) Class.forName("org.lwjgl.input.Mouse").getMethod("getDWheel").invoke(null);
        } catch (Throwable ignored) {
        }
        if (delta != 0) {
            scrollOffset -= Integer.signum(delta) * 12;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        // Draw labels
        this.fontRenderer.drawString("Filter:", PAD, PAD - 10, 0xFFFFFF);
        this.fontRenderer.drawString("Rad: " + radius, 140, PAD - 10, 0xFFFFFF);

        this.filterField.drawTextBox();

        int listTop = PAD + 24;
        int listBottom = this.height - 50;

        // shaded list background
        drawGradientRect(PAD - 6, listTop - 2, this.width - PAD + 6, listBottom, 0x66000000, 0x66000000);

        int y = listTop - scrollOffset;
        int idx = 0;
        RenderItem itemRender = mc.getRenderItem();
        for (TileEntry entry : tiles) {
            int lineHeight = 16;
            if (y + lineHeight > listTop && y < listBottom) {
                int cardLeft = PAD - 4;
                int cardRight = this.width - PAD + 4;
                int cardBottom = y + lineHeight + 4;
                int bgColorTop = 0x66000000; // translucent dark
                int bgColorBottom = 0x66000000;
                // hover effect
                boolean hover = mouseX >= cardLeft && mouseX <= cardRight && mouseY >= y && mouseY <= cardBottom;
                if (hover) {
                    int col = hover ? 0xAA3366FF : 0x66000000;
                    bgColorTop = col;
                    bgColorBottom = col;
                }
                drawGradientRect(cardLeft, y, cardRight, cardBottom, bgColorTop, bgColorBottom);

                // Icon
                if (!entry.icon.isEmpty()) {
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(entry.icon, cardLeft + 4, y + 2);
                    RenderHelper.disableStandardItemLighting();
                }

                int textX = cardLeft + 24;
                boolean collideAcrossMods = nameToMods.getOrDefault(entry.blockName, Collections.emptySet()).size() > 1;
                String namePart = entry.blockName + (collideAcrossMods ? " (" + entry.modName + ")" : "")
                        + (entry.count > 1 ? " x" + entry.count : "");
                this.fontRenderer.drawString(namePart, textX, y + 2, 0xFFFFFF);
                String coordPart = String.format("(%d,%d,%d) %.1fm", entry.pos.getX(), entry.pos.getY(),
                        entry.pos.getZ(), entry.distance);
                this.fontRenderer.drawString(coordPart, textX, y + 11, 0xAAAAAA);

                // star icon region
                int starX = cardRight - 18;
                int starY = y + 0;
                boolean isFav = favorites.contains(posKey(entry));
                GlStateManager.pushMatrix();
                GlStateManager.translate(starX, starY, 0);
                GlStateManager.scale(1.6f, 1.6f, 1);
                drawString(fontRenderer, isFav ? "★" : "☆", 0, 0, isFav ? 0xFFFF55 : 0xFFFFFF);
                GlStateManager.popMatrix();
            }
            y += 20;
            idx++;
        }
        // draw scrollbar
        if (maxScroll > 0) {
            int barHeight = Math.max(10,
                    (listBottom - listTop) * (listBottom - listTop) / (listBottom - listTop + maxScroll));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (listBottom - listTop - barHeight));
            drawGradientRect(this.width - PAD / 2, barY, this.width - PAD / 2 + 4, barY + barHeight, 0x99FFFFFF,
                    0x99FFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.filterField.updateCursorCounter();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.filterField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0)
            return; // left click only

        int listTop = PAD + 24;
        int listBottom = this.height - 50;
        if (mouseY < listTop || mouseY > listBottom) {
            return;
        }

        int relativeY = mouseY - listTop + scrollOffset;
        if (relativeY < 0)
            return;
        int index = relativeY / 20;

        // star toggle first
        int cardLeft = PAD - 4;
        int cardRight = width - PAD + 4;
        int starStart = cardRight - 20;
        int starEnd = cardRight - 4;
        int rowY = PAD + 24 - scrollOffset + index * 20;
        if (mouseX >= starStart && mouseX <= starEnd && mouseY >= rowY && mouseY <= rowY + 16) {
            TileEntry entry = tiles.get(index);
            String key = posKey(entry);
            if (favorites.contains(key))
                favorites.remove(key);
            else
                favorites.add(key);
            com.maxleiter.common.TileFinderConfig.saveFavorites();
            refreshTileList();
            return;
        }

        // proceed with normal selection
        if (index >= 0 && index < tiles.size()) {
            TileEntry entry = tiles.get(index);
            if (entry.count > 1 && groupMode != GroupMode.NONE) {
                // drill down
                if (groupMode == GroupMode.MODID) {
                    filterField.setText("@" + entry.modid.toLowerCase());
                } else {
                    filterField.setText(entry.blockName);
                }
                groupMode = GroupMode.NONE;
                ((ChipButton) this.buttonList.get(3)).displayString = "Group: NONE";
                refreshTileList();
                return;
            }
            PathHighlighter.highlightTo(entry.pos);

            // close GUI after selection
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Up/down history navigation (200/208)
        if (keyCode == 200 || keyCode == 208) {
            if (history.isEmpty())
                return;
            if (histIndex == -1) {
                histIndex = history.size();
            }
            histIndex += (keyCode == 200 ? -1 : 1);
            if (histIndex < 0)
                histIndex = 0;
            if (histIndex >= history.size())
                histIndex = history.size() - 1;
            String val = new ArrayList<>(history).get(histIndex);
            filterField.setText(val);
            refreshTileList();
            return;
        }

        // Tab completion (15)
        if (keyCode == 15) {
            String txt = filterField.getText();
            if (txt.startsWith("@")) {
                String sub = txt.substring(1).toLowerCase();
                for (String m : modSuggestions) {
                    if (m.toLowerCase().startsWith(sub)) {
                        filterField.setText("@" + m);
                        break;
                    }
                }
            } else {
                for (String n : nameSuggestions) {
                    if (n.toLowerCase().startsWith(txt.toLowerCase())) {
                        filterField.setText(n);
                        break;
                    }
                }
            }
            refreshTileList();
            return;
        }

        if (filterField.textboxKeyTyped(typedChar, keyCode))
            refreshTileList();
        else
            super.keyTyped(typedChar, keyCode);

        // store history on Enter (28)
        if (keyCode == 28) {
            String txt = filterField.getText();
            if (!txt.isEmpty() && (history.isEmpty() || !history.peekLast().equals(txt)))
                history.addLast(txt);
            if (history.size() > 20)
                history.removeFirst();
            histIndex = -1;
        }
    }

    private boolean isSecondaryChest(TileEntity te) {
        if (!(te instanceof TileEntityChest))
            return false;
        TileEntityChest chest = (TileEntityChest) te;

        // Check adjacent positions for double chest
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos adjacent = chest.getPos().offset(facing);
            TileEntity other = chest.getWorld().getTileEntity(adjacent);
            if (other instanceof TileEntityChest) {
                TileEntityChest otherChest = (TileEntityChest) other;
                // If there's a chest to the west or north, consider this the secondary
                if (facing == EnumFacing.WEST || facing == EnumFacing.NORTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String lookupModNameStatic(String id) {
        return GuiUtils.lookupModName(id);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private String posKey(TileEntry e) {
        return mc.world.provider.getDimension() + ":" + e.pos.getX() + ":" + e.pos.getY() + ":" + e.pos.getZ();
    }
}