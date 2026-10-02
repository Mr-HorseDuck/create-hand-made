package com.alben.createhandmade.bellows;

import com.alben.createhandmade.CreateHandMade;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID)
public class BellowsMediaEvents {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new BellowsMediaReloadListener());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BellowsMediaRegistry.autoDiscover();
    }
}