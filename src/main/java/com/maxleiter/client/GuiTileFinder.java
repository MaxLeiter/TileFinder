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
import net.minecraft.block.BlockChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.inventory.IInventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.maxleiter.client.PathHighlighter;

/**
 * Very simple GUI that lists nearby tile entities in a scrollable text list.
 * Press Escape or the Done button to close.
 */
public class GuiTileFinder extends GuiScreen {

    private int radius = 32; // scan radius (modifiable)

    private final List<TileEntry> tiles = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private GuiTextField filterField;

    private enum GroupMode {
        NONE, NAME, MODID
    }

    private GroupMode groupMode = GroupMode.NONE;

    private static final int PAD = 16; // UI padding

    @Override
    public void initGui() {
        // Enable key repeats if LWJGL keyboard is present (avoids compile-time
        // dependency)
        try {
            Class<?> keyboard = Class.forName("org.lwjgl.input.Keyboard");
            keyboard.getMethod("enableRepeatEvents", boolean.class).invoke(null, true);
        } catch (Throwable ignored) {
        }
        this.filterField = new GuiTextField(100, this.fontRenderer, PAD, PAD, 120, 15);
        this.filterField.setMaxStringLength(50);
        this.filterField.setFocused(false);

        refreshTileList();

        this.buttonList.clear();
        // Done button (chip style)
        this.buttonList.add(new ChipButton(0, this.width / 2 - 40, this.height - 30, 80, 18, "Done"));
        int btnY = PAD;
        this.buttonList.add(new ChipButton(1, 140, btnY, 18, 14, "-"));
        this.buttonList.add(new ChipButton(2, 162, btnY, 18, 14, "+"));
        // Group toggle
        this.buttonList.add(new ChipButton(3, 190, btnY, 70, 14, "Group: NONE"));
    }

