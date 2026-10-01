package com.alben.createhandmade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端追踪：哪些工作盆正在被"搅拌杖"作用。
 * 只在客户端使用，不涉及网络同步。
 */
public class ClientStirringState {

    /** 标记后多少 tick 内视为仍在搅拌（防抖） */
    private static final long WINDOW_TICKS = 5;

    private static final Map<BlockPos, Long> ACTIVE = new HashMap<>();

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