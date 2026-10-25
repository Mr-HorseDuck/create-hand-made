package com.alben.createhandmade;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** 注册助手：统一 {@code create_hand_made} 命名空间。 */
public final class ModRegistration {

    private ModRegistration() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CreateHandMade.MODID, path);
    }

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, id(path));
    }

    public static <T> T register(Registry<T> registry, String path, T value) {
        return Registry.register(registry, id(path), value);
    }

    public static <T> T register(Registry<T> registry, ResourceKey<T> key, T value) {
        return Registry.register(registry, key, value);
    }
}
