package com.alben.createhandmade.client;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public class HandMadeClothConfigIntegration {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.create_hand_made.title"))
                .setSavingRunnable(() -> {
                    // 保存 main 配置
                    Config.SPEC.save();
                    // 保存女仆配置（如果 TLM 加载了）
                    saveMaidSpec();
                });

        ConfigEntryBuilder entry = builder.entryBuilder();

        // ---------------- 手锯 ----------------
        ConfigCategory handSaw = builder.getOrCreateCategory(
                Component.translatable("config.create_hand_made.category.hand_saw"));

        handSaw.addEntry(entry
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