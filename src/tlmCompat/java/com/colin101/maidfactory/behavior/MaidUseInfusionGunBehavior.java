package com.colin101.maidfactory.behavior;

import com.alben.createhandmade.api.CreateHandMadeApi;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidFavorabilityHelper;
import com.colin101.maidfactory.MaidBubbleHelper;
import com.colin101.maidfactory.PointerDataHelper;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class MaidUseInfusionGunBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseInfusionGunBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        // ★ 配置开关：关掉后只保留"指杆指定目标"那条路径（优先级 6 的行为），
        //   服主可用 maid.passive_infusion_gun = false 避免女仆与玩家手搓抢置物台。
        if (!MaidConfig.INSTANCE.maidPassiveInfusionGun.get()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof InfusionGunItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 空枪没有可注的东西，先判掉，别浪费后面那次范围扫描
        InfusionGunContents contents = CreateHandMadeApi.infusionGunContents(tool);
        if (contents.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 玩家用指杆指定过抽/注/过剩目标时，让位给优先级 6 的 MaidUseLiquidTransferBehavior：
        //   那条路径按指定目标干活，本行为是"没有指定时自己找附近的置物台/工作盆"的兜底，
        //   两者同时生效会互相抢动作、甚至注到玩家没指定的容器里。
        if (PointerDataHelper.getLiquidInput(maid, level) != null
                || PointerDataHelper.getLiquidOutput(maid, level) != null
                || PointerDataHelper.getLiquidOverflow(maid, level) != null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 节流：附近找不到目标时不能每 tick 扫一遍 (2r+1)³ 方块。
        //   先把冷却扣上再扫，扫空就等下一个冷却窗口（与 6 号行为的做法一致）。
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }
        cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidInfusionGunCooldown, maid);

        // ★ 批量加工附魔 → 拒绝（灌注枪不在附魔类别，恒为 false，防御性保留）
        if (MaidBatchCraftingBlocker.checkAndRefuse(maid, tool)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        FilterItemStack filter = MaidHelpers.Filter.findFilterStack(maid);

        BlockPos depotPos = findNearbyDepot(level, maid);
        if (depotPos != null) {
            // ★ 注视即将注入的置物台
            MaidHelpers.Look.lookAtBlock(maid, depotPos);
            if (CreateHandMadeApi.injectItems(level, depotPos, maid, tool, filter)) {
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.INFUSION_GUN, tool);
                MaidBubbleHelper.tryShow(maid, ToolType.INFUSION_GUN, tool);
                status = Behavior.Status.RUNNING;
                return true;
            }
        }

        BlockPos basinPos = findNearbyBasin(level, maid);
        if (basinPos != null) {
            // ★ 注视即将注入的工作盆
            MaidHelpers.Look.lookAtBlock(maid, basinPos);
            IFluidHandler handler = CreateHandMadeApi.fluidHandlerAt(level, basinPos);
            if (handler != null && CreateHandMadeApi.storeFluid(level, basinPos, maid, tool)) {
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.INFUSION_GUN, tool);
                MaidBubbleHelper.tryShow(maid, ToolType.INFUSION_GUN, tool);
                status = Behavior.Status.RUNNING;
                return true;
            }
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    @Override
    public void tickOrStop(ServerLevel level, EntityMaid maid, long gameTime) {
        doStop(level, maid, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, EntityMaid maid, long gameTime) {
        status = Behavior.Status.STOPPED;
    }

    private BlockPos findNearbyBasin(ServerLevel level, EntityMaid maid) {
        BlockPos maidPos = ((Entity) maid).blockPosition();
        int r = (int) MaidConfig.INSTANCE.maidSearchRadius.get().doubleValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-r, -r, -r), maidPos.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BasinBlockEntity basin && !basin.isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private BlockPos findNearbyDepot(ServerLevel level, EntityMaid maid) {
        BlockPos maidPos = ((Entity) maid).blockPosition();
        int r = (int) MaidConfig.INSTANCE.maidSearchRadius.get().doubleValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-r, -r, -r), maidPos.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepotBlockEntity depot && !depot.getHeldItem().isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }
}