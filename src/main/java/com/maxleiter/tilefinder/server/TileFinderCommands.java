package com.maxleiter.tilefinder.server;

import com.maxleiter.tilefinder.Config;
import com.maxleiter.tilefinder.TileFinder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TileFinder.MODID)
public final class TileFinderCommands {
    private TileFinderCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tilefinder")
                .requires(src -> src.hasPermission(0))
                .executes(ctx -> open(ctx.getSource(), Config.defaultRadius))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> open(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))));
        event.getDispatcher().register(root);
    }

    private static int open(CommandSourceStack source, int radius) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            return Command.SINGLE_SUCCESS; // must be player context
        }
        TileFinderServerUI.open(player, radius);
        return Command.SINGLE_SUCCESS;
    }
} 