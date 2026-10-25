package com.alben.createhandmade.bellows;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/** 风箱介质表的重载与自动发现接线。 */
public final class BellowsMediaEvents {

    private BellowsMediaEvents() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new BellowsMediaReloadListener());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> BellowsMediaRegistry.autoDiscover());
    }
}
