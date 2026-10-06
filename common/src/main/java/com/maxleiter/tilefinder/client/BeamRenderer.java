package com.maxleiter.tilefinder.client;

import java.util.List;

import com.maxleiter.tilefinder.scan.Parts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=26 {
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
*///?}

/**
 * Draws the tracked targets: a box around each (nearest first, at most {@link #MAX_BOXES}) and, from in front of the
 * player to the nearest, an arcing double helix. Everything is drawn through walls.
 */
public final class BeamRenderer {
    private static final int MAX_BOXES = 64;
    private static final float BEAM_WIDTH = 2.0f;
    private static final float BOX_WIDTH = 2.5f;

    private BeamRenderer() {
    }

    private interface Lines {
        void line(Vec3 a, Vec3 b, int argb, float width);
    }

    private static void emit(float partialTick, Lines lines) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !Tracker.active()) return;
        Settings settings = Settings.get();
        List<BlockPos> targets = Tracker.targets();

        int boxColor = 0xFF000000 | Settings.rgb(settings.boxColor);
        for (int i = 0; i < Math.min(targets.size(), MAX_BOXES); i++) {
            BlockPos pos = targets.get(i);
            AABB box = Parts.outline(pos, mc.level.getBlockState(pos)).inflate(0.01);
            // The nearest target is drawn solid, the rest fainter.
            int color = i == 0 ? boxColor : (boxColor & 0x00FFFFFF) | 0x90000000;
            box(box, color, lines);
        }

        if (!settings.beam) return;
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        // Start ahead and a little to the right, so the beam leaves the middle of the view free.
        Vec3 start = eye.add(look.scale(2.0)).add(right.scale(0.5)).add(0, -0.3, 0);
        Vec3 end = Vec3.atCenterOf(targets.getFirst());
        helix(start, end, settings, lines);
    }

    private static void helix(Vec3 start, Vec3 end, Settings settings, Lines lines) {
        Vec3 path = end.subtract(start);
        double distance = path.length();
        if (distance < 0.5) return;
        Vec3 direction = path.scale(1 / distance);
        Vec3 axis = Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = direction.cross(axis).normalize();
        Vec3 up = direction.cross(side).normalize();

        int color1 = 0xFF000000 | Settings.rgb(settings.beamColor);
        int color2 = 0xFF000000 | Settings.rgb(settings.beamColor2);
        double radius = settings.helixRadius;
        double turns = Math.max(1, distance * 0.6);
        double spin = (System.currentTimeMillis() % 100_000L) / 1000.0 * settings.helixSpeed * Math.PI * 2;
        double arcHeight = Math.min(settings.arc, distance / 3);
        int segments = Math.min(400, Math.max(24, (int) (distance * 6)));

        Vec3 previous1 = null;
        Vec3 previous2 = null;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 center = start.add(direction.scale(distance * t)).add(0, Math.sin(Math.PI * t) * arcHeight, 0);
            double angle = t * turns * Math.PI * 2 + spin;
            // The helix narrows into the target, so the beam ends on the block.
            double r = radius * Math.min(1, (1 - t) * 4);
            Vec3 strand1 = center.add(side.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
            Vec3 strand2 = center.add(side.scale(Math.cos(angle + Math.PI) * r)).add(up.scale(Math.sin(angle + Math.PI) * r));
            if (previous1 != null) {
                lines.line(previous1, strand1, color1, BEAM_WIDTH);
                lines.line(previous2, strand2, color2, BEAM_WIDTH);
            }
            previous1 = strand1;
            previous2 = strand2;
        }
    }

    private static void box(AABB box, int argb, Lines lines) {
        Vec3[] c = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ)};
        for (int i = 0; i < 4; i++) {
            lines.line(c[i], c[(i + 1) % 4], argb, BOX_WIDTH);
            lines.line(c[i + 4], c[(i + 1) % 4 + 4], argb, BOX_WIDTH);
            lines.line(c[i], c[i + 4], argb, BOX_WIDTH);
        }
    }

    //? if >=26 {
    /**
     * Adds this frame's gizmos. Call once per frame inside the render thread's gizmo collection: NeoForge's
     * RenderFrameEvent.Pre, Fabric's LevelRenderEvents.BEFORE_GIZMOS. Gizmos are always-on-top, so they show through
     * walls.
     */
    public static void submitGizmos(float partialTick) {
        emit(partialTick, (a, b, argb, width) -> Gizmos.line(a, b, argb, width).setAlwaysOnTop());
    }
    //?} else {
    /*/^*
     * Draws the lines now, camera-relative, with the depth test off so they show through walls. Call after the level's
     * translucent geometry: NeoForge's RenderLevelStageEvent AFTER_PARTICLES, Fabric's WorldRenderEvents.LAST.
     ^/
    public static void renderImmediate(PoseStack poseStack, Vec3 camera, float partialTick) {
        if (!Tracker.active()) return;
        PoseStack.Pose pose = poseStack.last();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        boolean[] any = {false};
        emit(partialTick, (a, b, argb, width) -> {
            Vec3 normal = b.subtract(a).normalize();
            buffer.addVertex(pose, (float) (a.x - camera.x), (float) (a.y - camera.y), (float) (a.z - camera.z))
                    .setColor(argb).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            buffer.addVertex(pose, (float) (b.x - camera.x), (float) (b.y - camera.y), (float) (b.z - camera.z))
                    .setColor(argb).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            any[0] = true;
        });
        if (!any[0]) {
            buffer.build();
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(BEAM_WIDTH);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
    }
    *///?}
}
