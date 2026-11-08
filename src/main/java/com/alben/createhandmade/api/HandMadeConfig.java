package com.alben.createhandmade.api;

import com.alben.createhandmade.config.SyncedServerConfig;

/**
 * 配置查询门面：给外部模组一个"问当前生效参数"的稳定入口，不用反射去摸 {@code Config}。
 *
 * <p>取值统一走 {@link SyncedServerConfig}：服务端返回配置文件里的真实值，客户端返回
 * 服务端同步下来的值（未收到同步时才回落到本地文件）。所以这些方法在两侧都能调，
 * 拿到的都是"这一局实际在用的参数"。</p>
 *
 * <p>女仆相关的冷却 / 距离 / blockedMods 不在这里 —— 它们属于可选的 TLM 兼容层
 * （{@code com.colin101.maidfactory.MaidConfig}），只有在装了车万女仆、
 * 且接入方自己依赖 TLM 时才取得到，还没有稳定的对外门面。</p>
 */
public final class HandMadeConfig {

    private HandMadeConfig() {
    }

    // ==================== 手锯 ====================

    /** 手锯起手时长（tick）。 */
    public static int handSawWindupTicks() {
        return SyncedServerConfig.handSawWindup();
    }

    /** 手锯单次切削时长（tick）。 */
    public static int handSawCutTicks() {
        return SyncedServerConfig.handSawCut();
    }

    /** 是否开启整树砍伐。 */
    public static boolean isTreeFellingEnabled() {
        return SyncedServerConfig.isTreeFellingEnabled();
    }

    // ==================== 冲压锤 ====================

    /** 冲压锤蓄力时长（tick）。 */
    public static int pressHammerChargeTicks() {
        return SyncedServerConfig.pressHammerCharge();
    }

    /** 蓄力命中窗口（tick）：蓄力释放后暴击/额外击退的有效时间。 */
    public static int pressHammerChargeWindowTicks() {
        return SyncedServerConfig.pressHammerChargeWindow();
    }

    // ==================== 风箱 ====================

    /** 风箱蓄力时长（tick）。 */
    public static int bellowsChargeTicks() {
        return SyncedServerConfig.bellowsCharge();
    }

    // ==================== 研钵 ====================

    public static int mortarWindupTicks() {
        return SyncedServerConfig.mortarWindup();
    }

    public static int mortarProcessingTicks() {
        return SyncedServerConfig.mortarProcessing();
    }

    /** 杵上下捶捣一次的周期（tick）。 */
    public static int mortarPoundCycleTicks() {
        return SyncedServerConfig.mortarPoundCycle();
    }

    // ==================== 碾钵 ====================

    public static int crusherMortarWindupTicks() {
        return SyncedServerConfig.crusherMortarWindup();
    }

    public static int crusherMortarProcessingTicks() {
        return SyncedServerConfig.crusherMortarProcessing();
    }

    public static int crusherMortarPoundCycleTicks() {
        return SyncedServerConfig.crusherMortarPoundCycle();
    }

    // ==================== 搅拌杖 ====================

    public static int stirringStaffWindupTicks() {
        return SyncedServerConfig.stirringStaffWindup();
    }

    public static int stirringStaffProcessingTicks() {
        return SyncedServerConfig.stirringStaffProcessing();
    }

    // ==================== 调试 ====================

    /**
     * 是否输出 [HandMade-Debug] 日志。
     *
     * <p>调试开关是"本侧是否打日志"，不从服务端同步：客户端看的是自己那份配置。</p>
     */
    public static boolean isDebugLoggingEnabled() {
        return com.alben.createhandmade.Config.INSTANCE.debugEnabled.get();
    }
}
