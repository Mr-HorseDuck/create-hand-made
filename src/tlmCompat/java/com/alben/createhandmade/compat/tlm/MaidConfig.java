package com.alben.createhandmade.compat.tlm;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 女仆兼容独立配置。
 * ★ 由 CreateHandMade 主类通过反射调用 registerSpec()，避免 main 编译时依赖 tlmCompat。
 */
public class MaidConfig {

    public static final MaidConfig INSTANCE;
    public static final ForgeConfigSpec SPEC;

    public final ForgeConfigSpec.DoubleValue maidSearchRadius;
    public final ForgeConfigSpec.IntValue maidPressHammerCooldown;
    public final ForgeConfigSpec.IntValue maidInfusionGunCooldown;
    public final ForgeConfigSpec.IntValue maidHandSawCooldown;
    public final ForgeConfigSpec.IntValue maidBellowsCooldown;
    public final ForgeConfigSpec.IntValue maidPointerDeployCooldown;
    public final ForgeConfigSpec.IntValue maidStirringStaffCooldown;
    public final ForgeConfigSpec.IntValue maidMortarCooldown;
    public final ForgeConfigSpec.IntValue maidParticleLevel;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> blockedMods;

    static {
        Pair<MaidConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(MaidConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private MaidConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("车万女仆兼容设置").push("maid");

        maidSearchRadius = builder
                .comment("女仆与容器/工作方块的最大交互距离（格）",
                         "作用于：灌注枪就近搜索 + 指杆标记（冲压锤/手锯/液体搬运/搅拌杖/风箱/指杆部署）",
                         "默认: 8.0")
                .defineInRange("search_radius", 8.0, 1.0, 32.0);

        maidPressHammerCooldown = builder
                .comment("女仆使用冲压锤后的冷却时间（tick）",
                         "默认: 16")
                .defineInRange("press_hammer_cooldown", 16, 1, 200);

        maidInfusionGunCooldown = builder
                .comment("女仆使用灌注枪后的冷却时间（tick）",
                         "液体搬运行为共用此配置",
                         "默认: 16")
                .defineInRange("infusion_gun_cooldown", 16, 1, 200);

        maidHandSawCooldown = builder
                .comment("女仆使用手锯后的冷却时间（tick）",
                         "默认: 20")
                .defineInRange("hand_saw_cooldown", 20, 1, 400);

        maidBellowsCooldown = builder
                .comment("女仆使用风箱后的冷却时间（tick）",
                         "默认: 16")
                .defineInRange("bellows_cooldown", 16, 1, 200);

        maidPointerDeployCooldown = builder
                .comment("女仆使用指杆部署后的冷却时间（tick）",
                         "默认: 16")
                .defineInRange("pointer_deploy_cooldown", 16, 1, 200);

        maidStirringStaffCooldown = builder
                .comment("女仆使用搅拌杖后的冷却时间（tick）",
                         "默认: 16")
                .defineInRange("stirring_staff_cooldown", 16, 1, 200);

        maidMortarCooldown = builder
                .comment("女仆使用研钵/碾钵后的冷却时间（tick）",
                         "两者共用此配置",
                         "默认: 60")
                .defineInRange("mortar_cooldown", 60, 1, 400);

                maidParticleLevel = builder
        .comment("女仆加工粒子数量等级（1-5）",
                 "1 = 最少，3 = 默认，5 = 最多",
                 "所有女仆行为的粒子数量统一按此等级缩放",
                 "默认: 3")
        .defineInRange("particle_level", 3, 1, 5);

        blockedMods = builder
                .comment("当以下任意模组被加载时，自动禁用女仆兼容",
                         "填入模组 ID，例如 'some_conflicting_mod'",
                         "留空表示不屏蔽任何模组",
                         "默认: [maidassemblyline]")
                .defineListAllowEmpty("blocked_mods",
                        List.of("maidassemblyline"),
                        obj -> obj instanceof String);

        builder.pop();
    }

    /**
     * 由主类通过反射调用。
     * 注册独立的 COMMON 配置文件：create_hand_made-maid.toml
     */
    public static void registerSpec() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "create_hand_made-maid.toml");
    }
}