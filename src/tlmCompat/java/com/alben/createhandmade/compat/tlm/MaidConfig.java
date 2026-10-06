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
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> blockedMods;

    static {
        Pair<MaidConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(MaidConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private MaidConfig(ForgeConfigSpec.Builder builder) {
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