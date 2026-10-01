package com.alben.createhandmade.item;

import com.tterrag.registrate.util.entry.ItemEntry;

import static com.alben.createhandmade.CreateHandMade.REGISTRATE;

public class ModItems {

    public static final ItemEntry<PressHammerItem> PRESS_HAMMER = REGISTRATE
            .item("press_hammer", PressHammerItem::new)
            .properties(p -> p.stacksTo(1).durability(256))
            .register();

    public static final ItemEntry<MortarItem> MORTAR = REGISTRATE
            .item("mortar", MortarItem::new)
            .properties(p -> p.stacksTo(1).durability(256))
            .register();

    public static final ItemEntry<CrusherMortarItem> CRUSHER_MORTAR = REGISTRATE
            .item("crusher_mortar", CrusherMortarItem::new)
            .properties(p -> p.stacksTo(1).durability(512))
            .register();

    public static final ItemEntry<PointerItem> POINTER = REGISTRATE
            .item("pointer", PointerItem::new)
            .properties(p -> p.stacksTo(1).durability(512))
            .register();

    public static final ItemEntry<StirringStaffItem> STIRRING_STAFF = REGISTRATE
            .item("stirring_staff", StirringStaffItem::new)
            .properties(p -> p.stacksTo(1).durability(256))
            .register();

    public static final ItemEntry<BellowsItem> BELLOWS = REGISTRATE
            .item("bellows", BellowsItem::new)
            .properties(p -> p.stacksTo(1).durability(256))
            .register();

    public static final ItemEntry<InfusionGunItem> INFUSION_GUN = REGISTRATE
            .item("infusion_gun", InfusionGunItem::new)
            .properties(p -> p.stacksTo(1).durability(256))
            .register();

    public static final ItemEntry<HandSawItem> HAND_SAW = REGISTRATE
            .item("hand_saw", HandSawItem::new)
            .properties(p -> p.stacksTo(1).durability(512))
            .register();

    public static void register() {
    }
}