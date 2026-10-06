package com.maxleiter.tilefinder.server;

import java.util.List;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.scan.Finder;
import com.maxleiter.tilefinder.scan.Kinds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * {@code /tilefinder [radius] [filter]} and {@code /tilefinder clear}: TileFinder for players who don't have the mod.
 * It scans on the server and shows the result in a chest menu, which any client can display.
 */
public final class FinderCommand {
    public static final int DEFAULT_RADIUS = 32;
    public static final int MAX_RADIUS = 128;

    private FinderCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tilefinder")
                .executes(context -> run(context.getSource(), DEFAULT_RADIUS, ""))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "radius"),
                                        StringArgumentType.getString(context, "filter"))))));
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean had = Waypoints.clear(player.getUUID());
        player.sendSystemMessage(had ? Msg.t("tilefinder.server.cleared", "Stopped pointing the way.")
                : Msg.t("tilefinder.server.nothing_tracked", "You are not being pointed anywhere."));
        return had ? 1 : 0;
    }

    private static int run(CommandSourceStack source, int radius, String filter) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = Mc.serverLevel(player);
        List<Finder.Group> groups = Filters.apply(
                Finder.find(level, player.blockPosition(), new Finder.Options(radius, true, Kinds.defaults())), filter);
        if (groups.isEmpty()) {
            player.sendSystemMessage(Msg.t("tilefinder.server.none", "Nothing found within %s blocks. Only loaded chunks are searched.", radius));
            return 0;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, who) -> new FinderMenu(id, inventory, player, radius, filter, groups),
                Msg.t("tilefinder.server.title", "TileFinder")));
        return groups.size();
    }
}
