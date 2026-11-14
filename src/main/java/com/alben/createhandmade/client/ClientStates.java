package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.config.SyncedServerConfig;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

        /**
         * 当前仍在搅拌的所有工作盆坐标，同时把过期条目清掉。
         *
         * <p>返回的是新列表，调用方可以放心遍历（不会与 {@link #markStirring} 并发改同一个
         * 迭代器）。没有在搅拌时是空列表，调用方应先判空再往下做。</p>
         *
         * <p>过滤规则与"是否仍在搅拌"一致：最后一次标记距今超过 {@link #WINDOW_TICKS}
         * 即视为结束。原先还有一个只查单个坐标的 {@code isBeingStirred}，它是给 Mixin
         * 用的，移除 Mixin 后已无调用方，一并删除。</p>
         */
        public static List<BlockPos> activePositions() {
            Level level = Minecraft.getInstance().level;
            if (level == null) return List.of();

            // ★ 没有任何人搅拌时（绝大多数 tick 都是这种）直接返回共享的空列表，
            //   不分配 ArrayList——本方法在客户端每 tick 的 Phase.END 里都会被调用。
            if (ACTIVE.isEmpty()) return List.of();

            long now = level.getGameTime();
            List<BlockPos> out = new ArrayList<>(ACTIVE.size());
            Iterator<Map.Entry<BlockPos, Long>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Long> entry = it.next();
                if (now - entry.getValue() > WINDOW_TICKS) {
                    it.remove();
                } else {
                    out.add(entry.getKey());
                }
            }
            return out;
        }

        /** 玩家断开时清空 */
        public static void clear() {
            ACTIVE.clear();
        }
    }

    /**
     * 让"手动搅拌"时工作盆里的液体动起来。
     *
     * <p>背景：{@code BasinBlockEntity.lazyTick()} 每 10 tick 只根据"上方是否有正在运行的
     * 机械搅拌器"来决定 {@code areFluidsMoving}：上方没有搅拌器时直接置 false。玩家用搅拌杖
     * 手搓时上方恰恰没有搅拌器，于是液面永远是静止的、也不冒"流动"粒子。</p>
     *
     * <p>以前是靠一个 {@code @Redirect} Mixin 劫持那次 {@code setAreFluidsMoving} 调用，
     * 现在改成用**公开 API**（{@code areFluidsMoving()} / {@code setAreFluidsMoving(boolean)}）
     * 在客户端 tick 末尾补写一次，不需要注入任何字节码。</p>
     *
     * <p><b>为什么放在 Phase.END：</b>{@code lazyTick()} 在 {@code ClientLevel.tick()} 内执行，
     * 而 Forge 的 {@code onPostClientTick()} 是在 {@code Minecraft.tick()} 的最末尾才触发
     * （见 Forge 的 {@code patches/net/minecraft/client/Minecraft.java.patch}），
     * 所以这里的赋值一定发生在 lazyTick 之后——本帧渲染看到的就是 true。</p>
     *
     * <p><b>与 Mixin 版本的唯一差别（可接受，但要知道）：</b>Mixin 是把 lazyTick 里那次
     * {@code setAreFluidsMoving(false)} 直接替换掉，所以标志位**从不**变假；本实现没有拦截
     * 能力，每 10 tick 会被 lazyTick 真的置假一次，我们在同一个 tick 末尾再置真。副作用是那
     * 一个 tick 里 {@code LerpedFloat.tickChaser()} 已经朝 0 走了一步（{@code Chaser.EXP}
     * 每 tick 走 10% 的差值），液面旋转速度因此在 18~20 之间轻微起伏（约 2 Hz、幅度 10%）；
     * 同一 tick 的粒子判定也会按"静止"走一次（仍有 1/8 概率生成静止粒子）。
     * 视觉上几乎看不出来。如果以后发现这点抖动明显，可选的补救是：客户端在搅拌时按需补发
     * "流动"粒子（自己调 ParticleEngine），而不是再退回 Mixin。</p>
     */
    @Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
    public static final class StirAnimation {

        private StirAnimation() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            List<BlockPos> stirring = Stirring.activePositions();
            if (stirring.isEmpty()) return;

            Level level = Minecraft.getInstance().level;
            if (level == null) return;

            for (BlockPos pos : stirring) {
                if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) continue;

                // 上方有正在运行的机械搅拌器 → 交给原版逻辑（那次调用自己会置 true）
                BlockEntity above = level.getBlockEntity(pos.above(2));
                if (above instanceof MechanicalMixerBlockEntity mixer && mixer.running) continue;

                if (!basin.areFluidsMoving()) {
                    basin.setAreFluidsMoving(true);
                }
            }
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
