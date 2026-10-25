package com.alben.createhandmade.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * 网络包注册（Fabric）。
 *
 * <p>本模组目前只有服务端 → 客户端的方向；客户端接收器在
 * {@link com.alben.createhandmade.network.client.ClientPayloadHandler} 里注册。</p>
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    public static void register() {
        var clientbound = PayloadTypeRegistry.clientboundPlay();
        clientbound.register(HighlightBlockPacket.TYPE, HighlightBlockPacket.STREAM_CODEC);
        clientbound.register(PressParticlesPacket.TYPE, PressParticlesPacket.STREAM_CODEC);
        clientbound.register(FluidParticlesPacket.TYPE, FluidParticlesPacket.STREAM_CODEC);
        clientbound.register(BellowsBlastPacket.TYPE, BellowsBlastPacket.STREAM_CODEC);
        clientbound.register(StirringStatePacket.TYPE, StirringStatePacket.STREAM_CODEC);
        clientbound.register(HandSawRecipesPacket.TYPE, HandSawRecipesPacket.STREAM_CODEC);
    }
}
