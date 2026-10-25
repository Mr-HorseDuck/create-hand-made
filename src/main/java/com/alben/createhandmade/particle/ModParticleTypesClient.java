package com.alben.createhandmade.particle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

/** 客户端粒子工厂注册。 */
public final class ModParticleTypesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance()
                .register(ModParticleTypes.BELLOWS_AIR, BellowsAirParticleProvider::new);
    }
}
