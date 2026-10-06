package com.maxleiter.tilefinder.client;

import java.util.List;

import com.maxleiter.tilefinder.TileFinder;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/** TileFinder's key mappings. The loaders register them, then the client tick reads them. */
public final class Keys {
    //? if >=26 {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(TileFinder.id("tilefinder"));
    //?} else
    /*public static final String CATEGORY = "key.category.tilefinder.tilefinder";*/

    /** Opens the finder. Over an item in an inventory or a recipe viewer, finds that item's block instead. */
    public static final KeyMapping OPEN = new KeyMapping("key.tilefinder.open", InputConstants.KEY_BACKSLASH, CATEGORY);
    public static final KeyMapping CLEAR = new KeyMapping("key.tilefinder.clear", InputConstants.KEY_O, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(OPEN, CLEAR);

    private Keys() {
    }
}
