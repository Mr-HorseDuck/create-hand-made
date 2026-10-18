package com.alben.createhandmade.registry;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.enchantment.BatchCraftingEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CreateHandMade.MODID);

    public static final RegistryObject<Enchantment> BATCH_CRAFTING =
            ENCHANTMENTS.register("batch_crafting", BatchCraftingEnchantment::new);

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}