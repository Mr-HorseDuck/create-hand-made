package com.alben.createhandmade.registry;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.enchantment.BatchCrafting;
import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.item.PressHammerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * 除物品（{@link ModItems}）以外的注册入口与注册期事件，四合一。
 *
 * <ul>
 *   <li>{@link Enchantments} —— 批量加工附魔</li>
 *   <li>{@link EnchantmentCategories} —— 该附魔的自定义类别</li>
 *   <li>{@link CreativeTabs} —— 创造模式物品栏</li>
 *   <li>{@link ItemAttributes} —— 工具属性修饰（自动注册的事件订阅者）</li>
 * </ul>
 *
 * <p>物品单独留在 {@link ModItems}：它被 80 多处引用，合并没有收益。</p>
 */
public final class ModRegistries {

    private ModRegistries() {}

    // ==================== 批量加工附魔 ====================

    public static class Enchantments {

        public static final DeferredRegister<Enchantment> ENCHANTMENTS =
                DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CreateHandMade.MODID);

        public static final RegistryObject<Enchantment> BATCH_CRAFTING =
                ENCHANTMENTS.register("batch_crafting", BatchCrafting.Enchant::new);

        public static void register(IEventBus modEventBus) {
            ENCHANTMENTS.register(modEventBus);
        }
    }

    /**
     * 限定「批量加工」只出现在 5 件支持批量处理的工具上：
     * 手锯 / 冲压锤 / 研钵 / 碾钵 / 风箱。指杆 / 搅拌杖 / 灌注枪不在此类别。
     */
    public static final class EnchantmentCategories {

        public static final EnchantmentCategory HAND_MADE_BATCHABLE =
                EnchantmentCategory.create("create_hand_made:batchable", item ->
                        item instanceof HandSawItem
                                || item instanceof PressHammerItem
                                || item instanceof MortarItem
                                || item instanceof CrusherMortarItem
                                || item instanceof BellowsItem
                );

        private EnchantmentCategories() {}
    }

    // ==================== 创造模式物品栏 ====================

    public static class CreativeTabs {

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

    // ==================== 工具属性修饰 ====================

    /** 靠 {@code @Mod.EventBusSubscriber} 自动注册，没有任何地方显式引用。 */
    @Mod.EventBusSubscriber(modid = CreateHandMade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ItemAttributes {

        private static final UUID PRESS_HAMMER_DAMAGE = UUID.fromString("00000001-0000-0000-0000-000000000001");
        private static final UUID PRESS_HAMMER_SPEED  = UUID.fromString("00000001-0000-0000-0000-000000000002");
        private static final UUID POINTER_DAMAGE      = UUID.fromString("00000002-0000-0000-0000-000000000001");
        private static final UUID POINTER_SPEED       = UUID.fromString("00000002-0000-0000-0000-000000000002");
        private static final UUID POINTER_BLOCK_REACH = UUID.fromString("00000002-0000-0000-0000-000000000003");
        private static final UUID POINTER_ENTITY_REACH= UUID.fromString("00000002-0000-0000-0000-000000000004");
        private static final UUID STAFF_DAMAGE        = UUID.fromString("00000003-0000-0000-0000-000000000001");
        private static final UUID STAFF_SPEED         = UUID.fromString("00000003-0000-0000-0000-000000000002");
        private static final UUID STAFF_REACH         = UUID.fromString("00000003-0000-0000-0000-000000000003");
        private static final UUID SAW_DAMAGE          = UUID.fromString("00000004-0000-0000-0000-000000000001");
        private static final UUID SAW_SPEED           = UUID.fromString("00000004-0000-0000-0000-000000000002");

        @SubscribeEvent
        public static void onAttributeModifier(ItemAttributeModifierEvent event) {
            // ★ 只对主手和副手生效，跳过护甲槽位（头盔/胸甲/护腿/靴子）
            EquipmentSlot slot = event.getSlotType();
            if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;

            var item = event.getItemStack().getItem();

            if (item == ModItems.PRESS_HAMMER.get()) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(PRESS_HAMMER_DAMAGE, "press_hammer_damage",
                                8.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(Attributes.ATTACK_SPEED,
                        new AttributeModifier(PRESS_HAMMER_SPEED, "press_hammer_speed",
                                -3.1, AttributeModifier.Operation.ADDITION));

            } else if (item == ModItems.POINTER.get()) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(POINTER_DAMAGE, "pointer_damage",
                                3.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(Attributes.ATTACK_SPEED,
                        new AttributeModifier(POINTER_SPEED, "pointer_speed",
                                -1.8, AttributeModifier.Operation.ADDITION));
                event.addModifier(ForgeMod.BLOCK_REACH.get(),
                        new AttributeModifier(POINTER_BLOCK_REACH, "pointer_block_reach",
                                2.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(ForgeMod.ENTITY_REACH.get(),
                        new AttributeModifier(POINTER_ENTITY_REACH, "pointer_entity_reach",
                                2.0, AttributeModifier.Operation.ADDITION));

            } else if (item == ModItems.STIRRING_STAFF.get()) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(STAFF_DAMAGE, "stirring_staff_damage",
                                5.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(Attributes.ATTACK_SPEED,
                        new AttributeModifier(STAFF_SPEED, "stirring_staff_speed",
                                -2.5, AttributeModifier.Operation.ADDITION));
                event.addModifier(ForgeMod.ENTITY_REACH.get(),
                        new AttributeModifier(STAFF_REACH, "stirring_staff_reach",
                                1.0, AttributeModifier.Operation.ADDITION));

            } else if (item == ModItems.HAND_SAW.get()) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(SAW_DAMAGE, "hand_saw_damage",
                                7.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SAW_SPEED, "hand_saw_speed",
                                -3.1, AttributeModifier.Operation.ADDITION));
            }
        }
    }
}
