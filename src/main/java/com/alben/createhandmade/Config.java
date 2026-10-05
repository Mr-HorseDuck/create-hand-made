package com.alben.createhandmade;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    /** 手锯设置 */
    public final ForgeConfigSpec.BooleanValue enableTreeFelling;

    /** 女仆设置 */
    public final ForgeConfigSpec.DoubleValue maidSearchRadius;
    public final ForgeConfigSpec.IntValue maidPressHammerCooldown;
    public final ForgeConfigSpec.IntValue maidInfusionGunCooldown;
    public final ForgeConfigSpec.IntValue maidHandSawCooldown;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config(ForgeConfigSpec.Builder builder) {
        builder.comment("Create: Hand Made 配置").push("create_hand_made");

        // ==================== 手锯 ====================
        builder.comment("手锯设置").push("hand_saw");

        enableTreeFelling = builder
                .comment("是否开启手锯的整树砍伐功能",
                         "true  = 潜行+左键砍树时，会砍倒整棵树",
                         "false = 只会破坏单个方块",
                         "默认: true")
                .define("enable_tree_felling", true);

        builder.pop(); // hand_saw

        // ==================== 女仆 ====================
        builder.comment("车万女仆兼容设置").push("maid");

        maidSearchRadius = builder
                .comment("女仆搜索目标方块的半径（格）",
                         "默认: 8.0")
                .defineInRange("search_radius", 8.0, 1.0, 32.0);

        maidPressHammerCooldown = builder
                .comment("女仆使用冲压锤后的冷却时间（tick）",
                         "默认: 20")
                .defineInRange("press_hammer_cooldown", 20, 1, 200);

        maidInfusionGunCooldown = builder
                .comment("女仆使用灌注枪后的冷却时间（tick）",
                         "默认: 20")
                .defineInRange("infusion_gun_cooldown", 20, 1, 200);

        maidHandSawCooldown = builder
                .comment("女仆使用手锯后的冷却时间（tick）",
                         "默认: 40")
                .defineInRange("hand_saw_cooldown", 40, 1, 400);


        builder.pop(); // maid
        builder.pop(); // create_hand_made
    }
}