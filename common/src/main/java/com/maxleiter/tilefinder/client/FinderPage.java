package com.maxleiter.tilefinder.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maxleiter.tilefinder.compat.RecipeViewers;
import com.maxleiter.tilefinder.platform.Platform;
import com.maxleiter.tilefinder.scan.Finder;

import dev.vellum.mod.client.VellumScreen;
import dev.vellum.mod.client.VellumScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The finder screen: a Vellum page (assets/tilefinder/vellum/finder.html) fed with a scan of the block entities
 * around the player. Filtering, sorting and grouping happen in the page; Java rescans when the radius or the merge
 * setting changes, and acts on what the player picks.
 */
public final class FinderPage {
    public static final String URL = "tilefinder:vellum/finder.html";
    /** Spots listed per group; the rest are summed up as "and N more", still trackable through "all". */
    private static final int SPOTS_PER_GROUP = 200;
    private static final int MEMBERS_PER_SPOT = 64;

    /** A group icon on the page, as the page last reported it: the item and its box in GUI pixels. */
    public record Icon(ItemStack stack, int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** The open finder, for the recipe viewers' plugins (see {@link #iconAt}). */
    private static @Nullable FinderPage current;

    private final VellumScreen screen;
    private @Nullable Icon hovered;
    private final Map<String, Finder.Group> groupsByKey = new HashMap<>();
    private int radius;

    private FinderPage(@Nullable String query, String tab, @Nullable Screen parent) {
        Settings settings = Settings.get();
        this.radius = Math.min(settings.radius, maxRadius());
        JsonObject data = new JsonObject();
        data.add("tf", scan(query, tab));
        this.screen = VellumScreens.open(URL, data);
        screen.driver()
                .onMessage("track", this::track)
                .onMessage("fav", this::favorite)
                .onMessage("radius", value -> {
                    radius = Math.max(4, Math.min(maxRadius(), value.getAsInt()));
                    push();
                })
                .onMessage("rescan", value -> push())
                .onMessage("settings", value -> {
                    Settings.apply(value.getAsJsonObject());
                    push();
                })
                .onMessage("recipes", this::recipes)
                .onMessage("hover", this::hover)
                .onMessage("copy", value -> {
                    JsonObject pos = value.getAsJsonObject();
                    McClient.copy(pos.get("x").getAsInt() + " " + pos.get("y").getAsInt() + " " + pos.get("z").getAsInt());
                })
                .onMessage("clear", value -> Tracker.clear())
                .onClose(() -> {
                    if (current == this) current = null;
                    // Back to the screen the finder was opened from (a mod list's config button), unless something
                    // else replaced the finder.
                    if (parent != null) Minecraft.getInstance().execute(() -> {
                        if (McClient.screen() == null) McClient.setScreen(parent);
                    });
                })
                .onKey(event -> {
                    // The key that opened the finder closes it, as E closes the inventory.
                    //? if >=26 {
                    boolean open = Keys.OPEN.matches(event);
                    //?} else
                    /*boolean open = Keys.OPEN.matches(event.key(), event.scancode());*/
                    if (!open) return false;
                    screen.onClose();
                    return true;
                });
        current = this;
    }

    /** Whether {@code screen} is the finder, so the recipe viewers know to ask {@link #iconAt}. */
    public static boolean isFinder(Screen screen) {
        return current != null && current.screen == screen;
    }

    /** The group icon the pointer is over on {@code screen} if that is the finder, else null. */
    public static @Nullable Icon iconAt(Screen screen, double mouseX, double mouseY) {
        if (current == null || current.screen != screen) return null;
        Icon icon = current.hovered;
        return icon != null && icon.contains(mouseX, mouseY) ? icon : null;
    }

    /** Opens the finder. {@code query} prefills the search; {@code tab} is "list" or "settings". */
    public static void open(@Nullable String query, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        new FinderPage(query, tab, null);
    }

    /** Opens the settings tab, also outside a world, returning to {@code parent} when closed. */
    public static void openSettings(@Nullable Screen parent) {
        new FinderPage(null, "settings", parent);
    }

    /** Opens the finder searching for the block {@code stack} places, or the item itself. */
    public static void openFor(ItemStack stack) {
        open("item:" + BuiltInRegistries.ITEM.getKey(stack.getItem()), "list");
    }

    private static int maxRadius() {
        // The client only knows the chunks it has loaded.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Settings.MAX_RADIUS;
        return Math.min(Settings.MAX_RADIUS, mc.options.getEffectiveRenderDistance() * 16);
    }

    private void push() {
        JsonObject data = new JsonObject();
        data.add("tf", scan(null, null));
        screen.driver().merge(data);
    }

    private JsonObject scan(@Nullable String query, @Nullable String tab) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Settings settings = Settings.get();
        List<Finder.Group> groups = player == null || mc.level == null ? List.of()
                : Finder.find(mc.level, player.blockPosition(), new Finder.Options(radius, settings.mergeConnected, settings.kinds()));
        groupsByKey.clear();
        JsonArray groupsJson = new JsonArray();
        for (Finder.Group group : groups) {
            groupsByKey.put(group.key(), group);
            groupsJson.add(groupJson(group, player));
        }
        JsonObject tf = new JsonObject();
        tf.addProperty("radius", radius);
        tf.addProperty("maxRadius", maxRadius());
        tf.add("groups", groupsJson);
        tf.add("settings", settings.toJson());
        tf.addProperty("viewers", RecipeViewers.any());
        tf.addProperty("tracking", Tracker.active());
        tf.addProperty("inWorld", player != null);
        // Each scan gets a new id, so the page can tell a rescan from its own state changing.
        tf.addProperty("scan", System.nanoTime());
        if (query != null) tf.addProperty("query", query);
        if (tab != null) tf.addProperty("tab", tab);
        return tf;
    }

