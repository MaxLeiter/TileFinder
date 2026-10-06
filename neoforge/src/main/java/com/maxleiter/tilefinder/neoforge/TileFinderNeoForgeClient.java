package com.maxleiter.tilefinder.neoforge;

import com.maxleiter.tilefinder.TileFinder;
import com.maxleiter.tilefinder.client.BeamRenderer;
import com.maxleiter.tilefinder.client.Keys;
import com.maxleiter.tilefinder.client.TileFinderClient;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26 {
import net.neoforged.neoforge.client.event.RenderFrameEvent;
//?} else
/*import net.neoforged.neoforge.client.event.RenderLevelStageEvent;*/

@Mod(value = TileFinder.MOD_ID, dist = Dist.CLIENT)
public final class TileFinderNeoForgeClient {
    public TileFinderNeoForgeClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(FMLClientSetupEvent.class, event -> TileFinderClient.init());
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=26
            event.registerCategory(Keys.CATEGORY);
            for (KeyMapping key : Keys.ALL) event.register(key);
        });
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> TileFinderClient.settingsScreen(parent));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> TileFinderClient.tick(Minecraft.getInstance()));
        //? if >=26 {
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class,
                event -> BeamRenderer.submitGizmos(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            BeamRenderer.renderImmediate(event.getPoseStack(), event.getCamera().getPosition(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
        });
        *///?}
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
            //? if >=26 {
            boolean open = Keys.OPEN.matches(event.getKeyEvent());
            //?} else
            /*boolean open = Keys.OPEN.matches(event.getKeyCode(), event.getScanCode());*/
            if (open && TileFinderClient.onScreenKey(event.getScreen())) event.setCanceled(true);
        });
    }
}
