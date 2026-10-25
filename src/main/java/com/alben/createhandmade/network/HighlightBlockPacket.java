package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 指杆命中方块时的描边提示（服务端 → 客户端）。 */
public record HighlightBlockPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HighlightBlockPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "highlight_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HighlightBlockPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, HighlightBlockPacket::pos,
                    HighlightBlockPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