    private JsonObject groupJson(Finder.Group group, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        JsonObject json = new JsonObject();
        json.addProperty("key", group.key());
        json.addProperty("name", group.name().getString());
        json.addProperty("item", group.icon().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(group.icon().getItem()).toString());
        json.addProperty("block", group.blockId().toString());
        String namespace = group.blockId().getNamespace();
        json.addProperty("modId", namespace);
        json.addProperty("mod", Platform.INSTANCE.modName(namespace));
        JsonArray holds = new JsonArray();
        for (Platform.Holds h : group.holds()) holds.add(h.name().toLowerCase(Locale.ROOT));
        json.add("holds", holds);
        json.addProperty("decorative", group.decorative());
        json.addProperty("blocks", group.blocks());
        json.addProperty("count", group.members());
        json.addProperty("nearest", round1(Math.sqrt(group.nearestSq())));

        JsonArray spots = new JsonArray();
        List<Finder.Spot> list = group.spots();
        for (int i = 0; i < Math.min(list.size(), SPOTS_PER_GROUP); i++) spots.add(spotJson(list.get(i), player, eye));
        json.add("spots", spots);
        json.addProperty("more", Math.max(0, list.size() - SPOTS_PER_GROUP));
        return json;
    }

    private JsonObject spotJson(Finder.Spot spot, LocalPlayer player, Vec3 eye) {
        BlockPos pos = spot.pos();
        Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        json.addProperty("d", round1(to.length()));
        json.addProperty("dir", Tracker.arrow(player.getYRot(), to));
        json.addProperty("dy", pos.getY() - player.getBlockY());
        json.addProperty("blocks", spot.blocks());
        json.addProperty("fav", Favorites.contains(player.level().dimension(), pos));
        JsonArray names = new JsonArray();
        JsonArray members = new JsonArray();
        for (int i = 0; i < spot.members().size(); i++) {
            Finder.Member member = spot.members().get(i);
            String name = member.customName() == null ? null : member.customName().getString();
            if (name != null) names.add(name);
            if (spot.members().size() > 1 && i < MEMBERS_PER_SPOT) {
                JsonObject m = new JsonObject();
                m.addProperty("x", member.pos().getX());
                m.addProperty("y", member.pos().getY());
                m.addProperty("z", member.pos().getZ());
                m.addProperty("d", round1(Math.sqrt(eye.distanceToSqr(Vec3.atCenterOf(member.pos())))));
                m.addProperty("parts", member.blocks().size());
                m.addProperty("fav", Favorites.contains(player.level().dimension(), member.pos()));
                if (name != null) m.addProperty("name", name);
                members.add(m);
            }
        }
        json.addProperty("count", spot.members().size());
        json.addProperty("parts", spot.members().getFirst().blocks().size());
        json.add("names", names);
        if (!members.isEmpty()) json.add("members", members);
        return json;
    }

