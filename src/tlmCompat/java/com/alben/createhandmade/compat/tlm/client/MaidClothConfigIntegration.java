package com.alben.createhandmade.compat.tlm.client;

import com.alben.createhandmade.compat.tlm.MaidConfig;
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

    public static void addMaidCategory(ConfigBuilder builder, ConfigEntryBuilder entry) {
        ConfigCategory maid = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.maid"));

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
                        MaidConfig.INSTANCE.maidPressHammerCooldown.get(),
                        1, 200)
                .setDefaultValue(20)
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
                .setDefaultValue(20)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.infusion_gun_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidInfusionGunCooldown::set)
                .build());

        // ---- 手锯冷却 ----
        maid.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.maid.hand_saw_cooldown"),
                        MaidConfig.INSTANCE.maidHandSawCooldown.get(),
                        1, 400)
                .setDefaultValue(40)
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.hand_saw_cooldown.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.maidHandSawCooldown::set)
                .build());

        // ---- 屏蔽模组列表（List<? extends String> → List<String>）----
        List<String> blocked = new ArrayList<>(MaidConfig.INSTANCE.blockedMods.get());

        maid.addEntry(entry
                .startStrList(
                        Component.translatable("config.create_hand_made.maid.blocked_mods"),
                        blocked)
                .setDefaultValue(List.of("maidassemblyline"))
                .setTooltip(Component.translatable(
                        "config.create_hand_made.maid.blocked_mods.tooltip"))
                .setSaveConsumer(MaidConfig.INSTANCE.blockedMods::set)
                .build());
    }
}