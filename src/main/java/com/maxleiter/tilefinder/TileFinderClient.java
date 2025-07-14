package com.maxleiter.tilefinder;

import com.maxleiter.tilefinder.client.PathHighlighter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TileFinder.MODID, dist = Dist.CLIENT)
public class TileFinderClient {

    public TileFinderClient(ModContainer container) {
        // Register the configuration screen
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @EventBusSubscriber(modid = TileFinder.MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Client setup code
            System.out.println("TileFinder Client Setup");

            // Force class loading to ensure event bus registration
            try {
                Class.forName(PathHighlighter.class.getName());
                System.out.println("PathHighlighter initialized");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}