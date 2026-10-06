package com.maxleiter.tilefinder.compat.jei;

import java.util.Optional;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.client.FinderPage;
import com.maxleiter.tilefinder.compat.RecipeViewers;

import dev.vellum.mod.client.VellumScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * JEI's side of TileFinder: lets the finder screen take JEI's R and U keys on a group's icon, and gives
 * {@link RecipeViewers} what it needs to read JEI's hovered stack and open its recipe screen.
 */
@JeiPlugin
public final class TileFinderJeiPlugin implements IModPlugin {
    private static final Identifier UID = TileFinder.id("jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // JEI keeps one handler per screen class, so this covers every Vellum screen; it answers (non-null
        // properties) only for the finder, and other Vellum pages stay as JEI treats an unknown screen.
        registration.addGuiScreenHandler(VellumScreen.class, new FinderScreenHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeViewers.register(new Bridge(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeViewers.unregister(Bridge.NAME);
    }

    private static final class FinderScreenHandler implements IScreenHandler<VellumScreen> {
        @Override
        public @Nullable IGuiProperties apply(VellumScreen screen) {
            if (!FinderPage.isFinder(screen)) return null;
            // The whole screen: the finder draws its own background, so JEI's lists have no room beside it and stay
            // off, and only its hovered-ingredient keys apply.
            return new IGuiProperties() {
                @Override
                public Class<? extends Screen> screenClass() {
                    return screen.getClass();
                }

                @Override
                public int guiLeft() {
                    return 0;
                }

                @Override
                public int guiTop() {
                    return 0;
                }

                @Override
                public int guiXSize() {
                    return screen.width;
                }

                @Override
                public int guiYSize() {
                    return screen.height;
                }

                @Override
                public int screenWidth() {
                    return screen.width;
                }

                @Override
                public int screenHeight() {
                    return screen.height;
                }
            };
        }

        @Override
        public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory factory, VellumScreen screen, double mouseX, double mouseY) {
            FinderPage.Icon icon = FinderPage.iconAt(screen, mouseX, mouseY);
            if (icon == null) return Optional.empty();
            return factory.createBuilder(icon.stack()).buildWithArea(icon.x(), icon.y(), icon.width(), icon.height());
        }
    }

    private record Bridge(IJeiRuntime runtime) implements RecipeViewers.Viewer {
        static final String NAME = "jei";

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public ItemStack hovered(Screen screen, double mouseX, double mouseY) {
            ItemStack stack = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
            if (!isEmpty(stack)) return stack.copy();
            stack = runtime.getBookmarkOverlay().getItemStackUnderMouse();
            if (!isEmpty(stack)) return stack.copy();
            stack = runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(null);
            if (!isEmpty(stack)) return stack.copy();
            return runtime.getScreenHelper().getClickableIngredientUnderMouse(screen, mouseX, mouseY)
                    .map(IClickableIngredient::getTypedIngredient)
                    .flatMap(typed -> typed.getItemStack().stream())
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .map(ItemStack::copy)
                    .orElse(ItemStack.EMPTY);
        }

        @Override
        public boolean show(ItemStack stack, boolean uses) {
            if (stack.isEmpty()) return false;
            RecipeIngredientRole role = uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack));
            return true;
        }

        private static boolean isEmpty(@Nullable ItemStack stack) {
            return stack == null || stack.isEmpty();
        }
    }
}
