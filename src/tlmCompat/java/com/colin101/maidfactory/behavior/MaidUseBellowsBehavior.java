package com.colin101.maidfactory.behavior;

import com.alben.createhandmade.api.CreateHandMadeApi;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidDebug;
import com.colin101.maidfactory.PointerDataHelper;
import com.colin101.maidfactory.MaidBubbleHelper;
import com.colin101.maidfactory.MaidFavorabilityHelper;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.item.BellowsItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 女仆风箱行为。
 *
 * <p>工作方块支持两种：
 * <ul>
 *   <li><b>Depot</b>：完整流程——空则从输入取 1 个原料放上去；有原料则吹；有产物（吹不动）则移到输出。</li>
 *   <li><b>Belt</b>：只吹，不取料不输出，产物留在传送带上继续流动。</li>
 * </ul>
 *
 * <p>介质从女仆背包读（TLM 女仆没有原版副手槽），只读不取，和玩家侧"介质不消耗"一致。</p>
 */
public class MaidUseBellowsBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseBellowsBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof BellowsItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (MaidBatchCraftingBlocker.checkAndRefuse(maid, tool)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos workPos = PointerDataHelper.getWork(maid, level);
        if (workPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        IItemHandler maidInv = getMaidInventory(maid);
        if (maidInv == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        FanProcessingType type = findMediaType(maidInv);
        if (type == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!MaidHelpers.Range.isWithinRange(maid, workPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockEntity be = level.getBlockEntity(workPos);

        // ---- Belt：只吹，不管输入输出 ----
        if (BlockEntityBehaviour.get(level, workPos,
                TransportedItemStackHandlerBehaviour.TYPE) != null
                && !(be instanceof DepotBlockEntity)) {
            MaidHelpers.Look.lookAtBlock(maid, workPos);
            if (CreateHandMadeApi.applyBellows(level, workPos, type)) {
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.BELLOWS, tool);
                MaidBubbleHelper.tryShow(maid, ToolType.BELLOWS, tool);
                MaidFeedback.bellowsSound(level, workPos);
                MaidFeedback.windParticles(level, workPos, 6);

                MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidBellowsCooldown, maid);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Depot：完整流程 ----
        if (!(be instanceof DepotBlockEntity depot)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos inputPos = PointerDataHelper.getInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getOutput(maid, level);
        if (inputPos == null || outputPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }
        if (!MaidHelpers.Range.allWithinRange(maid, workPos, inputPos, outputPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        IItemHandler inputInv = getItemHandler(level, inputPos);
        IItemHandler outputInv = getItemHandler(level, outputPos);
        if (inputInv == null || outputInv == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        MaidHelpers.Look.lookAtBlock(maid, workPos);

        ItemStack held = depot.getHeldItem();

        if (held.isEmpty()) {
            if (fillDepotOne(level, depot, inputInv, type)) {
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidBellowsCooldown, maid);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (CreateHandMadeApi.bellowsCanProcess(level, held, type)) {
            if (CreateHandMadeApi.applyBellows(level, workPos, type)) {
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.BELLOWS, held);
                MaidBubbleHelper.tryShow(maid, ToolType.BELLOWS, held);
                MaidFeedback.bellowsSound(level, workPos);
                MaidFeedback.windParticles(level, workPos, 6);

                MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidBellowsCooldown, maid);
                status = Behavior.Status.RUNNING;
                return true;
            }
        } else {
            if (moveToOutput(depot, outputInv)) {
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidBellowsCooldown, maid);
                status = Behavior.Status.RUNNING;
                return true;
            }
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    // ================= 辅助方法 =================

    private static boolean fillDepotOne(ServerLevel level, DepotBlockEntity depot,
                                        IItemHandler inputInv, FanProcessingType type) {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack slot = inputInv.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            ItemStack simulated = inputInv.extractItem(i, 1, true);
            if (simulated.isEmpty()) continue;
            if (!BellowsItem.canProcess(level, simulated, type)) continue;

            ItemStack actual = inputInv.extractItem(i, 1, false);
            if (actual.isEmpty()) continue;

            depot.setHeldItem(actual);
            depot.notifyUpdate();
            depot.sendData();
            return true;
        }
        return false;
    }

    private static boolean moveToOutput(DepotBlockEntity depot, IItemHandler outputInv) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        ItemStack remaining = held.copy();
        for (int i = 0; i < outputInv.getSlots(); i++) {
            remaining = outputInv.insertItem(i, remaining, false);
            if (remaining.isEmpty()) break;
        }

        if (!remaining.isEmpty()) {
            depot.setHeldItem(remaining);
            depot.notifyUpdate();
            return false;
        }

        depot.setHeldItem(ItemStack.EMPTY);
        depot.notifyUpdate();
        depot.sendData();
        return true;
    }

    @Nullable
    private static FanProcessingType findMediaType(IItemHandler inv) {
        if (inv == null) return null;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            FanProcessingType type = BellowsMediaRegistry.resolve(slot);
            if (type != null) return type;
        }
        return null;
    }

    @Nullable
    private static IItemHandler getMaidInventory(EntityMaid maid) {
        // TLM 各版本的背包访问器名字不一致，按候选表逐个试。
        // NoSuchMethodException 属预期（换成下一个名字继续），但别的反射异常意味着
        // TLM 改了签名/可见性，必须留痕 —— 否则"女仆找不到背包"是彻底哑掉的失败。
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

    @Nullable
    private static IItemHandler getItemHandler(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        LazyOptional<IItemHandler> opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        return opt.orElse(null);
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