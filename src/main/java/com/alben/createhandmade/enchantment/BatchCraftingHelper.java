package com.alben.createhandmade.enchantment;

import com.alben.createhandmade.registry.ModEnchantments;
import net.minecraft.world.item.ItemStack;

/**
 * 批量加工附魔的工具侧辅助。
 *
 * 1 级 = 2 份；2 级 = 4 份；3 级 = 8 份；无附魔 = 1 份。
 */
public final class BatchCraftingHelper {

    private BatchCraftingHelper() {}

    /**
     * 读取手持物品上的批量附魔等级，返回本次应处理的最大份数。
     */
    public static int getBatchSize(ItemStack stack) {
        if (stack.isEmpty()) return 1;
        int level = stack.getEnchantmentLevel(ModEnchantments.BATCH_CRAFTING.get());
        if (level <= 0) return 1;
        return 1 << level;   // 2^level：1→2, 2→4, 3→8
    }

    /**
     * 在可处理数量限制下取实际份数。
     */
    public static int clampToAvailable(int batchSize, int available) {
        return Math.min(batchSize, available);
    }
}