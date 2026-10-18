package com.alben.createhandmade.registry;

import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.item.PressHammerItem;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 本模组的自定义附魔类别。
 *
 * 限定「批量加工」只出现在 5 件支持批量处理的工具上：
 * 手锯 / 冲压锤 / 研钵 / 碾钵 / 风箱。
 *
 * 指杆 / 搅拌杖 / 灌注枪不在此类别，所以它们不会拿到这个附魔。
 */
public final class ModEnchantmentCategories {

    public static final EnchantmentCategory HAND_MADE_BATCHABLE =
            EnchantmentCategory.create("create_hand_made:batchable", item ->
                    item instanceof HandSawItem
                            || item instanceof PressHammerItem
                            || item instanceof MortarItem
                            || item instanceof CrusherMortarItem
                            || item instanceof BellowsItem
            );

    private ModEnchantmentCategories() {}
}