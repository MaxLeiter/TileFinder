package com.maxleiter.tilefinder.client;

import com.maxleiter.tilefinder.Config;
import com.maxleiter.tilefinder.TileFinder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = TileFinder.MODID, value = Dist.CLIENT)
public class PathHighlighter {
    private static BlockPos targetPos = null;

    public static void setTarget(BlockPos pos) {
        targetPos = pos;
    }

    public static void clear() {
        targetPos = null;
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (targetPos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Get player position and adjust start position to avoid blocking view
        Vec3 playerPos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();

        // Offset the start position by 2 blocks in front of the player and slightly to
        // the side
        double offsetDistance = 2.0;
        double sideOffset = 0.5;
        Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize();
        Vec3 startPos = playerPos.add(lookVec.scale(offsetDistance)).add(rightVec.scale(sideOffset));

        Vec3 endPos = Vec3.atCenterOf(targetPos);

        // Debug logging
        System.out.println("PathHighlighter: Rendering to " + targetPos + ", enableBeam=" + Config.ENABLE_BEAM.get());

        // Render the highlight box
        renderHighlightBox(poseStack, bufferSource, cameraPos, targetPos);

        // Render the beam
        if (Config.ENABLE_BEAM.get()) {
            renderBeam(poseStack, bufferSource, cameraPos, startPos, endPos);
        }

        bufferSource.endBatch();
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Vec3 start,
            Vec3 end) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.0));
        Matrix4f matrix = poseStack.last().pose();

        Vec3 relativeStart = start.subtract(cameraPos);
        Vec3 relativeEnd = end.subtract(cameraPos);

        // Calculate path parameters
        Vec3 direction = relativeEnd.subtract(relativeStart);
        double distance = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create perpendicular vectors for the helix
        // First perpendicular: cross with up vector if not parallel
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 perpendicular1;
        if (Math.abs(normalizedDir.dot(up)) > 0.99) {
            // If direction is nearly vertical, use a different vector
            perpendicular1 = normalizedDir.cross(new Vec3(1, 0, 0)).normalize();
        } else {
            perpendicular1 = normalizedDir.cross(up).normalize();
        }
        // Second perpendicular: cross direction with first perpendicular
        Vec3 perpendicular2 = normalizedDir.cross(perpendicular1).normalize();

        double helixRadius = Config.HELIX_RADIUS.get();
        double helixSpeed = Config.HELIX_SPEED.get();
        double arcScale = Config.ARC_SCALE.get();
        double time = System.currentTimeMillis() / 1000.0;

        // Number of segments for smooth curves
        int segments = Math.max(30, (int) (distance * 6));

        // Draw first helix strand as connected line segments
        Vec3 prevPos1 = null;
        Vec3 prevPos2 = null;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;

            // Create arc path
            double arcHeight = Math.sin(Math.PI * t) * Math.min(arcScale, distance / 3);
            Vec3 basePos = relativeStart.add(normalizedDir.scale(distance * t)).add(0, arcHeight, 0);

            // Calculate helix rotation angle
            double rotations = helixSpeed * 3; // Number of full rotations
            double angle1 = t * Math.PI * 2 * rotations + time * 2;

            // First helix strand - use both perpendicular vectors for true 3D rotation
            double x1 = Math.cos(angle1) * helixRadius;
            double y1 = Math.sin(angle1) * helixRadius;
            Vec3 helixOffset1 = perpendicular1.scale(x1).add(perpendicular2.scale(y1));
            Vec3 pos1 = basePos.add(helixOffset1);

            // Second helix strand (180 degrees offset)
            double angle2 = angle1 + Math.PI;
            double x2 = Math.cos(angle2) * helixRadius;
            double y2 = Math.sin(angle2) * helixRadius;
            Vec3 helixOffset2 = perpendicular1.scale(x2).add(perpendicular2.scale(y2));
            Vec3 pos2 = basePos.add(helixOffset2);

            // Draw line segments
            if (prevPos1 != null) {
                // First strand in bright cyan
                consumer.addVertex(matrix, (float) prevPos1.x, (float) prevPos1.y, (float) prevPos1.z)
                        .setColor(0, 255, 255, 255);
                consumer.addVertex(matrix, (float) pos1.x, (float) pos1.y, (float) pos1.z)
                        .setColor(0, 255, 255, 255);

                // Second strand in lighter cyan
                consumer.addVertex(matrix, (float) prevPos2.x, (float) prevPos2.y, (float) prevPos2.z)
                        .setColor(100, 200, 255, 255);
                consumer.addVertex(matrix, (float) pos2.x, (float) pos2.y, (float) pos2.z)
                        .setColor(100, 200, 255, 255);
            }

            prevPos1 = pos1;
            prevPos2 = pos2;
        }
    }

    private static void renderHighlightBox(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos,
            BlockPos pos) {
        // Use built-in helper to render selection box (cyan, full opacity)
        AABB box = new AABB(pos).inflate(0.02);
        LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.debugLineStrip(2.0)),
                box.minX - cameraPos.x, box.minY - cameraPos.y, box.minZ - cameraPos.z,
                box.maxX - cameraPos.x, box.maxY - cameraPos.y, box.maxZ - cameraPos.z,
                0.0f, 1.0f, 1.0f, 1.0f);
    }
}