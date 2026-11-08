package com.colin101.maidfactory.client;

import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidStartupConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆配置的 Cloth Config 界面集成。
 * ★ 位于 tlmCompat sourceSet，由 main 通过反射调用，
 *   避免 main 直接依赖 TLM / 女仆配置类。
 */
public class MaidClothConfigIntegration {

    /**
     * @param singleplayer 是否单人游戏（集成服务端在跑）。
     *                     与主配置各分类一致：非单人环境整页只读，只显示提示文字。
     */
    public static void addMaidCategory(ConfigBuilder builder, ConfigEntryBuilder entry,
                                      boolean singleplayer) {
        ConfigCategory maid = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.maid"));

        // ★ SERVER 配置：多人游戏里客户端改不了（也不该改），与普通配置同样处理
        if (!singleplayer) {
            maid.addEntry(entry
                    .startTextDescription(
                            Component.translatable("config.create_hand_made.multiplayer_hint"))
                    .build());
            maid.addEntry(entry
                    .startTextDescription(
                            Component.translatable("config.create_hand_made.server_config_hint"))
                    .build());
            return;
        }

        // ---- 搜索半径（Double 值，用整数滑块 + 读写转换）----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.search_radius"),
                        MaidConfig.INSTANCE.maidSearchRadius.get().intValue(),
                        1, 32)
                .setDefaultValue(8)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.search_radius.tooltip"))
                .setSaveConsumer(value -> MaidConfig.INSTANCE.maidSearchRadius.set((double) value))
                .build());

        // ---- 冲压锤冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.press_hammer_cooldown"),
                        // ★ 这里以前读的是 maidPointerDeployCooldown：默认值都是 16 所以看不出来，
                        //   但玩家改过冷却后，滑块会显示成"指杆部署"的值。
                        MaidConfig.INSTANCE.maidPressHammerCooldown.get(),
                        1, 200)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.press_hammer_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidPressHammerCooldown::set)
                .build());

        // ---- 灌注枪冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.infusion_gun_cooldown"),
                        MaidConfig.INSTANCE.maidInfusionGunCooldown.get(),
                        1, 200)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.infusion_gun_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidInfusionGunCooldown::set)
                .build());

        // ---- 灌注枪：是否允许就近自动注液 ----
        maid.addEntry(entry
                .startBooleanToggle(
                        Component.translatable(
                                "config.create_hand_made.maid.passive_infusion_gun"),
                        MaidConfig.INSTANCE.maidPassiveInfusionGun.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.passive_infusion_gun.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidPassiveInfusionGun::set)
                .build());

        // ---- 手锯冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.hand_saw_cooldown"),
                        MaidConfig.INSTANCE.maidHandSawCooldown.get(),
                        1, 400)
                .setDefaultValue(20)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.hand_saw_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidHandSawCooldown::set)
                .build());

        // ---- 风箱冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.bellows_cooldown"),
                        MaidConfig.INSTANCE.maidBellowsCooldown.get(),
                        1, 200)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.bellows_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidBellowsCooldown::set)
                .build());

        // ---- 指杆部署冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.pointer_deploy_cooldown"),
                        MaidConfig.INSTANCE.maidPointerDeployCooldown.get(),
                        1, 200)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.pointer_deploy_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidPointerDeployCooldown::set)
                .build());

        // ---- 搅拌杖冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.stirring_staff_cooldown"),
                        MaidConfig.INSTANCE.maidStirringStaffCooldown.get(),
                        1, 200)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.stirring_staff_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidStirringStaffCooldown::set)
                .build());

        // ---- 研钵/碾钵冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.mortar_cooldown"),
                        MaidConfig.INSTANCE.maidMortarCooldown.get(),
                        1, 400)
                .setDefaultValue(60)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.mortar_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidMortarCooldown::set)
                .build());

        // ---- 女仆粒子----
                maid.addEntry(entry
        .startIntSlider(
                Component.translatable("config.create_hand_made.maid.particle_level"),
                MaidConfig.INSTANCE.maidParticleLevel.get(),
                1, 5)
        .setDefaultValue(3)
        .setTooltip(Component.translatable(
                "config.create_hand_made.maid.particle_level.tooltip"))
        .setSaveConsumer(MaidConfig.INSTANCE.maidParticleLevel::set)
        .build());

        // ---- 屏蔽模组列表（在 COMMON 的 MaidStartupConfig 里，改动需重启）----
        List<String> blocked = new ArrayList<>(MaidStartupConfig.INSTANCE.blockedMods.get());

        maid.addEntry(entry
                .startStrList(
                        Component.translatable("config.create_hand_made.maid.blocked_mods"),
                        blocked)
                .setDefaultValue(List.of("maidassemblyline"))
                .setTooltip(
                        Component.translatable("config.create_hand_made.maid.blocked_mods.tooltip.0"),
                        Component.translatable("config.create_hand_made.maid.blocked_mods.tooltip.1"),
                        Component.translatable("config.create_hand_made.maid.blocked_mods.tooltip.2"))
                .setSaveConsumer(MaidStartupConfig.INSTANCE.blockedMods::set)
                .build());
    }
}