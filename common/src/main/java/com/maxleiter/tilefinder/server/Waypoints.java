package com.maxleiter.tilefinder.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.scan.Parts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * "Point the way" for players without the mod: a trail of dust from just in front of the player toward the target and
 * a box around the target, sent to that player only. The server has no HUD to draw on, so particles are what it has.
 */
public final class Waypoints {
    private static final int DURATION_TICKS = 30 * 20;
    private static final int REFRESH_TICKS = 5;
    private static final double ARRIVED_SQ = 3 * 3;
    private static final double TRAIL_START = 1.5;
    private static final double TRAIL_STEP = 0.8;
    private static final double TRAIL_LENGTH = 16;
    private static final double OUTLINE_STEP = 0.5;

    private static final DustParticleOptions TRAIL = Mc.dust(0x55FFFF, 1.0f);
    private static final DustParticleOptions BOX = Mc.dust(0xFFAA00, 0.8f);

    private record Track(ServerLevel level, BlockPos pos, MutableComponent name, int endsAt) {
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private Waypoints() {
    }

    public static void start(ServerPlayer player, BlockPos pos, Component name) {
        ServerLevel level = Mc.serverLevel(player);
        TRACKS.put(player.getUUID(), new Track(level, pos, name.copy(), level.getServer().getTickCount() + DURATION_TICKS));
        player.sendSystemMessage(Msg.t("tilefinder.server.pointing", "Pointing the way to %s at %s, %s blocks away. Walk up to it, or run /tilefinder clear to stop.",
                name, coordinates(pos), distance(player, pos)));
        show(player, TRACKS.get(player.getUUID()));
    }

    /** Stops tracking for a player. Returns whether there was anything to stop. */
    public static boolean clear(UUID player) {
        return TRACKS.remove(player) != null;
    }

    public static void clearAll() {
        TRACKS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (TRACKS.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Track>> iterator = TRACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Track> entry = iterator.next();
            Track track = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            // A player in another dimension, or in a different world after the server restarted, is no longer on the way.
            if (player == null || Mc.serverLevel(player) != track.level()) {
                iterator.remove();
                continue;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(track.pos())) <= ARRIVED_SQ) {
                player.sendSystemMessage(Msg.t("tilefinder.server.arrived", "You are at %s.", track.name()));
                iterator.remove();
            } else if (now >= track.endsAt()) {
                player.sendSystemMessage(Msg.t("tilefinder.server.expired", "Stopped pointing the way to %s.", track.name()));
                iterator.remove();
            } else if (now % REFRESH_TICKS == 0) {
                show(player, track);
            }
        }
    }

    static Component coordinates(BlockPos pos) {
        String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        Style style = Mc.copyOnClick(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true), text,
                Msg.t("tilefinder.server.copy", "Click to copy the coordinates"));
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(style);
    }

    private static String distance(ServerPlayer player, BlockPos pos) {
        return String.valueOf(Math.round(Math.sqrt(player.blockPosition().distSqr(pos))));
    }

    private static void show(ServerPlayer player, Track track) {
        ServerLevel level = track.level();
        Vec3 target = Vec3.atCenterOf(track.pos());
        Vec3 from = player.position().add(0, 1.0, 0);
        Vec3 toTarget = target.subtract(from);
        double length = toTarget.length();
        if (length > 1.0e-3) {
            Vec3 direction = toTarget.scale(1.0 / length);
            double reach = Math.min(length, TRAIL_LENGTH);
            for (double along = TRAIL_START; along < reach; along += TRAIL_STEP) {
                Vec3 at = from.add(direction.scale(along));
                Mc.sendParticle(level, player, TRAIL, at.x, at.y, at.z);
            }
        }
        outline(level, player, Parts.outline(track.pos(), level.getBlockState(track.pos())));
    }

    private static void outline(ServerLevel level, ServerPlayer player, AABB box) {
        double[] x = {box.minX, box.maxX};
        double[] y = {box.minY, box.maxY};
        double[] z = {box.minZ, box.maxZ};
        for (double a : y) {
            for (double b : z) edge(level, player, box.minX, a, b, box.maxX, a, b);
        }
        for (double a : x) {
            for (double b : z) edge(level, player, a, box.minY, b, a, box.maxY, b);
            for (double b : y) edge(level, player, a, b, box.minZ, a, b, box.maxZ);
        }
    }

    private static void edge(ServerLevel level, ServerPlayer player, double x1, double y1, double z1, double x2, double y2, double z2) {
        double length = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        int steps = Math.max(1, (int) Math.ceil(length / OUTLINE_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Mc.sendParticle(level, player, BOX, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t);
        }
    }
}
