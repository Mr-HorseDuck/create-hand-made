package com.alben.createhandmade.particle;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 粒子类型注册 + 客户端粒子工厂注册（同一个粒子，放同一个文件）。
 */
public class ModParticleTypes {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CreateHandMade.MODID);

    public static final RegistryObject<ParticleType<BellowsAirParticle.Data>> BELLOWS_AIR =
            PARTICLE_TYPES.register("bellows_air",
                    () -> new BellowsAirParticle.Data().createType());

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }

    /** 客户端：把粒子工厂挂到对应的 sprite set 上。 */
    @Mod.EventBusSubscriber(
            modid = CreateHandMade.MODID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Client {

        @SubscribeEvent
        public static void registerFactories(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(
                    ModParticleTypes.BELLOWS_AIR.get(),
                    BellowsAirParticle.Provider::new
            );
        }
    }
}
