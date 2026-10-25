package com.alben.createhandmade.item;

import com.alben.createhandmade.ModRegistration;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;

import java.util.List;
import java.util.function.Function;

/**
 * 物品注册（Fabric：{@code BuiltInRegistries.ITEM} + {@code Registry.register}）。
 */
public final class ModItems {

    public static final PressHammerItem PRESS_HAMMER = register("press_hammer", PressHammerItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(256)
                    .enchantable(15)
                    .component(DataComponents.TOOL, new Tool(List.of(), 1, 1, false))
                    .component(DataComponents.WEAPON, new Weapon(1))
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(ModRegistration.id("press_hammer_damage"),
                                            8.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(ModRegistration.id("press_hammer_speed"),
                                            -3.1, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build()));

    public static final MortarItem MORTAR = register("mortar", MortarItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(256)
                    .enchantable(15));

    public static final CrusherMortarItem CRUSHER_MORTAR = register("crusher_mortar", CrusherMortarItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(512)
                    .enchantable(15));

    public static final PointerItem POINTER = register("pointer", PointerItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(512)
                    .enchantable(15)
                    .component(DataComponents.TOOL, new Tool(List.of(), 1, 1, false))
                    .component(DataComponents.WEAPON, new Weapon(1))
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(ModRegistration.id("pointer_damage"),
                                            3.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(ModRegistration.id("pointer_speed"),
                                            -1.8, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.BLOCK_INTERACTION_RANGE,
                                    new AttributeModifier(ModRegistration.id("pointer_block_reach"),
                                            2.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ENTITY_INTERACTION_RANGE,
                                    new AttributeModifier(ModRegistration.id("pointer_entity_reach"),
                                            2.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build()));

    public static final StirringStaffItem STIRRING_STAFF = register("stirring_staff", StirringStaffItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(256)
                    .enchantable(15)
                    .component(DataComponents.TOOL, new Tool(List.of(), 1, 1, false))
                    .component(DataComponents.WEAPON, new Weapon(1))
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(ModRegistration.id("stirring_staff_damage"),
                                            5.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(ModRegistration.id("stirring_staff_speed"),
                                            -2.5, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ENTITY_INTERACTION_RANGE,
                                    new AttributeModifier(ModRegistration.id("stirring_staff_reach"),
                                            1.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build()));

    public static final BellowsItem BELLOWS = register("bellows", BellowsItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(256)
                    .enchantable(15));

    public static final InfusionGunItem INFUSION_GUN = register("infusion_gun", InfusionGunItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(256)
                    .enchantable(15));

    public static final HandSawItem HAND_SAW = register("hand_saw", HandSawItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(512)
                    .enchantable(15)
                    .component(DataComponents.WEAPON, new Weapon(1))
                    .delayedComponent(DataComponents.TOOL, provider -> new Tool(
                            List.of(Tool.Rule.minesAndDrops(
                                    provider.lookupOrThrow(Registries.BLOCK).getOrThrow(BlockTags.MINEABLE_WITH_AXE),
                                    6.0f)),
                            1.0f,
                            1,
                            false))
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(ModRegistration.id("hand_saw_damage"),
                                            7.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(ModRegistration.id("hand_saw_speed"),
                                            -3.1, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build()));

    private ModItems() {
    }

    public static void register() {
        // 静态字段初始化即完成注册
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
                                               Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModRegistration.id(name));
        properties.setId(key);
        T item = factory.apply(properties);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
