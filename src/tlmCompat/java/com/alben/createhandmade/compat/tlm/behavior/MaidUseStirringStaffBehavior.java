package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.StirringStaffItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 女仆搅拌杖行为：读液体模式的输出坐标。
 *
 *   主手搅拌杖 + 输出是 Basin + 有匹配配方
 *   → BasinRecipe.apply 触发一次搅拌
 *   → 扣耐久
 *
 * 每 20 tick 触发一次。
 */
public class MaidUseStirringStaffBehavior implements BehaviorControl<EntityMaid> {

    private static final int CHECK_INTERVAL = 20;

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseStirringStaffBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }
        cooldown = CHECK_INTERVAL;

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof StirringStaffItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 读液体模式的输出坐标
        BlockPos outputPos = PointerDataHelper.getLiquidOutput(maid, level);
        if (outputPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 交互距离校验
        if (!MaidRangeHelper.isWithinRange(maid, outputPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockEntity be = level.getBlockEntity(outputPos);
        if (!(be instanceof BasinBlockEntity basin)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (basin.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        Recipe<?> recipe = StirringStaffItem.findMatchingRecipe(level, basin);
        if (recipe == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!BasinRecipe.apply(basin, recipe)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        basin.notifyChangeOfContents();

        // 扣耐久
        tool.hurtAndBreak(1, maid, e -> {});

        cooldown = CHECK_INTERVAL;
        status = Behavior.Status.RUNNING;
        return true;
    }

    @Override
    public void tickOrStop(ServerLevel level, EntityMaid maid, long gameTime) {
        doStop(level, maid, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, EntityMaid maid, long gameTime) {
        status = Behavior.Status.STOPPED;
    }
}