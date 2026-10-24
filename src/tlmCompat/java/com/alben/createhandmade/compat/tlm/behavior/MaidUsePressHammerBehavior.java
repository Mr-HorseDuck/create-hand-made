package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.PressHammerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
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
 * 女仆冲压锤行为。
 *
 * <p>只从指杆标记读坐标，支持三种工作方块：
 * <ul>
 *   <li><b>Basin</b>：直接冲压，不涉及输入输出容器。</li>
 *   <li><b>Depot</b>：完整物流——空则从输入取 1 个原料放上去；有原料则冲压；有产物则移到输出。</li>
 *   <li><b>Belt</b>：只冲压传送带上已有的物品，不取料、不输出，产物留在传送带。</li>
 * </ul>
 *
 * <p>判断顺序：Basin → Depot → Belt。Depot 也有 {@code TransportedItemStackHandlerBehaviour}，
 * 不先判 Depot 会把 Depot 误判成 Belt。</p>
 */
public class MaidUsePressHammerBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUsePressHammerBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof PressHammerItem)) {
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

        if (!MaidRangeHelper.isWithinRange(maid, workPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockEntity be = level.getBlockEntity(workPos);

        // ---- Basin：直接冲压 ----
        if (be instanceof BasinBlockEntity basin) {
            if (basin.isEmpty()) {
                status = Behavior.Status.STOPPED;
                return false;
            }
            MaidLookHelper.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (PressHammerItem.tryPressBasin(level, workPos, filter)) {
                // 反馈
                ItemStack particleItem = ItemStack.EMPTY;
                for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
                    ItemStack s = basin.inputInventory.getItem(i);
                    if (!s.isEmpty()) { particleItem = s.copy(); break; }
                }
                MaidFeedback.pressSound(level, workPos);
                MaidFeedback.itemParticles(level, workPos, particleItem, 6);

                tool.hurtAndBreak(1, maid, e -> {});
                cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Depot：完整物流 ----
        if (be instanceof DepotBlockEntity depot) {
            BlockPos inputPos = PointerDataHelper.getInput(maid, level);
            BlockPos outputPos = PointerDataHelper.getOutput(maid, level);
            if (inputPos == null || outputPos == null) {
                status = Behavior.Status.STOPPED;
                return false;
            }
            if (!MaidRangeHelper.allWithinRange(maid, workPos, inputPos, outputPos)) {
                status = Behavior.Status.STOPPED;
                return false;
            }

            IItemHandler inputInv = getItemHandler(level, inputPos);
            IItemHandler outputInv = getItemHandler(level, outputPos);
            if (inputInv == null || outputInv == null) {
                status = Behavior.Status.STOPPED;
                return false;
            }

            MaidLookHelper.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

            ItemStack held = depot.getHeldItem();

            if (held.isEmpty()) {
                // 空 → 从输入取 1 个（取料不播反馈）
                if (fillDepotOne(level, depot, inputInv)) {
                    cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
                status = Behavior.Status.STOPPED;
                return false;
            }

            // 有原料 → 冲压
            if (PressHammerItem.findPressingRecipe(level, held) != null) {
                if (PressHammerItem.tryPressTransported(level, workPos, filter)) {
                    // 反馈
                    MaidFeedback.pressSound(level, workPos);
                    MaidFeedback.itemParticles(level, workPos, held, 6);

                    tool.hurtAndBreak(1, maid, e -> {});
                    cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
            } else {
                // 无配方 → 视为产物 → 移到输出（移产物不播反馈）
                if (moveToOutput(depot, outputInv)) {
                    cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
            }

            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Belt：只冲压，不取料不输出 ----
        if (BlockEntityBehaviour.get(level, workPos,
                TransportedItemStackHandlerBehaviour.TYPE) != null) {
            MaidLookHelper.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (PressHammerItem.tryPressTransported(level, workPos, filter)) {
                // 反馈：Belt 上没有现成 item 引用，从 handler 里取第一个非空物品做粒子
                ItemStack particleItem = findFirstBeltItem(level, workPos);
                MaidFeedback.pressSound(level, workPos);
                MaidFeedback.itemParticles(level, workPos, particleItem, 6);

                tool.hurtAndBreak(1, maid, e -> {});
                cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    /** 从传送带上取第一个非空物品做粒子参考（不消耗）。 */
    private static ItemStack findFirstBeltItem(ServerLevel level, BlockPos pos) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return ItemStack.EMPTY;

        ItemStack[] result = {ItemStack.EMPTY};
        handler.handleProcessingOnAllItems(item -> {
            if (result[0].isEmpty() && !item.stack.isEmpty()) {
                result[0] = item.stack.copy();
            }
            return TransportedResult.doNothing();
        });
        return result[0];
    }

    // ================= Depot 物流辅助 =================

    /** 从输入容器取 1 个可冲压物品放到 Depot。 */
    private static boolean fillDepotOne(ServerLevel level, DepotBlockEntity depot, IItemHandler inputInv) {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack slot = inputInv.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            ItemStack simulated = inputInv.extractItem(i, 1, true);
            if (simulated.isEmpty()) continue;
            if (PressHammerItem.findPressingRecipe(level, simulated) == null) continue;

            ItemStack actual = inputInv.extractItem(i, 1, false);
            if (actual.isEmpty()) continue;

            depot.setHeldItem(actual);
            depot.notifyUpdate();
            depot.sendData();
            return true;
        }
        return false;
    }

    /** 把 Depot 上的物品移到输出容器。 */
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