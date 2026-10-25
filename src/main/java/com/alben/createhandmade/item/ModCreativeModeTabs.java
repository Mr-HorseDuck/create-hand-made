package com.alben.createhandmade.item;

import com.alben.createhandmade.ModRegistration;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** 创造模式物品栏标签页。 */
public final class ModCreativeModeTabs {

    public static final ResourceKey<CreativeModeTab> CREATE_HAND_MADE_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModRegistration.id("create_hand_made_tab"));

    public static final CreativeModeTab CREATE_HAND_MADE_TAB = CreativeModeTab
            .builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.create_hand_made"))
            .icon(() -> new ItemStack(ModItems.POINTER))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.PRESS_HAMMER);
                output.accept(ModItems.MORTAR);
                output.accept(ModItems.CRUSHER_MORTAR);
                output.accept(ModItems.POINTER);
                output.accept(ModItems.STIRRING_STAFF);
                output.accept(ModItems.INFUSION_GUN);
                output.accept(ModItems.BELLOWS);
                output.accept(ModItems.HAND_SAW);
            })
            .build();

    private ModCreativeModeTabs() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATE_HAND_MADE_TAB_KEY, CREATE_HAND_MADE_TAB);
    }
}
