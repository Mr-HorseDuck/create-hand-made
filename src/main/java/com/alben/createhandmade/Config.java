package com.alben.createhandmade;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    // ==================== 手锯 ====================
    public final ForgeConfigSpec.BooleanValue enableTreeFelling;
    public final ForgeConfigSpec.IntValue handSawWindupTicks;
    public final ForgeConfigSpec.IntValue handSawCutTicks;

    // ==================== 冲压锤 ====================
    public final ForgeConfigSpec.IntValue pressHammerChargeTicks;
    public final ForgeConfigSpec.IntValue pressHammerChargeWindowTicks;

    // ==================== 风箱 ====================
    public final ForgeConfigSpec.IntValue bellowsChargeTicks;

    // ==================== 研钵 ====================
    public final ForgeConfigSpec.IntValue mortarWindupTicks;
    public final ForgeConfigSpec.IntValue mortarProcessingTicks;
    public final ForgeConfigSpec.IntValue mortarPoundCycleTicks;

    // ==================== 碾钵 ====================
    public final ForgeConfigSpec.IntValue crusherMortarWindupTicks;
    public final ForgeConfigSpec.IntValue crusherMortarProcessingTicks;
    public final ForgeConfigSpec.IntValue crusherMortarPoundCycleTicks;

    // ==================== 搅拌杖 ====================
    public final ForgeConfigSpec.IntValue stirringStaffWindupTicks;
    public final ForgeConfigSpec.IntValue stirringStaffProcessingTicks;

    // ==================== 调试 ====================
    public final ForgeConfigSpec.BooleanValue debugEnabled;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config(ForgeConfigSpec.Builder builder) {
        builder.comment("Create: Hand Made 服务端配置",
                        "此文件为 SERVER 类型：单人游戏可改，多人游戏由服务端权威，客户端只读")
                .push("create_hand_made");

        // ==================== 工具时间参数 ====================
        builder.comment("工具时间参数（单位：tick，20 tick = 1 秒）").push("tools");

        // ---- 手锯 ----
        builder.comment("手锯设置").push("hand_saw");

        enableTreeFelling = builder
                .comment("是否开启手锯的整树砍伐功能",
                         "true  = 潜行+左键砍树时，会砍倒整棵树",
                         "false = 只会破坏单个方块",
                         "默认: true")
                .define("enable_tree_felling", true);

        handSawWindupTicks = builder
                .comment("起手时长：每次长按切削开始时的准备时间",
                         "默认: 10")
                .defineInRange("windup_ticks", 10, 0, 200);

        handSawCutTicks = builder
                .comment("单次切削时长：起手结束后，每切削一次所需的 tick 数",
                         "默认: 20")
                .defineInRange("cut_ticks", 20, 1, 200);

        builder.pop(); // hand_saw

        // ---- 冲压锤 ----
        builder.comment("冲压锤设置").push("press_hammer");

        pressHammerChargeTicks = builder
                .comment("蓄力时长：按住右键多久后可以释放",
                         "默认: 15")
                .defineInRange("charge_ticks", 15, 1, 200);

        pressHammerChargeWindowTicks = builder
                .comment("蓄力命中窗口：蓄力释放后，暴击和额外击退的有效时间",
                         "默认: 20")
                .defineInRange("charge_window_ticks", 20, 1, 200);

        builder.pop(); // press_hammer

        // ---- 风箱 ----
        builder.comment("风箱设置").push("bellows");

        bellowsChargeTicks = builder
                .comment("蓄力时长：按住右键多久后可以释放",
                         "默认: 10")
                .defineInRange("charge_ticks", 10, 1, 200);

        builder.pop(); // bellows

        // ---- 研钵 ----
        builder.comment("研钵设置").push("mortar");

        mortarWindupTicks = builder
                .comment("起手时长：每次研磨开始时的准备时间",
                         "默认: 20")
                .defineInRange("windup_ticks", 20, 0, 200);

        mortarProcessingTicks = builder
                .comment("加工时长：起手结束后，完成一次研磨所需的 tick 数",
                         "默认: 100")
                .defineInRange("processing_ticks", 100, 1, 400);

        mortarPoundCycleTicks = builder
                .comment("捶捣周期：杵上下捶捣一次的 tick 数",
                         "默认: 10")
                .defineInRange("pound_cycle_ticks", 10, 1, 100);

        builder.pop(); // mortar

        // ---- 碾钵 ----
        builder.comment("碾钵设置").push("crusher_mortar");

        crusherMortarWindupTicks = builder
                .comment("起手时长：每次粉碎开始时的准备时间",
                         "默认: 20")
                .defineInRange("windup_ticks", 20, 0, 200);

        crusherMortarProcessingTicks = builder
                .comment("加工时长：起手结束后，完成一次粉碎所需的 tick 数",
                         "默认: 100")
                .defineInRange("processing_ticks", 100, 1, 400);

        crusherMortarPoundCycleTicks = builder
                .comment("捶捣周期：杵上下捶捣一次的 tick 数",
                         "默认: 10")
                .defineInRange("pound_cycle_ticks", 10, 1, 100);

        builder.pop(); // crusher_mortar

        // ---- 搅拌杖 ----
        builder.comment("搅拌杖设置").push("stirring_staff");

        stirringStaffWindupTicks = builder
                .comment("起手时长：每次搅拌开始时的准备时间",
                         "默认: 8")
                .defineInRange("windup_ticks", 8, 0, 200);

        stirringStaffProcessingTicks = builder
                .comment("加工时长：起手结束后，完成一次搅拌所需的 tick 数",
                         "默认: 60")
                .defineInRange("processing_ticks", 60, 1, 400);

        builder.pop(); // stirring_staff

        builder.pop(); // tools

        // ==================== 调试 ====================
        builder.comment("调试选项").push("debug");

        debugEnabled = builder
                .comment("是否输出 [HandMade-Debug] 调试日志",
                         "用于排查女仆兼容 / 指杆标记 / 配方查询问题",
                         "注意：SERVER 配置在多人游戏里客户端读不到，",
                         "      debug 日志只在服务端生效；单人游戏不受影响",
                         "默认: false")
                .define("enable_debug", false);

        builder.pop(); // debug

        builder.pop(); // create_hand_made
    }
}