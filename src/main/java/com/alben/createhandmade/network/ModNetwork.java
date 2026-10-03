package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * ★ Forge 1.20.1 版本：
 *   - 用 SimpleChannel 替代 NeoForge 的 PayloadRegistrar
 *   - 用 messageBuilder(...).encoder(...).decoder(...).consumerMainThread(...).add() 注册每个包
 *   - 客户端处理逻辑用 DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...) 包裹，
 *     避免服务端加载客户端类时抛 NoClassDefFoundError
 *
 *   ★ 需要在主类 CreateHandMade 的构造函数里调用 ModNetwork.register()，
 *     或在 FMLCommonSetupEvent 里调用。
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CreateHandMade.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // ================= HighlightBlockPacket =================
        CHANNEL.messageBuilder(HighlightBlockPacket.class, id++)
                .encoder(HighlightBlockPacket::encode)
                .decoder(HighlightBlockPacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handleHighlight(packet)))
                .add();

        // ================= PressParticlesPacket =================
        CHANNEL.messageBuilder(PressParticlesPacket.class, id++)
                .encoder(PressParticlesPacket::encode)
                .decoder(PressParticlesPacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handlePressParticles(packet)))
                .add();

        // ================= FluidParticlesPacket =================
        CHANNEL.messageBuilder(FluidParticlesPacket.class, id++)
                .encoder(FluidParticlesPacket::encode)
                .decoder(FluidParticlesPacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handleFluidParticles(packet)))
                .add();

        // ================= BellowsBlastPacket =================
        CHANNEL.messageBuilder(BellowsBlastPacket.class, id++)
                .encoder(BellowsBlastPacket::encode)
                .decoder(BellowsBlastPacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handleBellowsBlast(packet)))
                .add();

        // ================= StirringStatePacket =================
        CHANNEL.messageBuilder(StirringStatePacket.class, id++)
                .encoder(StirringStatePacket::encode)
                .decoder(StirringStatePacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handleStirringState(packet)))
                .add();
    }
}