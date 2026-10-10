package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * 指杆标记的距离校验。
 * ★ 指杆标记本身是"指哪打哪"，用 maidSearchRadius 限制最大作用距离。
 */
public final class MaidRangeHelper {

    private MaidRangeHelper() {}

    /**
     * 目标坐标是否在女仆的搜索半径内。
     * null 不校验（视为通过）。
     */
    public static boolean isWithinRange(EntityMaid maid, BlockPos pos) {
        if (pos == null) return true;
        int r = MaidConfig.INSTANCE.maidSearchRadius.get().intValue();
        double rSq = (double) r * r;
        return ((Entity) maid).blockPosition().distSqr(pos) <= rSq;
    }

    /**
     * 三个坐标都在范围内才返回 true。
     */
    public static boolean allWithinRange(EntityMaid maid, BlockPos a, BlockPos b, BlockPos c) {
        return isWithinRange(maid, a)
                && isWithinRange(maid, b)
                && isWithinRange(maid, c);
    }
}