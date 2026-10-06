package com.maxleiter.tilefinder.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonObject;
import com.maxleiter.tilefinder.TileFinder;

import dev.vellum.mod.client.VellumHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the player asked to be led to: one block, or every block of a kind. Draws nothing itself; the beam renderer
 * and the HUD overlay read it. Targets drop off as the player reaches them or they are broken.
 */
public final class Tracker {
    public static final Identifier HUD = TileFinder.id("tracker");
    public static final String HUD_URL = "tilefinder:vellum/tracker.html";
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final List<BlockPos> targets = new ArrayList<>();
    private static @Nullable ResourceKey<Level> dimension;
    private static String label = "";
    private static String item = "";
    private static int total;
    private static @Nullable String lastHud;

    private Tracker() {
    }

    public static void track(String label, String item, List<BlockPos> positions) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || positions.isEmpty()) return;
        targets.clear();
        targets.addAll(positions);
        dimension = mc.level.dimension();
        Tracker.label = label;
        Tracker.item = item;
        total = positions.size();
        lastHud = null;
        if (Settings.get().hud) VellumHud.show(HUD);
        updateHud(mc);
    }

    public static void clear() {
        targets.clear();
        dimension = null;
        lastHud = null;
        if (VellumHud.isShown(HUD)) VellumHud.hide(HUD);
    }

    public static boolean active() {
        return !targets.isEmpty();
    }

    /** Targets nearest the player first. */
    public static List<BlockPos> targets() {
        return targets;
    }

    public static void tick(Minecraft mc) {
        if (targets.isEmpty()) return;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || level.dimension() != dimension) {
            clear();
            return;
        }
        double arrive = Settings.get().arriveDistance;
        Vec3 eye = player.getEyePosition();
        targets.removeIf(pos -> {
            // A broken block stops being a target, but only where the client can tell: its chunk is loaded.
            boolean loaded = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
            if (loaded && level.getBlockEntity(pos) == null) return true;
            return arrive > 0 && eye.distanceToSqr(Vec3.atCenterOf(pos)) <= arrive * arrive;
        });
        if (targets.isEmpty()) {
            clear();
            return;
        }
        targets.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
        updateHud(mc);
    }

    private static void updateHud(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || targets.isEmpty() || !Settings.get().hud) return;
        BlockPos nearest = targets.getFirst();
        Vec3 eye = player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(nearest).subtract(eye);
        JsonObject data = new JsonObject();
        data.addProperty("label", label);
        data.addProperty("item", item);
        data.addProperty("distance", Math.round(to.length()));
        data.addProperty("arrow", arrow(player.getYRot(), to));
        data.addProperty("dy", nearest.getY() - player.getBlockY());
        data.addProperty("left", targets.size());
        data.addProperty("total", total);
        data.addProperty("x", nearest.getX());
        data.addProperty("y", nearest.getY());
        data.addProperty("z", nearest.getZ());
        JsonObject root = new JsonObject();
        root.add("tracker", data);
        String json = root.toString();
        if (json.equals(lastHud)) return;
        lastHud = json;
        VellumHud.push(HUD, root);
    }

    /** An arrow pointing from where the player looks toward {@code to}: ↑ is straight ahead, → to the right. */
    public static String arrow(float playerYaw, Vec3 to) {
        double heading = Math.toDegrees(Math.atan2(-to.x, to.z));
        double relative = wrapDegrees(heading - playerYaw);
        int index = (int) Math.floorMod(Math.round(relative / 45.0), 8);
        return ARROWS[index];
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
