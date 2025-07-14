package com.maxleiter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import com.maxleiter.common.TileFinderConfig;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Spawns a line of particles from the player to a target BlockPos for a short
 * duration
 * to guide the player. Purely client-side.
 */
public class PathHighlighter {

    private static final Queue<Vec3d> points = new ArrayDeque<>();
    private static boolean registered = false;
    private static BlockPos targetPos;
    private static float pulse = 0f;

    public static void highlightTo(BlockPos target) {
        targetPos = target;
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(handler);
            registered = true;
        }
    }

    private static void spawnCurrentLine() {
        if (targetPos == null)
            return;
        points.clear();
        BlockPos playerPos = Minecraft.getMinecraft().player.getPosition();
        Vec3d start = new Vec3d(playerPos.getX() + 0.5, playerPos.getY() + 1.6, playerPos.getZ() + 0.5);
        Vec3d end = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Math.min(240, (int) (dist * 8)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double arc = Math.sin(Math.PI * t) * Math.min(TileFinderConfig.arcScale, dist / 4);
            points.add(new Vec3d(start.x + dx * t, start.y + dy * t + arc, start.z + dz * t));
        }
    }

    public static void clear() {
        points.clear();
        targetPos = null;
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(handler);
            registered = false;
        }
    }

    public static boolean isActive() {
        return targetPos != null;
    }

    public static BlockPos getTargetPos() {
        return targetPos;
    }

    private static final Object handler = new Object() {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent evt) {
            if (evt.phase != TickEvent.Phase.END)
                return;
            if (Minecraft.getMinecraft().world == null)
                return;
            if (Minecraft.getMinecraft().player.ticksExisted % 2 != 0)
                return;
            spawnCurrentLine();
        }

        @SubscribeEvent
        public void onRenderWorld(RenderWorldLastEvent evt) {
            if (points.isEmpty())
                return;
            Minecraft mc = Minecraft.getMinecraft();
            Vec3d cam = new Vec3d(mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY,
                    mc.getRenderManager().viewerPosZ);

            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();
            GlStateManager.glLineWidth(4F);

            if (!TileFinderConfig.enableBeam)
                return;
            float time = mc.player.ticksExisted + evt.getPartialTicks();

            java.util.List<Vec3d> pts = new java.util.ArrayList<>(points);
            renderStrip(pts, cam, 0x32FFFFFF, 4F);

            // double helix
            renderHelix(pts, cam, time, 0f);
            renderHelix(pts, cam, time, (float) Math.PI);

            // highlight target block
            if (targetPos != null) {
                AxisAlignedBB aabb = new AxisAlignedBB(targetPos).expand(0.002, 0.002, 0.002);
                RenderGlobal.drawSelectionBoundingBox(aabb.offset(-cam.x, -cam.y, -cam.z), 0f, 0.8f, 1f, 0.6f);
            }

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    };

    private static void renderStrip(java.util.List<Vec3d> pts, Vec3d cam, int argb, float width) {
        GlStateManager.glLineWidth(width);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(3, DefaultVertexFormats.POSITION_COLOR);
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        for (Vec3d p : pts) {
            buf.pos(p.x - cam.x, p.y - cam.y, p.z - cam.z).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }

    private static void renderHelix(java.util.List<Vec3d> pts, Vec3d cam, float time, float phaseOffset) {
        double radius = TileFinderConfig.helixRadius;
        GlStateManager.glLineWidth(2F);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(3, DefaultVertexFormats.POSITION_COLOR);

        int idx = 0;
        for (Vec3d p : pts) {
            float t = (float) idx / (float) pts.size();
            // simple circular offset in XZ plane
            double angle = t * Math.PI * 8 + time * TileFinderConfig.helixSpeed + phaseOffset;
            double offX = Math.cos(angle) * radius;
            double offZ = Math.sin(angle) * radius;
            buf.pos(p.x + offX - cam.x, p.y - cam.y, p.z + offZ - cam.z).color(50, 200, 255, 180).endVertex();
            idx++;
        }
        tess.draw();
    }
}