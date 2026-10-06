package com.maxleiter.tilefinder.compat.rei;

import com.maxleiter.tilefinder.client.FinderPage;
import com.maxleiter.tilefinder.compat.RecipeViewers;

import dev.architectury.event.CompoundEventResult;
import dev.vellum.mod.client.VellumScreen;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.overlay.OverlayListWidget;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * REI's side of TileFinder. REI finds it through the {@code rei_client} entrypoint on Fabric and through the
 * {@code @REIPluginClient} subclass in the NeoForge module. It lets the finder screen take REI's R and U keys on a
 * group's icon, and gives {@link RecipeViewers} what it needs to read REI's hovered stack and open its recipe screen.
 */
public class TileFinderReiPlugin implements REIClientPlugin {
    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDecider(new FinderDecider());
        registry.registerFocusedStack((screen, mouse) -> {
            FinderPage.Icon icon = FinderPage.iconAt(screen, mouse.getX(), mouse.getY());
            return icon == null ? CompoundEventResult.pass() : CompoundEventResult.interruptTrue(EntryStacks.of(icon.stack()));
        });
    }

    // REI's plugins are reloaded with the world's recipes: the bridge is only valid once a reload has finished.
    @Override
    public void preStage(PluginManager<REIClientPlugin> manager, ReloadStage stage) {
        if (stage == ReloadStage.START) RecipeViewers.unregister(Bridge.NAME);
    }

    @Override
    public void postStage(PluginManager<REIClientPlugin> manager, ReloadStage stage) {
        if (stage == ReloadStage.END) RecipeViewers.register(new Bridge());
    }

    /**
     * Has REI overlay the finder (its keys only work with the overlay present) with the page as its whole screen, so
     * REI's lists have no room beside it and stay off. Other Vellum screens are left to REI's other deciders.
     */
    private static final class FinderDecider implements DisplayBoundsProvider<VellumScreen> {
        @Override
        public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
            return VellumScreen.class.isAssignableFrom(screen);
        }

        @Override
        public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
            return FinderPage.isFinder(screen) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        @Override
        public @Nullable Rectangle getScreenBounds(VellumScreen screen) {
            return FinderPage.isFinder(screen) ? new Rectangle(0, 0, screen.width, screen.height) : null;
        }
    }

    private static final class Bridge implements RecipeViewers.Viewer {
        static final String NAME = "rei";

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public ItemStack hovered(Screen screen, double mouseX, double mouseY) {
            Point mouse = new Point(mouseX, mouseY);
            ItemStack stack = REIRuntime.getInstance().getOverlay()
                    .map(overlay -> {
                        ItemStack found = item(overlay.getEntryList().getFocusedStack());
                        if (!found.isEmpty()) return found;
                        return overlay.getFavoritesList().map(OverlayListWidget::getFocusedStack).map(Bridge::item).orElse(ItemStack.EMPTY);
                    })
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) return stack;
            // A recipe screen's slots.
            for (Slot slot : Widgets.<Slot>walk(screen.children(), widget -> widget instanceof Slot s && s.containsMouse(mouse))) {
                stack = item(slot.getCurrentEntry());
                if (!stack.isEmpty()) return stack;
            }
            // Container slots, and whatever else a plugin (the finder's included) registered.
            return item(ScreenRegistry.getInstance().getFocusedStack(screen, mouse));
        }

        @Override
        public boolean show(ItemStack stack, boolean uses) {
            if (stack.isEmpty()) return false;
            ViewSearchBuilder builder = ViewSearchBuilder.builder();
            if (uses) builder.addUsagesFor(EntryStacks.of(stack));
            else builder.addRecipesFor(EntryStacks.of(stack));
            return builder.open();
        }

        private static ItemStack item(@Nullable EntryStack<?> entry) {
            if (entry == null || entry.isEmpty()) return ItemStack.EMPTY;
            ItemStack stack = entry.cheatsAs().getValue();
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }
}
