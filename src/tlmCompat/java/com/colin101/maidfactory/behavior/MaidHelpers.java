package com.colin101.maidfactory.behavior;

import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidDebug;
import com.colin101.maidfactory.PointerDataHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 女仆行为共用的小工具：转头 / 距离校验 / 背包过滤器查找，三合一。
 */
public final class MaidHelpers {

    private MaidHelpers() {}

    /** 让女仆看向某个方块坐标 / 向量。 */
    public static final class Look {

        private static final float YAW_SPEED = 30.0F;
        private static final float PITCH_SPEED = 30.0F;

        private Look() {}

        public static void lookAtBlock(EntityMaid maid, @Nullable BlockPos pos) {
            if (pos == null) return;
            Mob mob = (Mob) maid;
            mob.getLookControl().setLookAt(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    YAW_SPEED, PITCH_SPEED);
        }

        public static void lookAtVec(EntityMaid maid, @Nullable Vec3 vec) {
            if (vec == null) return;
            Mob mob = (Mob) maid;
            mob.getLookControl().setLookAt(
                    vec.x, vec.y, vec.z,
                    YAW_SPEED, PITCH_SPEED);
        }

        public static void lookAtWork(EntityMaid maid, ServerLevel level) {
            lookAtBlock(maid, PointerDataHelper.getWork(maid, level));
        }

        public static void lookAtLiquidOutput(EntityMaid maid, ServerLevel level) {
            lookAtBlock(maid, PointerDataHelper.getLiquidOutput(maid, level));
        }
    }

    /**
     * 指杆标记的距离校验。
     * ★ 指杆标记本身是"指哪打哪"，用 maidSearchRadius 限制最大作用距离。
     */
    public static final class Range {

        private Range() {}

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

    /** 从女仆背包里找 Create 的列表过滤器。 */
    public static final class Filter {

        private Filter() {}

        /**
         * 从女仆背包中查找 Create 列表过滤器并封装成 FilterItemStack
         */
        @Nullable
        public static FilterItemStack findFilterStack(EntityMaid maid) {
            IItemHandler inv = getMaidInventory(maid);
            if (inv == null) return null;

            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slot = inv.getStackInSlot(i);
                if (slot.isEmpty()) continue;

                if (slot.getItem() instanceof FilterItem) {
                    return FilterItemStack.of(slot);
                }
            }
            return null;
        }

        @Nullable
        private static IItemHandler getMaidInventory(EntityMaid maid) {
            // 与 MaidUseBellowsBehavior 同一套候选表：TLM 各版本访问器名字不一致。
            // NoSuchMethodException 属预期，其余反射异常要留痕，别让失败无声无息。
            String[] methodNames = {"getMaidInv", "getMaidInventory", "getInventory"};
            for (String name : methodNames) {
                try {
                    Object result = maid.getClass().getMethod(name).invoke(maid);
                    if (result instanceof IItemHandler handler) {
                        return handler;
                    }
                } catch (NoSuchMethodException ignored) {
                    // 预期：这个版本没有该访问器
                } catch (ReflectiveOperationException ex) {
                    MaidDebug.log("maid inventory accessor '{}' failed", name, ex);
                }
            }
            return null;
        }
    }
}
