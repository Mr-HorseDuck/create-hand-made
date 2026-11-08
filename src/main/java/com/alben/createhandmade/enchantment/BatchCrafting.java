package com.alben.createhandmade.enchantment;

import com.alben.createhandmade.registry.ModRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 批量加工附魔 + 工具侧辅助，二合一。
 *
 * <p>等级 → 份数：1 级 = 2 份，2 级 = 4 份，3 级 = 8 份，无附魔 = 1 份。</p>
 */
public final class BatchCrafting {

    private BatchCrafting() {}

    /**
     * 读取手持物品上的批量附魔等级，返回本次应处理的最大份数。
     */
    public static int getBatchSize(ItemStack stack) {
        if (stack.isEmpty()) return 1;
        int level = stack.getEnchantmentLevel(ModRegistries.Enchantments.BATCH_CRAFTING.get());
        if (level <= 0) return 1;
        return 1 << level;   // 2^level：1→2, 2→4, 3→8
    }

    /**
     * 在可处理数量限制下取实际份数。
     */
    public static int clampToAvailable(int batchSize, int available) {
        return Math.min(batchSize, available);
    }

    /**
     * 批量加工附魔本体。
     *
     * <p>宝藏附魔：附魔台不出，只能从宝箱 / 交易 / 钓鱼 / 掠夺获得。</p>
     */
    public static class Enchant extends Enchantment {

        public Enchant() {
            super(Rarity.RARE,
                    ModRegistries.EnchantmentCategories.HAND_MADE_BATCHABLE,
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
}