    private void refreshTileList() {
        this.tiles.clear();
        if (mc.player == null) {
            return;
        }
        BlockPos playerPos = mc.player.getPosition();
        String filterRaw = filterField != null ? filterField.getText() : "";
        String filter = filterRaw.toLowerCase();
        String modFilter = null;
        if (filter.startsWith("@")) {
            modFilter = filter.substring(1);
        }

        final String modFilterFinal = modFilter;
        List<TileEntity> raw = Minecraft.getMinecraft().world.loadedTileEntityList.stream()
                .filter(te -> te.getPos().distanceSq(playerPos) <= radius * radius)
                .filter(te -> !isSecondaryChest(te)) // filter out secondary chest halves
                .collect(Collectors.toList());

        boolean autoGroupLarge = groupMode == GroupMode.NONE;

        if (groupMode == GroupMode.NAME || groupMode == GroupMode.MODID || autoGroupLarge) {
            java.util.function.Function<TileEntity, String> keyFunc = groupMode == GroupMode.MODID
                    ? te -> te.getBlockType().getRegistryName().getNamespace()
                    : te -> getDisplayName(te);

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
                                    if (e.blockName.toLowerCase().contains(filter))
                                        tiles.add(e);
                                });
                            } else {
                                String label = (groupMode == GroupMode.MODID) ? name : null;
                                TileEntry entry = new TileEntry(closest, cnt, label);
                                if (entry.blockName.toLowerCase().contains(filter))
                                    tiles.add(entry);
                            }
                        }
                    });
        } else {
            raw.forEach(te -> {
                if (modFilterFinal != null) {
                    String ns = te.getBlockType().getRegistryName().getNamespace().toLowerCase();
                    if (!ns.contains(modFilterFinal))
                        return;
                }
                TileEntry entry = new TileEntry(te, 1, null);
                if (modFilterFinal == null && !entry.blockName.toLowerCase().contains(filter))
                    return;
                tiles.add(entry);
            });
        }

        tiles.sort(Comparator.comparingDouble(e -> e.distance));

        int listTop = PAD + 24;
        int listBottom = this.height - 50;
        int listHeight = listBottom - listTop;
        this.maxScroll = Math.max(0, tiles.size() * 20 - listHeight);
        this.scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) { // radius -
            if (radius > 8)
                radius -= 8;
            refreshTileList();
        } else if (button.id == 2) { // radius +
            if (radius < 128)
                radius += 8;
            refreshTileList();
        } else if (button.id == 3) { // cycle grouping mode
            switch (groupMode) {
                case NONE:
                    groupMode = GroupMode.NAME;
                    break;
                case NAME:
                    groupMode = GroupMode.MODID;
                    break;
                case MODID:
                    groupMode = GroupMode.NONE;
                    break;
            }
            button.displayString = "Group: " + groupMode.name();
            refreshTileList();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = 0;
        try {
            Class<?> mouseCls = Class.forName("org.lwjgl.input.Mouse");
            delta = (Integer) mouseCls.getMethod("getDWheel").invoke(null);
        } catch (Throwable ignored) {
        }
        if (delta != 0) {
            scrollOffset -= Integer.signum(delta) * 12;
            if (scrollOffset < 0)
                scrollOffset = 0;
            if (scrollOffset > maxScroll)
                scrollOffset = maxScroll;
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
                    double phase = (System.currentTimeMillis() % 1000L) / 1000.0;
                    int alpha = (int) (0x55 + 0x33 * Math.sin(phase * Math.PI * 2));
                    int col = (alpha << 24) | 0x3366FF;
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
                String namePart = entry.blockName + (entry.count > 1 ? " x" + entry.count : "");
                this.fontRenderer.drawString(namePart, textX, y + 2, 0xFFFFFF);
                String coordPart = String.format("(%d,%d,%d) %.1fm", entry.pos.getX(), entry.pos.getY(),
                        entry.pos.getZ(), entry.distance);
                this.fontRenderer.drawString(coordPart, textX, y + 11, 0xAAAAAA);
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
        if (index >= 0 && index < tiles.size()) {
            TileEntry entry = tiles.get(index);
            if (entry.count > 1) {
                // drill down
                if (groupMode != GroupMode.NONE) {
                    if (groupMode == GroupMode.MODID) {
                        filterField.setText("@" + entry.blockName);
                    } else {
                        filterField.setText(entry.blockName);
                    }
                    groupMode = GroupMode.NONE;
                    ((ChipButton) this.buttonList.get(3)).displayString = "Group: NONE";
                    refreshTileList();
                    return;
                }
            }
            String coord = String.format("%d %d %d", entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
            setClipboardString(coord);
            PathHighlighter.highlightTo(entry.pos);
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§aCopied coords: " + coord));
            }
            // close GUI after selection
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (filterField.textboxKeyTyped(typedChar, keyCode)) {
            refreshTileList();
        } else {
            super.keyTyped(typedChar, keyCode);
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

    private static String getDisplayName(TileEntity te) {
        // 1. If tile entity exposes a display name, use it
        try {
            ITextComponent comp = te.getDisplayName();
            if (comp != null) {
                return comp.getFormattedText();
            }
        } catch (Throwable ignored) {
        }

        // 2. Try ItemStack display name
        Item item = Item.getItemFromBlock(te.getBlockType());
        if (item != null && item != Item.getItemById(0)) {
            return new ItemStack(item).getDisplayName();
        }

        // 3. Fallback: translate block's unlocalized name
        return I18n.translateToLocal(te.getBlockType().getLocalizedName());
    }

    private static class TileEntry {
        String blockName;
        final BlockPos pos;
        final double distance;
        final int count;
        final ItemStack icon;

        TileEntry(TileEntity te, int count, String forcedName) {
            this.blockName = (forcedName != null) ? forcedName : GuiTileFinder.getDisplayName(te);
            this.pos = te.getPos();
            this.distance = Math.sqrt(Minecraft.getMinecraft().player.getPosition().distanceSq(pos));
            this.count = count;

            // Special handling for chests to show proper icon
            if (te instanceof TileEntityChest) {
                // Check if part of double chest
                for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                    BlockPos adjacent = te.getPos().offset(facing);
                    if (te.getWorld().getTileEntity(adjacent) instanceof TileEntityChest) {
                        this.blockName = "Large Chest";
                        break;
                    }
                }
            }

            Item item = Item.getItemFromBlock(te.getBlockType());
            this.icon = (item != null) ? new ItemStack(item) : ItemStack.EMPTY;
        }

        // Convenience constructor keeping previous usages
        TileEntry(TileEntity te, int count) {
            this(te, count, null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * A minimal pill-style button that looks less like vanilla
     * Minecraft: flat colour with subtle hover tint.
     */
    private static class ChipButton extends GuiButton {
        ChipButton(int id, int x, int y, int w, int h, String txt) {
            super(id, x, y, w, h, txt);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible)
                return;
            this.hovered = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
                    && mouseY < this.y + this.height;
            int bg = this.hovered ? 0xFF3C6BFF : 0xFF2E2E2E;
            int fg = 0xFFFFFF;

            GlStateManager.disableTexture2D();
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bg);
            GlStateManager.enableTexture2D();

            int textX = this.x + (this.width - mc.fontRenderer.getStringWidth(this.displayString)) / 2;
            int textY = this.y + (this.height - 8) / 2;
            mc.fontRenderer.drawString(this.displayString, textX, textY, fg);
        }
    }
}