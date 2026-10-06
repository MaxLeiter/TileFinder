package com.maxleiter.tilefinder.compat.emi;

import com.maxleiter.tilefinder.client.FinderPage;
import com.maxleiter.tilefinder.compat.RecipeViewers;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.vellum.mod.client.VellumScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * EMI's side of TileFinder (EMI has no 26.x builds, so this file is 1.21.1 only; see common/versions/1.21.1). EMI
 * finds it through {@code @EmiEntrypoint} on NeoForge and the {@code emi} entrypoint on Fabric. It lets the finder
 * screen take EMI's R and U keys on a group's icon, and gives {@link RecipeViewers} what it needs to read EMI's
 * hovered stack and open its recipe screen.
 */
@EmiEntrypoint
public final class TileFinderEmiPlugin implements EmiPlugin {
    private static final String NAME = "emi";

    @Override
    public void register(EmiRegistry registry) {
        // EMI matches the screen class exactly, so these are generic providers that answer only for the finder. With
        // the page as its whole screen EMI's lists have no room beside it and stay off, but its keys still work.
        registry.addGenericScreenBoundsProvider(screen ->
                screen instanceof VellumScreen && FinderPage.isFinder(screen) ? new Bounds(0, 0, screen.width, screen.height) : null);
        registry.addGenericStackProvider((screen, mouseX, mouseY) -> {
            FinderPage.Icon icon = FinderPage.iconAt(screen, mouseX, mouseY);
            return icon == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(icon.stack()));
        });
        // register() runs again whenever EMI reloads; the bridge is replaced by name.
        RecipeViewers.register(new RecipeViewers.Viewer() {
            @Override
            public String name() {
                return NAME;
            }

            @Override
            public ItemStack hovered(Screen screen, double mouseX, double mouseY) {
                EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, true);
                if (hovered == null || hovered.isEmpty()) return ItemStack.EMPTY;
                for (EmiStack stack : hovered.getStack().getEmiStacks()) {
                    ItemStack item = stack.getItemStack();
                    if (!item.isEmpty()) return item.copy();
                }
                return ItemStack.EMPTY;
            }

            @Override
            public boolean show(ItemStack stack, boolean uses) {
                if (stack.isEmpty()) return false;
                if (uses) EmiApi.displayUses(EmiStack.of(stack));
                else EmiApi.displayRecipes(EmiStack.of(stack));
                return true;
            }
        });
    }
}
