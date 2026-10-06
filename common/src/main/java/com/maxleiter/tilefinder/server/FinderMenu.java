package com.maxleiter.tilefinder.server;

import java.util.ArrayList;
import java.util.List;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.platform.Platform;
import com.maxleiter.tilefinder.scan.Finder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
//? if >=26 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;
*///?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * A six-row chest a vanilla client can show, used as a menu: every slot is a button and nothing can be taken out, put
 * in or moved. Page one lists the groups, page two the places of one group.
 */
public final class FinderMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int PER_PAGE = SIZE - 9;
    private static final int PREVIOUS = PER_PAGE, BACK = PER_PAGE + 1, INFO = PER_PAGE + 4, CLOSE = PER_PAGE + 7, NEXT = PER_PAGE + 8;

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final String filter;
    private final List<Finder.Group> groups;
    private final int totalSpots;
    private Finder.@Nullable Group open;
    private int page;

    FinderMenu(int id, Inventory inventory, ServerPlayer player, int radius, String filter, List<Finder.Group> groups) {
        super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(SIZE), ROWS);
        this.player = player;
        this.level = Mc.serverLevel(player);
        this.center = player.blockPosition();
        this.radius = radius;
        this.filter = filter;
        this.groups = groups;
        this.totalSpots = groups.stream().mapToInt(group -> group.spots().size()).sum();
        render();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && player == this.player && Mc.serverLevel(this.player) == level;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    //? if >=26 {
    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        click(slot, button, input == ContainerInput.PICKUP, input == ContainerInput.QUICK_MOVE, player);
    }
    //?} else {
    /*@Override
    public void clicked(int slot, int button, ClickType input, Player player) {
        click(slot, button, input == ClickType.PICKUP, input == ClickType.QUICK_MOVE, player);
    }
    *///?}

    /**
     * Every click is a button press and nothing else. The default handling is never called, so number-key swaps,
     * double-click collecting, drops, drags and clones can't touch an item; the client's guess at what happened is
     * corrected by the resync.
     */
    private void click(int slot, int button, boolean pickup, boolean shift, Player who) {
        if (who != player || slot < 0 || slot >= SIZE || !(pickup || shift)) {
            resync();
            return;
        }
        boolean right = !shift && button == 1;
        if (slot < PER_PAGE) {
            slotClicked(slot, right, shift);
        } else if (slot == PREVIOUS && page > 0) {
            show(open, page - 1);
        } else if (slot == NEXT && page + 1 < pages()) {
            show(open, page + 1);
        } else if (slot == BACK && open != null) {
            show(null, 0);
        } else if (slot == CLOSE) {
            player.closeContainer();
            return;
        } else if (slot == INFO) {
            boolean had = Waypoints.clear(player.getUUID());
            player.sendSystemMessage(had ? Msg.t("tilefinder.server.cleared", "Stopped pointing the way.")
                    : Msg.t("tilefinder.server.nothing_tracked", "You are not being pointed anywhere."));
        }
        resync();
    }

    private void resync() {
        if (player.containerMenu == this) {
            setCarried(ItemStack.EMPTY);
            sendAllDataToRemote();
        }
    }

    private void slotClicked(int slot, boolean right, boolean shift) {
        int index = page * PER_PAGE + slot;
        Finder.Group group;
        Finder.Spot spot;
        if (open == null) {
            if (index >= groups.size()) return;
            group = groups.get(index);
            spot = group.spots().getFirst();
            if (right && !shift) {
                show(group, 0);
                return;
            }
        } else {
            if (index >= open.spots().size()) return;
            group = open;
            spot = open.spots().get(index);
            if (right && !shift) {
                player.sendSystemMessage(Msg.t("tilefinder.server.at", "%s is at %s.", group.name(), Waypoints.coordinates(spot.pos())));
                return;
            }
        }
        if (shift) teleport(group, spot);
        else point(group, spot);
    }

    private void point(Finder.Group group, Finder.Spot spot) {
        player.closeContainer();
        Waypoints.start(player, spot.pos(), group.name());
    }

    private void teleport(Finder.Group group, Finder.Spot spot) {
        if (!Mc.isGamemaster(player)) {
            player.sendSystemMessage(Msg.t("tilefinder.server.no_teleport", "Only operators can teleport. Click to be pointed the way instead.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos spotPos = spot.pos();
        BlockPos feet = standingSpot(spotPos);
        player.closeContainer();
        if (feet == null) {
            player.sendSystemMessage(Msg.t("tilefinder.server.no_safe_spot", "There is no safe place to stand next to %s.", group.name())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.sendSystemMessage(Msg.t("tilefinder.server.teleported", "Teleported next to %s at %s.", group.name(), Waypoints.coordinates(spotPos)));
    }

    /** A place to stand on top of or beside the block: room for feet and head, no liquid, solid ground. */
    private @Nullable BlockPos standingSpot(BlockPos block) {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(block.above());
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            candidates.add(block.relative(side));
            candidates.add(block.relative(side).above());
            candidates.add(block.relative(side).below());
        }
        candidates.add(block.above(2));
        for (BlockPos feet : candidates) {
            BlockPos head = feet.above();
            BlockPos ground = feet.below();
            if (passable(feet) && passable(head) && !level.getBlockState(ground).getCollisionShape(level, ground, CollisionContext.empty()).isEmpty()
                    && level.getFluidState(ground).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    private boolean passable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private int pages() {
        int entries = open == null ? groups.size() : open.spots().size();
        return Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE);
    }

    private void show(Finder.@Nullable Group group, int newPage) {
        open = group;
        page = newPage;
        render();
    }

    private void render() {
        Container container = getContainer();
        container.clearContent();
        if (open == null) {
            for (int slot = 0; slot < PER_PAGE; slot++) {
                int index = page * PER_PAGE + slot;
                if (index >= groups.size()) break;
                container.setItem(slot, groupItem(groups.get(index)));
            }
        } else {
            for (int slot = 0; slot < PER_PAGE; slot++) {
                int index = page * PER_PAGE + slot;
                if (index >= open.spots().size()) break;
                container.setItem(slot, spotItem(open, open.spots().get(index)));
            }
            container.setItem(BACK, button(Items.ARROW, Msg.t("tilefinder.server.back", "Back to all groups")));
        }
        if (page > 0) container.setItem(PREVIOUS, button(Items.SPECTRAL_ARROW, Msg.t("tilefinder.server.previous", "Previous page")));
        if (page + 1 < pages()) container.setItem(NEXT, button(Items.SPECTRAL_ARROW, Msg.t("tilefinder.server.next", "Next page")));
        container.setItem(CLOSE, button(Items.BARRIER, Msg.t("tilefinder.server.close", "Close")));
        container.setItem(INFO, info());
        broadcastChanges();
    }

    private static ItemStack button(Item item, MutableComponent name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Msg.item(name, ChatFormatting.YELLOW));
        return stack;
    }

    private ItemStack info() {
        int groupCount = groups.size();
        List<Component> lore = new ArrayList<>();
        lore.add(gray(Msg.t("tilefinder.server.info.radius", "Radius: %s blocks", radius)));
        lore.add(gray(Msg.t("tilefinder.server.info.totals", "%s groups, %s places", groupCount, totalSpots)));
        if (!filter.isBlank()) lore.add(gray(Msg.t("tilefinder.server.info.filter", "Filter: %s", filter.trim())));
        lore.add(Component.empty());
        lore.add(Msg.item(Msg.t("tilefinder.server.info.clear", "Click: clear tracking"), ChatFormatting.YELLOW));
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, Msg.item(Msg.t("tilefinder.server.info.title", "TileFinder"), ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static MutableComponent gray(MutableComponent text) {
        return Msg.item(text, ChatFormatting.GRAY);
    }

    private static MutableComponent hint(MutableComponent text) {
        return Msg.item(text, ChatFormatting.DARK_AQUA);
    }

    private ItemStack iconFor(Finder.Group group, int count) {
        ItemStack stack = group.icon().isEmpty() ? new ItemStack(Items.BARRIER) : group.icon().copy();
        stack.setCount(Math.max(1, Math.min(64, count)));
        return stack;
    }

    private ItemStack groupItem(Finder.Group group) {
        ItemStack stack = iconFor(group, group.members());
        stack.set(DataComponents.CUSTOM_NAME, Msg.item(group.name()));
        List<Component> lore = new ArrayList<>();
        int spots = group.spots().size();
        int blocks = group.blocks();
        if (blocks > spots) lore.add(gray(Msg.t("tilefinder.server.group.merged", "×%s (%s blocks)", spots, blocks)));
        else if (spots > 1) lore.add(gray(Msg.t("tilefinder.server.group.count", "×%s", spots)));
        lore.add(gray(Component.literal(modName(group))));
        lore.add(gray(nearest(group.spots().getFirst().pos(), group.nearestSq())));
        lore.add(Component.empty());
        lore.add(hint(Msg.t("tilefinder.server.hint.point", "Click: point the way to the nearest")));
        lore.add(hint(Msg.t("tilefinder.server.hint.list", "Right-click: list each one")));
        lore.add(hint(Msg.t("tilefinder.server.hint.teleport", "Shift-click: teleport to the nearest (operators)")));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private ItemStack spotItem(Finder.Group group, Finder.Spot spot) {
        ItemStack stack = iconFor(group, spot.members().size());
        MutableComponent name = Msg.item(group.name());
        for (Finder.Member member : spot.members()) {
            if (member.customName() != null) {
                name = Msg.t("tilefinder.server.spot.named", "%s \"%s\"", name, member.customName()).withStyle(style -> style.withItalic(false));
                break;
            }
        }
        stack.set(DataComponents.CUSTOM_NAME, name);
        BlockPos pos = spot.pos();
        List<Component> lore = new ArrayList<>();
        lore.add(gray(Msg.t("tilefinder.server.spot.coordinates", "%s, %s, %s", pos.getX(), pos.getY(), pos.getZ())));
        lore.add(gray(nearest(pos, spot.distanceSq())));
        if (spot.members().size() > 1) lore.add(gray(Msg.t("tilefinder.server.spot.network", "A network of %s", spot.members().size())));
        lore.add(Component.empty());
        lore.add(hint(Msg.t("tilefinder.server.hint.point_here", "Click: point the way")));
        lore.add(hint(Msg.t("tilefinder.server.hint.coords", "Right-click: write the coordinates in chat")));
        lore.add(hint(Msg.t("tilefinder.server.hint.teleport_here", "Shift-click: teleport (operators)")));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String modName(Finder.Group group) {
        return Platform.INSTANCE.modName(group.blockId().getNamespace());
    }

    /** "12 blocks NE, 3 above" from where the player stood when they ran the command. */
    private MutableComponent nearest(BlockPos pos, double distanceSq) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        int dy = pos.getY() - center.getY();
        long distance = Math.round(Math.sqrt(distanceSq));
        MutableComponent height = dy == 0 ? Msg.t("tilefinder.server.height.same", "same height")
                : dy > 0 ? Msg.t("tilefinder.server.height.above", "%s above", dy)
                : Msg.t("tilefinder.server.height.below", "%s below", -dy);
        if (dx == 0 && dz == 0) return Msg.t("tilefinder.server.nearest.here", "%s blocks away, %s", distance, height);
        // North is -Z, east is +X.
        int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
        return Msg.t("tilefinder.server.nearest", "%s blocks %s, %s", distance, COMPASS[octant], height);
    }
}
