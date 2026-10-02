package com.alben.createhandmade.particle;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModParticleTypes {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CreateHandMade.MODID);

    public static final RegistryObject<ParticleType<BellowsAirParticleData>> BELLOWS_AIR =
            PARTICLE_TYPES.register("bellows_air",
                    () -> new BellowsAirParticleData().createType());

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}