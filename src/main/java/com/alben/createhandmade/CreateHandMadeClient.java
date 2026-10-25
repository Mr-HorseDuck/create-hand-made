package com.alben.createhandmade;

import com.alben.createhandmade.client.BellowsItemModel;
import com.alben.createhandmade.client.HandSawHudRenderer;
import com.alben.createhandmade.client.HandSawItemModel;
import com.alben.createhandmade.client.MortarItemModel;
import com.alben.createhandmade.client.StirringStaffItemModel;
import com.alben.createhandmade.network.client.ClientPayloadHandler;
import com.alben.createhandmade.particle.ModParticleTypesClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;

/** 客户端入口：粒子工厂 + 网络接收器 + HUD + 自定义物品模型类型。 */
public class CreateHandMadeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        new ModParticleTypesClient().onInitializeClient();
        ClientPayloadHandler.register();

        // 26.1 的物品模型类型是 LateBoundIdMapper（延迟绑定），客户端初始化时补类型即可，
        // 之后 items/*.json 里的 "type": "create_hand_made:xxx" 就能解析。
        // 该字段已被 fabric-transitive-access-wideners-v1 放宽为 public。
        ItemModels.ID_MAPPER.put(MortarItemModel.TYPE_ID, MortarItemModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(HandSawItemModel.TYPE_ID, HandSawItemModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(BellowsItemModel.TYPE_ID, BellowsItemModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(StirringStaffItemModel.TYPE_ID, StirringStaffItemModel.Unbaked.MAP_CODEC);

        // 手锯 HUD（旧版是 NeoForge 的 RenderGuiEvent.Post）
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "hand_saw_hud"),
                new HandSawHudRenderer());
    }
}
