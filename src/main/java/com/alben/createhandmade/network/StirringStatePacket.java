package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 搅拌杖开始搅拌某个工作盆（服务端 → 客户端，用于客户端动画）。 */
public record StirringStatePacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StirringStatePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "stirring_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StirringStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StirringStatePacket::pos,
                    StirringStatePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
