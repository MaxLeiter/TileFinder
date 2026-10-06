package com.maxleiter.tilefinder;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class TileFinder {
    public static final String MOD_ID = "tilefinder";
    public static final Logger LOG = LogUtils.getLogger();

    private TileFinder() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
