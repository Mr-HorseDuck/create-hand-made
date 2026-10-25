package com.alben.createhandmade;

import com.alben.createhandmade.item.InfusionGunContents;
import com.alben.createhandmade.item.MortarContents;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;

public final class ModDataComponents {

    private ModDataComponents() {
    }

    public static final DataComponentType<MortarContents> MORTAR_CONTENTS = register(
            "mortar_contents",
            DataComponentType.<MortarContents>builder()
                    .persistent(MortarContents.CODEC)
                    .networkSynchronized(MortarContents.STREAM_CODEC)
                    .build());

    public static final DataComponentType<InfusionGunContents> INFUSION_GUN_CONTENTS = register(
            "infusion_gun_contents",
            DataComponentType.<InfusionGunContents>builder()
                    .persistent(InfusionGunContents.CODEC)
                    .networkSynchronized(InfusionGunContents.STREAM_CODEC)
                    .build());

    /** 手锯当前选中的切削配方索引。 */
    public static final DataComponentType<Integer> HAND_SAW_RECIPE_INDEX = register(
            "hand_saw_recipe_index",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        ResourceKey<DataComponentType<?>> key = ResourceKey.create(Registries.DATA_COMPONENT_TYPE, ModRegistration.id(name));
        return (DataComponentType<T>) net.minecraft.core.Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type);
    }

    public static void register() {
        // 静态初始化即注册
    }
}