    /**
     * {"group": key} tracks every spot of a group; {"group": key, "spots": [[x, y, z]...]} some of them (what a search
     * left); {"group": key, "x", "y", "z"} one spot (all its members) or one member. The page can be replaced by a
     * resource pack, so positions must belong to the last scan.
     */
    private void track(JsonElement value) {
        JsonObject request = value.getAsJsonObject();
        Finder.Group group = groupsByKey.get(request.get("group").getAsString());
        if (group == null) return;
        List<BlockPos> targets = new ArrayList<>();
        if (request.has("spots")) {
            Set<BlockPos> wanted = new HashSet<>();
            for (JsonElement element : request.getAsJsonArray("spots")) {
                JsonArray xyz = element.getAsJsonArray();
                wanted.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
            }
            for (Finder.Spot spot : group.spots()) {
                if (!wanted.contains(spot.pos())) continue;
                for (Finder.Member member : spot.members()) targets.add(member.pos());
            }
        } else if (request.has("x")) {
            BlockPos pos = new BlockPos(request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
            for (Finder.Spot spot : group.spots()) {
                if (spot.pos().equals(pos)) {
                    for (Finder.Member member : spot.members()) targets.add(member.pos());
                    break;
                }
                for (Finder.Member member : spot.members()) {
                    if (member.pos().equals(pos)) targets.add(pos);
                }
            }
        } else {
            for (Finder.Spot spot : group.spots()) {
                for (Finder.Member member : spot.members()) targets.add(member.pos());
            }
        }
        if (targets.isEmpty()) return;
        String item = group.icon().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(group.icon().getItem()).toString();
        Tracker.track(group.name().getString(), item, targets);
        screen.onClose();
    }

    private void favorite(JsonElement value) {
        JsonObject request = value.getAsJsonObject();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos pos = new BlockPos(request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
        Favorites.toggle(mc.level.dimension(), pos);
        push();
    }

    private void recipes(JsonElement value) {
        JsonObject request = value.getAsJsonObject();
        Finder.Group group = groupsByKey.get(request.get("group").getAsString());
        if (group == null || group.icon().isEmpty()) return;
        boolean uses = request.has("uses") && request.get("uses").getAsBoolean();
        RecipeViewers.show(group.icon().copy(), uses);
    }

    /**
     * {"group": key, "x", "y", "w", "h"} when the pointer enters a group's icon, null when it leaves. The group must
     * belong to the last scan and the box must be plausible: the page can be replaced by a resource pack.
     */
    private void hover(JsonElement value) {
        hovered = null;
        if (value == null || !value.isJsonObject()) return;
        JsonObject request = value.getAsJsonObject();
        for (String field : new String[] {"group", "x", "y", "w", "h"}) {
            if (!request.has(field) || !request.get(field).isJsonPrimitive()) return;
        }
        Finder.Group group = groupsByKey.get(request.get("group").getAsString());
        if (group == null || group.icon().isEmpty()) return;
        double x = request.get("x").getAsDouble();
        double y = request.get("y").getAsDouble();
        double w = request.get("w").getAsDouble();
        double h = request.get("h").getAsDouble();
        if (!(w > 0 && w <= 64 && h > 0 && h <= 64 && Math.abs(x) < 16384 && Math.abs(y) < 16384)) return;
        hovered = new Icon(group.icon().copy(), (int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w), (int) Math.ceil(h));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
