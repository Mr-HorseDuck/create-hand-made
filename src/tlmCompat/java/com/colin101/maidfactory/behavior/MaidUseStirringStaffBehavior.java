package com.colin101.maidfactory.behavior;

import com.alben.createhandmade.api.CreateHandMadeApi;
import com.colin101.maidfactory.MaidBubbleHelper;
import com.colin101.maidfactory.MaidFavorabilityHelper;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.PointerDataHelper;
import com.alben.createhandmade.item.StirringStaffItem;
import com.alben.createhandmade.recipe.ToolType;
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
 * 冷却由 MaidConfig.maidStirringStaffCooldown 控制，默认 20 tick。
 */
public class MaidUseStirringStaffBehavior implements BehaviorControl<EntityMaid> {

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
        cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidStirringStaffCooldown, maid);

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof StirringStaffItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (MaidBatchCraftingBlocker.checkAndRefuse(maid, tool)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos outputPos = PointerDataHelper.getLiquidOutput(maid, level);
        MaidHelpers.Look.lookAtBlock(maid, outputPos);
        if (outputPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!MaidHelpers.Range.isWithinRange(maid, outputPos)) {
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

        Recipe<?> recipe = CreateHandMadeApi.findStirringRecipe(level, basin);
        if (recipe == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!BasinRecipe.apply(basin, recipe)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        basin.notifyChangeOfContents();

        // 反馈
        MaidFeedback.stirSound(level, outputPos);
        MaidFeedback.stirParticles(level, outputPos, 8);

        // 扣耐久
        MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);

        MaidBubbleHelper.tryShowFirstUse(maid, ToolType.STIRRING_STAFF, tool);
        MaidBubbleHelper.tryShow(maid, ToolType.STIRRING_STAFF, tool);

        cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidStirringStaffCooldown, maid);
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