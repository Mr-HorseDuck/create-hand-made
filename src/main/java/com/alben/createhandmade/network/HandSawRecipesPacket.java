package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 手锯 HUD 数据（服务端 → 客户端）。
 *
 * <p>26.1 的客户端没有配方表，所以「副手这件东西能切出哪些产物」只能由服务端算好推过来。
 * 服务端只在（副手物品 + 组件）变化时推送一次；{@code input} 为空表示"当前没有可切的组合"，
 * 客户端据此清空 HUD。</p>
 *
 * @param input   副手物品（数量恒为 1），客户端用它校验数据是否仍然适用
 * @param outputs 每种配方一个代表产物（跳过空产物），按配方顺序
 */
public record HandSawRecipesPacket(ItemStack input, List<ItemStack> outputs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HandSawRecipesPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "hand_saw_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandSawRecipesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, HandSawRecipesPacket::input,
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, HandSawRecipesPacket::outputs,
                    HandSawRecipesPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
