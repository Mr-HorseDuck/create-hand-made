package com.alben.createhandmade.recipe;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/** 把 L2 的数据包加载器挂到 Fabric 的资源重载总线上。 */
public final class HandMadeRecipeFilterEvents {

    private HandMadeRecipeFilterEvents() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new HandMadeRecipeFilterLoader());
    }
}
