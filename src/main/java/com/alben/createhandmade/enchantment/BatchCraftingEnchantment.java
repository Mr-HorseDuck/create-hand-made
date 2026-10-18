package com.alben.createhandmade.enchantment;

import com.alben.createhandmade.registry.ModEnchantmentCategories;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 批量加工附魔。
 *
 * 1 级 = 一次加工 2 份
 * 2 级 = 一次加工 4 份
 * 3 级 = 一次加工 8 份
 *
 * 宝藏附魔：附魔台不出，只能从宝箱 / 交易 / 钓鱼 / 掠夺获得。
 */
public class BatchCraftingEnchantment extends Enchantment {

    public BatchCraftingEnchantment() {
        super(Rarity.RARE,
                ModEnchantmentCategories.HAND_MADE_BATCHABLE,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        // 宝藏附魔，附魔台不显示成本，随便给
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /** 宝藏附魔：附魔台不出现。 */
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    /** 不给村民交易（可选，去掉就是可交易）。 */
    @Override
    public boolean isTradeable() {
        return true;
    }

    /** 允许在宝箱战利品里出现。 */
    @Override
    public boolean isDiscoverable() {
        return true;
    }
}