package com.alben.createhandmade.config;

import com.alben.createhandmade.Config;

/**
 * 服务端配置在客户端侧的一份镜像。
 *
 * <p>背景：{@link Config} 注册为 {@code ModConfig.Type.SERVER}，多人游戏里客户端根本
 * 读不到（{@code ConfigValue#get()} 在客户端返回的是默认值），于是客户端侧的表现 ——
 * 起手提示音时机、{@code getUseDuration} 算出的使用时长、各种动画周期 —— 会和服务端
 * 实际用的参数不一致。这里的做法是让服务端在玩家登录、配置重载时把参数推给客户端，
 * 客户端各处统一从本类取值。</p>
 *
 * <p>没收到同步时（单人游戏、开发环境、以及服务端自己）自动回落到 {@link Config}，
 * 所以本类两侧都能安全调用，调用方不必判断 {@code isClientSide}。</p>
 */
public final class SyncedServerConfig {

    /** 槽位表长度；{@link #capture()} 与 {@link #apply(int[])} 的顺序必须一致。 */
    private static final int SLOT_COUNT = 14;

    private static volatile boolean synced = false;

    private static volatile boolean enableTreeFelling = true;
    private static volatile int handSawWindupTicks;
    private static volatile int handSawCutTicks;
    private static volatile int pressHammerChargeTicks;
    private static volatile int pressHammerChargeWindowTicks;
    private static volatile int bellowsChargeTicks;
    private static volatile int mortarWindupTicks;
    private static volatile int mortarProcessingTicks;
    private static volatile int mortarPoundCycleTicks;
    private static volatile int crusherMortarWindupTicks;
    private static volatile int crusherMortarProcessingTicks;
    private static volatile int crusherMortarPoundCycleTicks;
    private static volatile int stirringStaffWindupTicks;
    private static volatile int stirringStaffProcessingTicks;

    private SyncedServerConfig() {
    }

    // ================= 同步 =================

    /** 是否已经收到过服务端同步（仅用于排查，取值逻辑不该依赖它）。 */
    public static boolean isSynced() {
        return synced;
    }

    /** 服务端侧取当前配置参数，打包待发。只能在服务端调用（配置此时已加载）。 */
    public static int[] capture() {
        return new int[]{
                Config.INSTANCE.handSawWindupTicks.get(),
                Config.INSTANCE.handSawCutTicks.get(),
                Config.INSTANCE.pressHammerChargeTicks.get(),
                Config.INSTANCE.pressHammerChargeWindowTicks.get(),
                Config.INSTANCE.bellowsChargeTicks.get(),
                Config.INSTANCE.mortarWindupTicks.get(),
                Config.INSTANCE.mortarProcessingTicks.get(),
                Config.INSTANCE.mortarPoundCycleTicks.get(),
                Config.INSTANCE.crusherMortarWindupTicks.get(),
                Config.INSTANCE.crusherMortarProcessingTicks.get(),
                Config.INSTANCE.crusherMortarPoundCycleTicks.get(),
                Config.INSTANCE.stirringStaffWindupTicks.get(),
                Config.INSTANCE.stirringStaffProcessingTicks.get(),
                Config.INSTANCE.enableTreeFelling.get() ? 1 : 0
        };
    }

    /** 客户端收到同步包时调用；长度不符（版本不匹配）直接忽略，回落默认值。 */
    public static void apply(int[] values) {
        if (values == null || values.length < SLOT_COUNT) return;

        handSawWindupTicks = values[0];
        handSawCutTicks = values[1];
        pressHammerChargeTicks = values[2];
        pressHammerChargeWindowTicks = values[3];
        bellowsChargeTicks = values[4];
        mortarWindupTicks = values[5];
        mortarProcessingTicks = values[6];
        mortarPoundCycleTicks = values[7];
        crusherMortarWindupTicks = values[8];
        crusherMortarProcessingTicks = values[9];
        crusherMortarPoundCycleTicks = values[10];
        stirringStaffWindupTicks = values[11];
        stirringStaffProcessingTicks = values[12];
        enableTreeFelling = values[13] != 0;

        synced = true;
    }

    /** 断开连接时清空，避免离开多人服务器后进单人游戏仍沿用旧服务端的参数。 */
    public static void reset() {
        synced = false;
    }

    // ================= 取值 =================

    public static int handSawWindup() {
        return synced ? handSawWindupTicks : Config.INSTANCE.handSawWindupTicks.get();
    }

    /** 是否开启手锯整树砍伐。 */
    public static boolean isTreeFellingEnabled() {
        return synced ? enableTreeFelling : Config.INSTANCE.enableTreeFelling.get();
    }

    public static int handSawCut() {
        return synced ? handSawCutTicks : Config.INSTANCE.handSawCutTicks.get();
    }

    public static int pressHammerCharge() {
        return synced ? pressHammerChargeTicks : Config.INSTANCE.pressHammerChargeTicks.get();
    }

    public static int pressHammerChargeWindow() {
        return synced ? pressHammerChargeWindowTicks : Config.INSTANCE.pressHammerChargeWindowTicks.get();
    }

    public static int bellowsCharge() {
        return synced ? bellowsChargeTicks : Config.INSTANCE.bellowsChargeTicks.get();
    }

    public static int mortarWindup() {
        return synced ? mortarWindupTicks : Config.INSTANCE.mortarWindupTicks.get();
    }

    public static int mortarProcessing() {
        return synced ? mortarProcessingTicks : Config.INSTANCE.mortarProcessingTicks.get();
    }

    public static int mortarPoundCycle() {
        return synced ? mortarPoundCycleTicks : Config.INSTANCE.mortarPoundCycleTicks.get();
    }

    public static int crusherMortarWindup() {
        return synced ? crusherMortarWindupTicks : Config.INSTANCE.crusherMortarWindupTicks.get();
    }

    public static int crusherMortarProcessing() {
        return synced ? crusherMortarProcessingTicks : Config.INSTANCE.crusherMortarProcessingTicks.get();
    }

    public static int crusherMortarPoundCycle() {
        return synced ? crusherMortarPoundCycleTicks : Config.INSTANCE.crusherMortarPoundCycleTicks.get();
    }

    public static int stirringStaffWindup() {
        return synced ? stirringStaffWindupTicks : Config.INSTANCE.stirringStaffWindupTicks.get();
    }

    public static int stirringStaffProcessing() {
        return synced ? stirringStaffProcessingTicks : Config.INSTANCE.stirringStaffProcessingTicks.get();
    }
}
