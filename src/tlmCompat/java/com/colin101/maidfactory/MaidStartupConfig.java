package com.colin101.maidfactory;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 女仆兼容启动期配置。
 *
 * <p>只放"必须在 mod 初始化阶段就读取"的项。TLM 在 {@code addMaidTask} 时（世界还没加载）
 * 就要判断要不要注册女仆兼容，而 SERVER 配置那时尚未加载，所以这类项必须留在 COMMON 配置里。</p>
 *
 * <p>Cloth 配置界面里仍然显示这个列表（跟着"女仆"分类一起，非单人环境只读）。
 * 改动需要重启游戏才生效。</p>
 */
public class MaidStartupConfig {

    public static final MaidStartupConfig INSTANCE;
    public static final ForgeConfigSpec SPEC;

    public final ForgeConfigSpec.ConfigValue<List<? extends String>> blockedMods;

    static {
        Pair<MaidStartupConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(MaidStartupConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private MaidStartupConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("车万女仆兼容 - 启动期设置（改动需重启游戏）").push("maid");

        blockedMods = builder
                .comment("当以下任意模组被加载时，自动禁用女仆兼容",
                         "填入模组 ID，例如 'some_conflicting_mod'",
                         "留空表示不屏蔽任何模组",
                         "改动需要重启游戏才生效",
                         "默认: [maidassemblyline]")
                .defineListAllowEmpty("blocked_mods",
                        List.of("maidassemblyline"),
                        obj -> obj instanceof String);

        builder.pop();
    }

    /**
     * 由主类通过反射调用。
     * 注册独立的 COMMON 配置文件：config/create_hand_made-maid.toml
     */
    public static void registerSpec() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, SPEC, "create_hand_made-maid.toml");
    }
}
