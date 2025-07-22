package com.maxleiter.tilefinder.server;

import ca.landonjw.gooeylibs2.api.tasks.Task;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Emits a recurring particle path from a player to a target block until cleared.
 * Designed for vanilla clients: particles are sent from the server each tick (or interval).
 * Heavy visual FX are intentionally throttled.
 */
public final class ServerPathHighlighter {
    private ServerPathHighlighter() {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Start (or replace) a persistent path highlight from player to pos. */
    public static void highlightPath(ServerPlayer player, BlockPos pos) {
        clear(player); // replace existing
        Session session = new Session(player, pos);
        SESSIONS.put(player.getUUID(), session);
        session.start();
        player.displayClientMessage(Component.literal("Highlighting target at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.AQUA), false);
    }

    /** Emit a temporary path highlight that automatically expires after durationTicks. */
    public static void highlightPathForDuration(ServerPlayer player, BlockPos pos, int durationTicks) {
        // do not clear persistent session; this is a transient preview
        // schedule a task that fires every 5 ticks for N intervals
        int interval = 5;
        int iterations = Math.max(1, durationTicks / interval);
        new Task(t -> emitOnce(player, pos), 0, interval, iterations); // constructor auto-registers
    }

    private static void emitOnce(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 start = eye.add(look.scale(2.0)).add(right.scale(0.5));
        Vec3 end = Vec3.atCenterOf(pos);
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist < 0.001) return;
        int samples = Math.max(16, (int)(dist * 4));
        for (int i=0;i<=samples;i++) {
            double t = i/(double)samples;
            double ax = start.x + dx * t;
            double ay = start.y + dy * t + Math.sin(Math.PI * t) * Math.min(4.0, dist/3.0);
            double az = start.z + dz * t;
            level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, ax, ay, az, 1, 0,0,0,0.0);
        }
    }

    /** Burst highlight many positions (no persistent path). */
    public static void burst(ServerPlayer player, List<BlockPos> positions) {
        ServerLevel level = player.serverLevel();
        for (BlockPos p : positions) {
            double x = p.getX() + 0.5;
            double y = p.getY() + 1.1;
            double z = p.getZ() + 0.5;
            level.sendParticles(player, ParticleTypes.END_ROD, true, x, y, z, 20, 0.2, 0.4, 0.2, 0.0);
        }
        player.displayClientMessage(Component.literal("Highlighted " + positions.size() + " blocks"), false);
    }

    /** Clear current highlight for player. */
    public static void clear(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) s.stop();
        player.displayClientMessage(Component.literal("Highlight cleared."), false);
    }

    /** Clear all highlights (server shutdown). */
    public static void clearAll() {
        for (Session s : SESSIONS.values()) s.stop();
        SESSIONS.clear();
    }

    // --------------------------------------------------------------------------------------------------
    // Session impl
    // --------------------------------------------------------------------------------------------------
    private static final class Session {
        private final ServerPlayer player;
        private final BlockPos target;
        private Task task;
        private int tickCounter;

        Session(ServerPlayer player, BlockPos target) {
            this.player = player;
            this.target = target.immutable();
        }

        void start() {
            // run every 5 ticks indefinitely
            this.task = Task.builder()
                    .execute(t -> emitPath())
                    .interval(5)
                    .iterations(-1) // infinite
                    .build();
        }

        void stop() {
            if (task != null) task.setExpired();
        }

        private void emitPath() {
            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            // offset start ~2 blocks forward & a little to the right so particles do not spawn in the camera frustum
            Vec3 look = player.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 start = eye.add(look.scale(2.0)).add(right.scale(0.5));
            Vec3 end = Vec3.atCenterOf(target);
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double dz = end.z - start.z;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.001) return;

            // sample arc along straight line plus vertical sinusoidal bump
            int samples = Math.max(16, (int)(dist * 4));
            for (int i=0;i<=samples;i++) {
                double t = i/(double)samples;
                double ax = start.x + dx * t;
                double ay = start.y + dy * t + Math.sin(Math.PI * t) * Math.min(4.0, dist/3.0);
                double az = start.z + dz * t;
                // alternate particle types every other tick for motion
                level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK,
                        true, ax, ay, az, 1, 0,0,0,0.0);
            }
            tickCounter++;
        }
    }
} 