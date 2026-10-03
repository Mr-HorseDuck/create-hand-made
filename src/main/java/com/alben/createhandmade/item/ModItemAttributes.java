package com.alben.createhandmade.item;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModItemAttributes {

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