package com.alben.createhandmade.particle;

import com.alben.createhandmade.CreateHandMade;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CreateHandMade.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModParticleTypesClient {

    @SubscribeEvent
    public static void registerFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticleTypes.BELLOWS_AIR.get(),
                BellowsAirParticleProvider::new
        );
    }
}