package com.alben.createhandmade.client.config;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面的「装配」入口：把主配置的 8 页拼出来，再追加可选模组提供的页
 * （目前是女仆配置的 3 页），最后交给 {@link HandMadeConfigScreen} 渲染。
 *
 * <p>主配置的每一项都直接对着 {@link Config} 里的 {@code ForgeConfigSpec.Value} 读写，
 * 范围与默认值照抄 {@code defineInRange} —— 界面里改不出配置文件不允许的值。</p>
 */
public final class ConfigScreens {

    private static final String P = "config.create_hand_made.";
    private static final String CAT = P + "category.";

    private ConfigScreens() {
    }

    /** 打开配置界面（{@code parent} 为 {@code null} 表示回到游戏）。 */
    public static void open(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(create(parent));
    }

    public static Screen create(Screen parent) {
        boolean readOnly = Minecraft.getInstance().getSingleplayerServer() == null;
        return new HandMadeConfigScreen(parent, buildCategories(), ConfigScreens::saveAll, readOnly);
    }

    // ==================== 分类 ====================

    private static List<ConfigCategorySpec> buildCategories() {
        List<ConfigCategorySpec> categories = new ArrayList<>();

        Config c = Config.INSTANCE;

        categories.add(new ConfigCategorySpec(tr("hand_saw"), List.of(
                bool("hand_saw.enable_tree_felling", true, c.enableTreeFelling),
                integer("hand_saw.windup_ticks", 0, 200, 10, c.handSawWindupTicks),
                integer("hand_saw.cut_ticks", 1, 200, 20, c.handSawCutTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("press_hammer"), List.of(
                integer("press_hammer.charge_ticks", 1, 200, 15, c.pressHammerChargeTicks),
                integer("press_hammer.charge_window_ticks", 1, 200, 20, c.pressHammerChargeWindowTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("bellows"), List.of(
                integer("bellows.charge_ticks", 1, 200, 10, c.bellowsChargeTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("mortar"), List.of(
                integer("mortar.windup_ticks", 0, 200, 20, c.mortarWindupTicks),
                integer("mortar.processing_ticks", 1, 400, 100, c.mortarProcessingTicks),
                integer("mortar.pound_cycle_ticks", 1, 100, 10, c.mortarPoundCycleTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("crusher_mortar"), List.of(
                integer("crusher_mortar.windup_ticks", 0, 200, 20, c.crusherMortarWindupTicks),
                integer("crusher_mortar.processing_ticks", 1, 400, 100, c.crusherMortarProcessingTicks),
                integer("crusher_mortar.pound_cycle_ticks", 1, 100, 10, c.crusherMortarPoundCycleTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("stirring_staff"), List.of(
                integer("stirring_staff.windup_ticks", 0, 200, 8, c.stirringStaffWindupTicks),
                integer("stirring_staff.processing_ticks", 1, 400, 60, c.stirringStaffProcessingTicks)
        )));

        categories.add(new ConfigCategorySpec(tr("guidebook"), List.of(
                bool("guidebook.give_on_first_join", true, c.giveGuideBookOnFirstJoin)
        )));

        categories.add(new ConfigCategorySpec(tr("debug"), List.of(
                bool("debug.enable_debug", false, c.debugEnabled)
        )));

        ConfigSectionProvider maid = maidProvider();
        if (maid != null) {
            categories.addAll(maid.categories());
        }
        return categories;
    }

    private static Component tr(String path) {
        return Component.translatable(CAT + path);
    }

    // ==================== 选项构造 ====================

    private static ConfigOption.Bool bool(String path, boolean defaultValue,
                                          net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        return new ConfigOption.Bool(label(path), tooltip(path),
                defaultValue, value::get, value::set);
    }

    private static ConfigOption.Int integer(String path, int min, int max, int defaultValue,
                                            net.minecraftforge.common.ForgeConfigSpec.IntValue value) {
        return new ConfigOption.Int(label(path), tooltip(path),
                min, max, defaultValue, value::get, value::set);
    }

    private static Component label(String path) {
        return Component.translatable(P + path);
    }

    /** 用 {@code path.tooltip}（单行）或 {@code path.tooltip.0/1/2…}（多行）。 */
    private static List<Component> tooltip(String path) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(P + path + ".tooltip.0"));
        lines.add(Component.translatable(P + path + ".tooltip.1"));
        lines.add(Component.translatable(P + path + ".tooltip.2"));
        // 多行键不存在时 translatable 会原样显示键名——这里用「翻译结果等于键名」来剔除
        lines.removeIf(line -> line.getString().equals(P + path + ".tooltip.1")
                || line.getString().equals(P + path + ".tooltip.2")
                || line.getString().equals(P + path + ".tooltip.0"));
        if (lines.isEmpty()) {
            lines.add(Component.translatable(P + path + ".tooltip"));
        }
        return lines;
    }

    // ==================== 可选模组的页 ====================

    /**
     * 通过反射找「女仆配置页」的提供方。
     *
     * <p>为什么反射：{@code src/tlmCompat} 是独立 sourceSet（依赖方向 tlmCompat → main），
     * 主代码引用不到 {@code com.colin101.maidfactory.*}，这些类也只在装了 TLM 时存在。</p>
     */
    private static ConfigSectionProvider maidProvider() {
        if (!ModList.get().isLoaded("touhou_little_maid")) {
            return null;
        }
        try {
            Class<?> provider = Class.forName(
                    "com.colin101.maidfactory.client.MaidConfigSectionProvider");
            return (ConfigSectionProvider) provider.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            CreateHandMade.LOGGER.warn("[HandMade] 女仆配置页加载失败，配置界面只显示主配置", t);
            return null;
        }
    }

    // ==================== 保存 ====================

    /**
     * 落盘 + 广播。
     *
     * <p>改值的过程已经写进 {@code ForgeConfigSpec} 的内存值，这里只负责写回 toml；
     * 广播丢回服务端线程执行，因为单人游戏里「客户端线程」并不等于「集成服务端线程」。</p>
     */
    private static void saveAll() {
        try {
            Config.SPEC.save();
        } catch (Throwable t) {
            CreateHandMade.LOGGER.error("[HandMade] 保存主配置失败", t);
        }

        ConfigSectionProvider maid = maidProvider();
        if (maid != null) {
            try {
                maid.save();
            } catch (Throwable t) {
                CreateHandMade.LOGGER.error("[HandMade] 保存女仆配置失败", t);
            }
        }

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(ModNetwork::broadcastConfigSync);
        }
    }
}
