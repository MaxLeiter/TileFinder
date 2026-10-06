package com.maxleiter.tilefinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The few calls whose shape differs between the Minecraft versions TileFinder builds for (sides both). */
public final class Mc {
    private Mc() {
    }

    public static int minY(Level level) {
        //? if >=26 {
        return level.getMinY();
        //?} else
        /*return level.getMinBuildHeight();*/
    }

    /** The highest block Y, inclusive. */
    public static int maxY(Level level) {
        //? if >=26 {
        return level.getMaxY();
        //?} else
        /*return level.getMaxBuildHeight() - 1;*/
    }

    /** What pick-block gives for this block: the item a wall sign or a wall head comes from, without its data. */
    @SuppressWarnings("deprecation") // 1.21.1's Block#getCloneItemStack; NeoForge's replacement needs a Player
    public static ItemStack pickStack(Level level, BlockPos pos, BlockState state) {
        //? if >=26 {
        return state.getCloneItemStack(level, pos, false);
        //?} else
        /*return state.getBlock().getCloneItemStack(level, pos, state);*/
    }

    public static Identifier blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static Identifier typeId(BlockEntityType<?> type) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    }

    // ---- server side, for the /tilefinder command

    public static ServerLevel serverLevel(ServerPlayer player) {
        //? if >=26 {
        return player.level();
        //?} else
        /*return player.serverLevel();*/
    }

    /** Whether the player may use commands that need operator level 2 (like /tp). */
    public static boolean isGamemaster(ServerPlayer player) {
        //? if >=26 {
        return player.createCommandSourceStack().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        //?} else
        /*return player.createCommandSourceStack().hasPermission(2);*/
    }

    /** A dust particle of this colour (0xRRGGBB). */
    public static DustParticleOptions dust(int rgb, float scale) {
        //? if >=26 {
        return new DustParticleOptions(rgb, scale);
        //?} else
        /*return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);*/
    }

    /** Sends one particle to one player only, even from far away, and regardless of their particle setting. */
    public static void sendParticle(ServerLevel level, ServerPlayer player, DustParticleOptions particle, double x, double y, double z) {
        //? if >=26 {
        level.sendParticles(player, particle, true, true, x, y, z, 1, 0, 0, 0, 0);
        //?} else
        /*level.sendParticles(player, particle, true, x, y, z, 1, 0, 0, 0, 0);*/
    }

    public static Style copyOnClick(Style style, String text, Component hover) {
        //? if >=26 {
        return style.withClickEvent(new ClickEvent.CopyToClipboard(text)).withHoverEvent(new HoverEvent.ShowText(hover));
        //?} else
        /*return style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));*/
    }
}
