package com.example;

import com.example.client.GuiTileFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * A minimal Forge 1.12 mod that lets the player open a GUI listing nearby
 * {@link net.minecraft.tileentity.TileEntity TileEntities}. The GUI is opened
 * with a key (binding default "P").
 */
@Mod(modid = TileFinderMod.MODID, name = TileFinderMod.NAME, version = TileFinderMod.VERSION, acceptableRemoteVersions = "*")
public class TileFinderMod {
    public static final String MODID = "tilefinder";
    public static final String NAME = "Tile Finder";
    public static final String VERSION = "0.1.0";

    private static KeyBinding openGuiKey;
    private static KeyBinding clearPathKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (e.getSide().isClient()) {
            registerClientStuff();
        }
    }

    private static void registerClientStuff() {
        openGuiKey = new KeyBinding("key.tilefinder.open", // localisation key
                KeyConflictContext.IN_GAME,
                25, // 'P'
                "key.categories.tilefinder");
        ClientRegistry.registerKeyBinding(openGuiKey);

        clearPathKey = new KeyBinding("key.tilefinder.clear", KeyConflictContext.IN_GAME, 24, // 'O'
                "key.categories.tilefinder");
        ClientRegistry.registerKeyBinding(clearPathKey);

        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onKeyInput(InputEvent.KeyInputEvent evt) {
                if (openGuiKey.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiTileFinder());
                }
                if (clearPathKey.isPressed()) {
                    com.example.client.PathHighlighter.clear();
                }
            }

            @SubscribeEvent
            public void onInteract(PlayerInteractEvent.RightClickBlock evt) {
                if (evt.getWorld().isRemote && com.example.client.PathHighlighter.isActive()) {
                    if (evt.getPos().equals(com.example.client.PathHighlighter.getTargetPos())) {
                        com.example.client.PathHighlighter.clear();
                    }
                }
            }
        });
    }
}