package com.maxleiter.common;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public class TileFinderConfig {
    public static double helixRadius = 0.25;
    public static double helixSpeed = 0.15;
    public static double arcScale = 10.0; // max height
    public static boolean enableBeam = true;

    private static Configuration cfg;

    public static void load(File file) {
        cfg = new Configuration(file);
        sync();
    }

    public static void sync() {
        helixRadius = cfg.get("beam", "helixRadius", 0.25d).getDouble();
        helixSpeed = cfg.get("beam", "helixSpeed", 0.15d).getDouble();
        arcScale = cfg.get("beam", "arcScale", 10d).getDouble();
        enableBeam = cfg.get("beam", "enableBeam", true).getBoolean();
        if (cfg.hasChanged())
            cfg.save();
    }

    public static Configuration getConfig() {
        return cfg;
    }
}