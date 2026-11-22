package com.colin101.maidfactory.client;

import com.alben.createhandmade.client.config.ConfigCategorySpec;
import com.alben.createhandmade.client.config.ConfigOption;
import com.alben.createhandmade.client.config.ConfigSectionProvider;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidStartupConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆配置的「配置界面数据源」：给自绘界面交出 3 页可调项，<b>不画任何东西</b>。
 *
 * <p>放在 tlmCompat sourceSet（依赖方向 tlmCompat → main），主代码用
 * {@code Class.forName("com.colin101.maidfactory.client.MaidConfigSectionProvider")}
 * + {@link ConfigSectionProvider} 接口拿到它，所以主代码不需要引用女仆配置类，
 * 没装 TLM 时也不会 NoClassDefFoundError。</p>
 *
 * <p>为什么拆成 4 页：自绘界面右侧<b>不滚动</b>（见 {@code HandMadeConfigScreen}），
 * 一页按 4 项左右最稳（窗口矮的时候 7 行会顶到「完成」按钮上），而女仆配置有 12 项。</p>
 *
 * <p>0.7.0 起本类取代了原来的 {@code MaidClothConfigIntegration}——模组不再依赖 Cloth Config。</p>
 */
public class MaidConfigSectionProvider implements ConfigSectionProvider {

    private static final String P = "config.create_hand_made.";
    private static final String CAT = P + "category.";

    @Override
    public List<ConfigCategorySpec> categories() {
        List<ConfigCategorySpec> categories = new ArrayList<>(3);
        categories.add(basics());
        categories.add(cooldownsOne());
        categories.add(cooldownsTwo());
        categories.add(blockedMods());
        return categories;
    }

    // ==================== 基础 ====================

    private ConfigCategorySpec basics() {
        // 搜索半径在配置里是 Double，界面按整数调（格），读写时转换
        ConfigOption.Int searchRadius = new ConfigOption.Int(
                label("maid.search_radius"), tooltip("maid.search_radius"),
                1, 32, 8,
                () -> MaidConfig.INSTANCE.maidSearchRadius.get().intValue(),
                value -> MaidConfig.INSTANCE.maidSearchRadius.set((double) value));

        ConfigOption.Int particleLevel = new ConfigOption.Int(
                label("maid.particle_level"), tooltip("maid.particle_level"),
                1, 5, 3,
                MaidConfig.INSTANCE.maidParticleLevel::get,
                MaidConfig.INSTANCE.maidParticleLevel::set);

        ConfigOption.Bool passiveInfusionGun = new ConfigOption.Bool(
                label("maid.passive_infusion_gun"), tooltip("maid.passive_infusion_gun"),
                true,
                MaidConfig.INSTANCE.maidPassiveInfusionGun::get,
                MaidConfig.INSTANCE.maidPassiveInfusionGun::set);

        return new ConfigCategorySpec(Component.translatable(CAT + "maid.basics"),
                List.of(searchRadius, passiveInfusionGun, particleLevel));
    }

    // ==================== 冷却 ====================

    private ConfigCategorySpec cooldownsOne() {
        return new ConfigCategorySpec(Component.translatable(CAT + "maid.cooldowns"), List.of(
                cooldown("press_hammer_cooldown", 1, 200, 16, MaidConfig.INSTANCE.maidPressHammerCooldown),
                cooldown("infusion_gun_cooldown", 1, 200, 16, MaidConfig.INSTANCE.maidInfusionGunCooldown),
                cooldown("hand_saw_cooldown", 1, 400, 20, MaidConfig.INSTANCE.maidHandSawCooldown),
                cooldown("bellows_cooldown", 1, 200, 16, MaidConfig.INSTANCE.maidBellowsCooldown)
        ));
    }

    private ConfigCategorySpec cooldownsTwo() {
        return new ConfigCategorySpec(Component.translatable(CAT + "maid.cooldowns_2"), List.of(
                cooldown("pointer_deploy_cooldown", 1, 200, 16, MaidConfig.INSTANCE.maidPointerDeployCooldown),
                cooldown("stirring_staff_cooldown", 1, 200, 16, MaidConfig.INSTANCE.maidStirringStaffCooldown),
                cooldown("mortar_cooldown", 1, 400, 60, MaidConfig.INSTANCE.maidMortarCooldown)
        ));
    }

    private ConfigOption.Int cooldown(String path, int min, int max, int defaultValue,
                                      net.minecraftforge.common.ForgeConfigSpec.IntValue value) {
        return new ConfigOption.Int(label("maid." + path), tooltip("maid." + path),
                min, max, defaultValue, value::get, value::set);
    }

    // ==================== 屏蔽模组 ====================

    private ConfigCategorySpec blockedMods() {
        ConfigOption.Strings blocked = new ConfigOption.Strings(
                label("maid.blocked_mods"),
                List.of(
                        Component.translatable(P + "maid.blocked_mods.tooltip.0"),
                        Component.translatable(P + "maid.blocked_mods.tooltip.1"),
                        Component.translatable(P + "maid.blocked_mods.tooltip.2")),
                List.of("maidassemblyline"),
                () -> new ArrayList<>(MaidStartupConfig.INSTANCE.blockedMods.get()),
                value -> MaidStartupConfig.INSTANCE.blockedMods.set(value));

        return new ConfigCategorySpec(Component.translatable(CAT + "maid.blocked_mods"),
                List.of(blocked));
    }

    // ==================== 落盘 ====================

    /**
     * 两个 SPEC 都要存：{@code MaidConfig} 是 SERVER（存档 serverconfig/），
     * {@code MaidStartupConfig} 是 COMMON（config/，因为启动阶段要读 blocked_mods）。
     */
    @Override
    public void save() {
        MaidConfig.SPEC.save();
        MaidStartupConfig.SPEC.save();
    }

    // ==================== 辅助 ====================

    private static Component label(String path) {
        return Component.translatable(P + path);
    }

    /** 女仆配置的说明都是单行 {@code .tooltip}。 */
    private static List<Component> tooltip(String path) {
        return List.of(Component.translatable(P + path + ".tooltip"));
    }
}
