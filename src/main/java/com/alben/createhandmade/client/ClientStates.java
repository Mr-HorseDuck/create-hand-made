package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.config.SyncedServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端会话级状态：搅拌追踪 + 断线清理，二合一。
 */
public final class ClientStates {

    private ClientStates() {}

    /**
     * 哪些工作盆正在被"搅拌杖"作用。只在客户端使用，不涉及网络同步。
     */
    public static final class Stirring {

        /** 标记后多少 tick 内视为仍在搅拌（防抖） */
        private static final long WINDOW_TICKS = 5;

        private static final Map<BlockPos, Long> ACTIVE = new HashMap<>();

        private Stirring() {}

        /** 由 StirringStaffItem.onUseTick 在客户端调用 */
        public static void markStirring(BlockPos pos) {
            Level level = Minecraft.getInstance().level;
            if (level == null) return;
            ACTIVE.put(pos.immutable(), level.getGameTime());
        }

        /** 由 Mixin 查询 */
        public static boolean isBeingStirred(BlockPos pos) {
            Level level = Minecraft.getInstance().level;
            if (level == null) return false;

            Long last = ACTIVE.get(pos);
            if (last == null) return false;

            if (level.getGameTime() - last > WINDOW_TICKS) {
                ACTIVE.remove(pos);
                return false;
            }
            return true;
        }

        /** 玩家断开时清空 */
        public static void clear() {
            ACTIVE.clear();
        }
    }

    /**
     * 断线 / 退服时清掉客户端侧的会话级状态。
     *
     * <p>这里管的是「只在一个连接内有效」的客户端数据，换服后必须归零：</p>
     *
     * <ul>
     *   <li>{@link SyncedServerConfig}：服务端配置镜像。否则从多人服退回单人游戏后，
     *       客户端仍沿用上个服务端的参数（起手提示音时机、使用时长、动画周期全对不上）。</li>
     *   <li>{@link Stirring}：哪些工作盆正被搅拌杖作用。这是按 BlockPos 记的，
     *       跨维度/跨存档同名坐标会撞上，表现为进新世界后某个工作盆「一进来就在搅拌」。
     *       以前 {@code clear()} 没有任何调用点，等于永久残留。</li>
     * </ul>
     */
    @Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
    public static final class Reset {

        private Reset() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SyncedServerConfig.reset();
            Stirring.clear();
        }
    }
}
