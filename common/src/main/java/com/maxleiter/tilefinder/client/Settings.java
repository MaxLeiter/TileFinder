package com.maxleiter.tilefinder.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.platform.Platform;
import com.maxleiter.tilefinder.scan.Kinds;

/** The client's settings, in config/tilefinder.json. Edited from the finder's settings tab. */
public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    public static final int MAX_RADIUS = 256;

    private static Settings current = new Settings();

    public int radius = 32;
    public boolean mergeConnected = true;
    public boolean showDecorative = false;
    public boolean beam = true;
    public String beamColor = "#B43CFF";
    public String beamColor2 = "#FF78FF";
    public String boxColor = "#40E0FF";
    public double helixRadius = 0.3;
    public double helixSpeed = 0.5;
    public double arc = 5.0;
    /** A target counts as reached, and stops being highlighted, once you are this close to it. 0 keeps it. */
    public double arriveDistance = 2.5;
    public boolean hud = true;
    /** Block entity type ids to leave out entirely, added to the built-in ones and the tilefinder:hidden tag. */
    public List<String> hidden = new ArrayList<>();
    /** Block entity type ids that count as decoration, added to the built-in ones and the tilefinder:decorative tag. */
    public List<String> decorative = new ArrayList<>();

    public static Settings get() {
        return current;
    }

    public Kinds kinds() {
        Set<String> hiddenIds = new HashSet<>(Kinds.DEFAULT_HIDDEN);
        hiddenIds.addAll(hidden);
        Set<String> decorativeIds = new HashSet<>(Kinds.DEFAULT_DECORATIVE);
        decorativeIds.addAll(decorative);
        return new Kinds(hiddenIds, decorativeIds);
    }

    public static int rgb(String color) {
        return Integer.parseInt(color.substring(1), 16);
    }

    private static Path file() {
        return Platform.INSTANCE.configDir().resolve("tilefinder.json");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            current = new Settings();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            current = loaded == null ? new Settings() : loaded.validated();
        } catch (IOException | JsonParseException e) {
            TileFinder.LOG.error("Couldn't read {}, using the defaults", file, e);
            current = new Settings();
        }
    }

    public static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(current, writer);
            }
        } catch (IOException e) {
            TileFinder.LOG.error("Couldn't write {}", file, e);
        }
    }

    /** Applies the settings tab's values. They come from a page a resource pack can replace, so each is checked. */
    public static void apply(JsonObject values) {
        Settings next = GSON.fromJson(GSON.toJson(current), Settings.class);
        JsonObject merged = GSON.toJsonTree(next).getAsJsonObject();
        for (String key : values.keySet()) {
            if (merged.has(key)) merged.add(key, values.get(key));
        }
        try {
            current = GSON.fromJson(merged, Settings.class).validated();
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            TileFinder.LOG.warn("Ignored invalid TileFinder settings from the page: {}", values, e);
            return;
        }
        save();
    }

    public JsonObject toJson() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    private Settings validated() {
        Settings defaults = new Settings();
        radius = clamp(radius, 4, MAX_RADIUS);
        helixRadius = clamp(helixRadius, 0, 1);
        helixSpeed = clamp(helixSpeed, 0, 2);
        arc = clamp(arc, 0, 20);
        arriveDistance = clamp(arriveDistance, 0, 16);
        if (beamColor == null || !COLOR.matcher(beamColor).matches()) beamColor = defaults.beamColor;
        if (beamColor2 == null || !COLOR.matcher(beamColor2).matches()) beamColor2 = defaults.beamColor2;
        if (boxColor == null || !COLOR.matcher(boxColor).matches()) boxColor = defaults.boxColor;
        hidden = hidden == null ? new ArrayList<>() : new ArrayList<>(hidden.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList());
        decorative = decorative == null ? new ArrayList<>() : new ArrayList<>(decorative.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList());
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
