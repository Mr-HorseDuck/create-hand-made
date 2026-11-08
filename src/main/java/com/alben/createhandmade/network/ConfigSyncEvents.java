package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 把 SERVER 配置同步到客户端的时机：
 *
 * <ul>
 *   <li>玩家登录 —— 客户端进场时拿到一次当前参数；</li>
 *   <li>服务端配置重载（{@code /reload} 或改 toml 后存档事件）—— 广播给所有在线玩家，
 *       这样热重载的改动能同步到客户端，不至于让动画/使用时长和服务端脱节。</li>
 * </ul>
 */
public final class ConfigSyncEvents {

    private ConfigSyncEvents() {
    }

    @Mod.EventBusSubscriber(modid = CreateHandMade.MODID)
    public static final class ForgeBusEvents {

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModNetwork.sendConfigSync(player);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = CreateHandMade.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {

        @SubscribeEvent
        public static void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getType() != ModConfig.Type.SERVER) return;
            ModNetwork.broadcastConfigSync();
        }
    }
}
