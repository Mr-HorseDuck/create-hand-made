package com.alben.createhandmade.client;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public class HandMadeClothConfigIntegration {

    public static Screen create(Screen parent) {
        // SERVER 配置在多人游戏里由服务端权威，客户端只读
        boolean singleplayer = Minecraft.getInstance().getSingleplayerServer() != null;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.create_hand_made.title"));

        if (singleplayer) {
            builder.setSavingRunnable(() -> {
                Config.SPEC.save();
                saveMaidSpec();
            });
        }

        ConfigEntryBuilder entry = builder.entryBuilder();

        addHandSawCategory(builder, entry, singleplayer);
        addPressHammerCategory(builder, entry, singleplayer);
        addBellowsCategory(builder, entry, singleplayer);
        addMortarCategory(builder, entry, singleplayer);
        addCrusherMortarCategory(builder, entry, singleplayer);
        addStirringStaffCategory(builder, entry, singleplayer);
        addDebugCategory(builder, entry, singleplayer);

        // ★ 女仆配置 category（TLM 加载时）
        if (ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class<?> maidIntegration = Class.forName(
                        "com.alben.createhandmade.compat.tlm.client.MaidClothConfigIntegration");
                java.lang.reflect.Method addMaidCategory = maidIntegration.getMethod(
                        "addMaidCategory", ConfigBuilder.class, ConfigEntryBuilder.class);
                addMaidCategory.invoke(null, builder, entry);
            } catch (Throwable t) {
                CreateHandMade.LOGGER.warn(
                        "Failed to add maid config category to Cloth Config screen", t);
            }
        }

        return builder.build();
    }

    // ==================== 手锯 ====================

    private static void addHandSawCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.hand_saw"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startBooleanToggle(
                        Component.translatable("config.create_hand_made.hand_saw.enable_tree_felling"),
                        Config.INSTANCE.enableTreeFelling.get())
                .setDefaultValue(true)
                .setTooltip(
                        Component.translatable("config.create_hand_made.hand_saw.enable_tree_felling.tooltip.0"),
                        Component.translatable("config.create_hand_made.hand_saw.enable_tree_felling.tooltip.1"),
                        Component.translatable("config.create_hand_made.hand_saw.enable_tree_felling.tooltip.2"))
                .setSaveConsumer(Config.INSTANCE.enableTreeFelling::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.hand_saw.windup_ticks"),
                        Config.INSTANCE.handSawWindupTicks.get(),
                        0, 200)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.create_hand_made.hand_saw.windup_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.handSawWindupTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.hand_saw.cut_ticks"),
                        Config.INSTANCE.handSawCutTicks.get(),
                        1, 200)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("config.create_hand_made.hand_saw.cut_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.handSawCutTicks::set)
                .build());
    }

    // ==================== 冲压锤 ====================

    private static void addPressHammerCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.press_hammer"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.press_hammer.charge_ticks"),
                        Config.INSTANCE.pressHammerChargeTicks.get(),
                        1, 200)
                .setDefaultValue(15)
                .setTooltip(Component.translatable("config.create_hand_made.press_hammer.charge_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.pressHammerChargeTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.press_hammer.charge_window_ticks"),
                        Config.INSTANCE.pressHammerChargeWindowTicks.get(),
                        1, 200)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("config.create_hand_made.press_hammer.charge_window_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.pressHammerChargeWindowTicks::set)
                .build());
    }

    // ==================== 风箱 ====================

    private static void addBellowsCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.bellows"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.bellows.charge_ticks"),
                        Config.INSTANCE.bellowsChargeTicks.get(),
                        1, 200)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.create_hand_made.bellows.charge_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.bellowsChargeTicks::set)
                .build());
    }

    // ==================== 研钵 ====================

    private static void addMortarCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.mortar"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.mortar.windup_ticks"),
                        Config.INSTANCE.mortarWindupTicks.get(),
                        0, 200)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("config.create_hand_made.mortar.windup_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.mortarWindupTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.mortar.processing_ticks"),
                        Config.INSTANCE.mortarProcessingTicks.get(),
                        1, 400)
                .setDefaultValue(100)
                .setTooltip(Component.translatable("config.create_hand_made.mortar.processing_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.mortarProcessingTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.mortar.pound_cycle_ticks"),
                        Config.INSTANCE.mortarPoundCycleTicks.get(),
                        1, 100)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.create_hand_made.mortar.pound_cycle_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.mortarPoundCycleTicks::set)
                .build());
    }

    // ==================== 碾钵 ====================

    private static void addCrusherMortarCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.crusher_mortar"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.crusher_mortar.windup_ticks"),
                        Config.INSTANCE.crusherMortarWindupTicks.get(),
                        0, 200)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("config.create_hand_made.crusher_mortar.windup_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.crusherMortarWindupTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.crusher_mortar.processing_ticks"),
                        Config.INSTANCE.crusherMortarProcessingTicks.get(),
                        1, 400)
                .setDefaultValue(100)
                .setTooltip(Component.translatable("config.create_hand_made.crusher_mortar.processing_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.crusherMortarProcessingTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.crusher_mortar.pound_cycle_ticks"),
                        Config.INSTANCE.crusherMortarPoundCycleTicks.get(),
                        1, 100)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.create_hand_made.crusher_mortar.pound_cycle_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.crusherMortarPoundCycleTicks::set)
                .build());
    }

    // ==================== 搅拌杖 ====================

    private static void addStirringStaffCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.stirring_staff"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.stirring_staff.windup_ticks"),
                        Config.INSTANCE.stirringStaffWindupTicks.get(),
                        0, 200)
                .setDefaultValue(8)
                .setTooltip(Component.translatable("config.create_hand_made.stirring_staff.windup_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.stirringStaffWindupTicks::set)
                .build());

        cat.addEntry(entry
                .startIntSlider(
                        Component.translatable("config.create_hand_made.stirring_staff.processing_ticks"),
                        Config.INSTANCE.stirringStaffProcessingTicks.get(),
                        1, 400)
                .setDefaultValue(60)
                .setTooltip(Component.translatable("config.create_hand_made.stirring_staff.processing_ticks.tooltip"))
                .setSaveConsumer(Config.INSTANCE.stirringStaffProcessingTicks::set)
                .build());
    }

    // ==================== 调试 ====================

    private static void addDebugCategory(ConfigBuilder builder, ConfigEntryBuilder entry, boolean singleplayer) {
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.debug"));

        if (!singleplayer) {
            addReadOnlyHint(cat, entry);
            return;
        }

        cat.addEntry(entry
                .startBooleanToggle(
                        Component.translatable("config.create_hand_made.debug.enable_debug"),
                        Config.INSTANCE.debugEnabled.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.create_hand_made.debug.enable_debug.tooltip"))
                .setSaveConsumer(Config.INSTANCE.debugEnabled::set)
                .build());
    }

    // ==================== 辅助 ====================

    private static void addReadOnlyHint(ConfigCategory cat, ConfigEntryBuilder entry) {
        cat.addEntry(entry
                .startTextDescription(
                        Component.translatable("config.create_hand_made.multiplayer_hint"))
                .build());
        cat.addEntry(entry
                .startTextDescription(
                        Component.translatable("config.create_hand_made.server_config_hint"))
                .build());
    }

    /** 通过反射保存女仆配置 SPEC，避免 main 直接依赖 tlmCompat。 */
    private static void saveMaidSpec() {
        if (!ModList.get().isLoaded("touhou_little_maid")) return;
        try {
            Class<?> maidConfig = Class.forName(
                    "com.alben.createhandmade.compat.tlm.MaidConfig");
            Object spec = maidConfig.getField("SPEC").get(null);
            spec.getClass().getMethod("save").invoke(spec);
        } catch (Throwable t) {
            CreateHandMade.LOGGER.warn("Failed to save maid config", t);
        }
    }
}