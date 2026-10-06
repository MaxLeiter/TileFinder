package com.maxleiter.tilefinder.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Starred spots, per world and dimension, in config/tilefinder/favorites.json. A world is the server's address, or the
 * save's folder name in singleplayer, so the same coordinates in two worlds are different favorites.
 */
public final class Favorites {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Set<String>>>() { }.getType();
    private static Map<String, Set<String>> byWorld;

    private Favorites() {
    }

    public static boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        return worldSet(false).contains(key(dimension, pos));
    }

    public static boolean toggle(ResourceKey<Level> dimension, BlockPos pos) {
        Set<String> set = worldSet(true);
        String key = key(dimension, pos);
        boolean starred = set.add(key) || !set.remove(key);
        save();
        return starred;
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        //? if >=26 {
        String id = dimension.identifier().toString();
        //?} else
        /*String id = dimension.location().toString();*/
        return id + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Set<String> worldSet(boolean create) {
        if (byWorld == null) load();
        String world = worldKey();
        Set<String> set = byWorld.get(world);
        if (set == null) {
            set = new LinkedHashSet<>();
            if (create) byWorld.put(world, set);
        }
        return set;
    }

    private static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        ServerData server = mc.getCurrentServer();
        return server != null ? "server:" + server.ip : "unknown";
    }

    private static Path file() {
        return Platform.INSTANCE.configDir().resolve("tilefinder").resolve("favorites.json");
    }

    private static void load() {
        byWorld = new HashMap<>();
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Set<String>> loaded = GSON.fromJson(reader, TYPE);
            if (loaded != null) {
                loaded.forEach((world, set) -> byWorld.put(world, new LinkedHashSet<>(set)));
            }
        } catch (IOException | JsonParseException e) {
            TileFinder.LOG.error("Couldn't read {}", file, e);
        }
    }

    private static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(byWorld, TYPE, writer);
            }
        } catch (IOException e) {
            TileFinder.LOG.error("Couldn't write {}", file, e);
        }
    }
}
