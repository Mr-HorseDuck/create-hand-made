package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.config.SyncedServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

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

    // ★ 配置同步包（id 5）加入后升到 "2"：老客户端/服务端会直接被判为不兼容，
    //   而不是收到包后按旧格式解出半截数据。
    private static final String PROTOCOL_VERSION = "2";

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

        // ================= ConfigSyncPacket =================
        CHANNEL.messageBuilder(ConfigSyncPacket.class, id++)
                .encoder(ConfigSyncPacket::encode)
                .decoder(ConfigSyncPacket::new)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.alben.createhandmade.network.client.ClientPayloadHandler
                                        .handleConfigSync(packet)))
                .add();
    }

    // ================= SERVER 配置 → 客户端 =================

    /** 把当前 SERVER 配置里的工具时间参数推给某个玩家（登录时调用）。 */
    public static void sendConfigSync(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ConfigSyncPacket(SyncedServerConfig.capture()));
    }

    /** 配置重载后广播给所有在线玩家；没有服务端（纯客户端环境）时什么也不做。 */
    public static void broadcastConfigSync() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;

        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ConfigSyncPacket(SyncedServerConfig.capture()));
    }
}