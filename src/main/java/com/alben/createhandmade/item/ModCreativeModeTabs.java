package com.alben.createhandmade.item;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateHandMade.MODID);

    public static final RegistryObject<CreativeModeTab> CREATE_HAND_MADE_TAB =
            CREATIVE_MODE_TABS.register("create_hand_made_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_hand_made"))
                    .icon(() -> new ItemStack(ModItems.POINTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PRESS_HAMMER.get());
                        output.accept(ModItems.MORTAR.get());
                        output.accept(ModItems.CRUSHER_MORTAR.get());
                        output.accept(ModItems.POINTER.get());
                        output.accept(ModItems.STIRRING_STAFF.get());
                        output.accept(ModItems.INFUSION_GUN.get());
                        output.accept(ModItems.BELLOWS.get());
                        output.accept(ModItems.HAND_SAW.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}