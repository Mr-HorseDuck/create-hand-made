package com.alben.createhandmade.particle;

import com.alben.createhandmade.ModRegistration;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/** 粒子类型注册。 */
public final class ModParticleTypes {

    public static final ParticleType<BellowsAirParticleData> BELLOWS_AIR = register("bellows_air",
            new ParticleType<>(false) {
                @Override
                public com.mojang.serialization.MapCodec<BellowsAirParticleData> codec() {
                    return BellowsAirParticleData.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, BellowsAirParticleData> streamCodec() {
                    return BellowsAirParticleData.STREAM_CODEC;
                }
            });

    private ModParticleTypes() {
    }

    public static void register() {
        // 静态字段初始化即完成注册
    }

    private static ParticleType<BellowsAirParticleData> register(String name,
                                                                 ParticleType<BellowsAirParticleData> type) {
        ResourceKey<ParticleType<?>> key = ResourceKey.create(Registries.PARTICLE_TYPE, ModRegistration.id(name));
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, key, type);
    }
}
