package com.maxleiter.tilefinder.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * The recipe viewers that are installed (JEI, REI). Each viewer's plugin class registers a bridge here once the
 * viewer has loaded it, so nothing else in TileFinder ever touches a viewer's classes.
 */
public final class RecipeViewers {
    public interface Viewer {
        String name();

        /** The stack under the pointer in the viewer's own lists or recipe pages over {@code screen}, or empty. */
        ItemStack hovered(Screen screen, double mouseX, double mouseY);

        /** Opens the viewer on how to make {@code stack} (or, with {@code uses}, what it makes). False if nothing shows. */
        boolean show(ItemStack stack, boolean uses);
    }

    private static final List<Viewer> viewers = new ArrayList<>();

    private RecipeViewers() {
    }

    public static void register(Viewer viewer) {
        viewers.removeIf(v -> v.name().equals(viewer.name()));
        viewers.add(viewer);
    }

    public static void unregister(String name) {
        viewers.removeIf(v -> v.name().equals(name));
    }

    public static boolean any() {
        return !viewers.isEmpty();
    }

    public static ItemStack hovered(Screen screen, double mouseX, double mouseY) {
        for (Viewer viewer : viewers) {
            ItemStack stack = viewer.hovered(screen, mouseX, mouseY);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean show(ItemStack stack, boolean uses) {
        for (Viewer viewer : viewers) {
            if (viewer.show(stack, uses)) return true;
        }
        return false;
    }
}
