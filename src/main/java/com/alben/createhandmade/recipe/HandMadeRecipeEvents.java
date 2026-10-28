package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID)
public class HandMadeRecipeEvents {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new HandMadeRecipeLoader());
    }
}