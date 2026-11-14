package com.colin101.maidfactory.behavior;

import com.alben.createhandmade.api.CreateHandMadeApi;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.PointerDataHelper;
import com.colin101.maidfactory.MaidBubbleHelper;
import com.colin101.maidfactory.MaidFavorabilityHelper;
import com.alben.createhandmade.recipe.ToolType;
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
import net.minecraftforge.items.IItemHandler;

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

        if (!MaidHelpers.Range.isWithinRange(maid, workPos)) {
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
            MaidHelpers.Look.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidHelpers.Filter.findFilterStack(maid);
            if (CreateHandMadeApi.pressBasin(level, workPos, filter).isSuccess()) {
                // 反馈
                ItemStack particleItem = ItemStack.EMPTY;
                for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
                    ItemStack s = basin.inputInventory.getItem(i);
                    if (!s.isEmpty()) { particleItem = s.copy(); break; }
                }
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.PRESS_HAMMER_BASIN, tool);
                MaidBubbleHelper.tryShow(maid, ToolType.PRESS_HAMMER_BASIN, tool);
                MaidFeedback.pressSound(level, workPos);
                MaidFeedback.itemParticles(level, workPos, particleItem, 6);

                MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidPressHammerCooldown, maid);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Depot：标了输入/输出走完整物流，只标工作方块则纯冲压 ----
        if (be instanceof DepotBlockEntity depot) {
            // ★ 只加工模式：input / output 可选。缺失时只冲压玩家自己放在 Depot 上的物品。
            IItemHandler inputInv = MaidHelpers.Handler.resolve(level, maid, PointerDataHelper.getInput(maid, level));
            IItemHandler outputInv = MaidHelpers.Handler.resolve(level, maid, PointerDataHelper.getOutput(maid, level));
            boolean logistics = inputInv != null && outputInv != null;

            MaidHelpers.Look.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidHelpers.Filter.findFilterStack(maid);

            ItemStack held = depot.getHeldItem();

            if (held.isEmpty()) {
                // 空 → 从输入取 1 个（取料不播反馈）
                if (logistics && fillDepotOne(level, depot, inputInv)) {
                    cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidPressHammerCooldown, maid);
                    status = Behavior.Status.RUNNING;
                    return true;
                }
                status = Behavior.Status.STOPPED;
                return false;
            }

            // 有原料 → 冲压
            if (CreateHandMadeApi.findPressingRecipe(level, held) != null) {
                if (CreateHandMadeApi.pressTransported(level, workPos, filter).isSuccess()) {
                    // 反馈
                    MaidFeedback.pressSound(level, workPos);
                    MaidFeedback.itemParticles(level, workPos, held, 6);

                    MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);
                    // ★ Depot 分支属于冲压锤，不是指杆：以前误传 POINTER，
                    //   既让冲压锤的首次气泡永远不弹，又把指杆的首次标记提前烧掉。
                    MaidBubbleHelper.tryShowFirstUse(maid, ToolType.PRESS_HAMMER_DEPOT, held);
                    MaidBubbleHelper.tryShow(maid, ToolType.PRESS_HAMMER_DEPOT, held);
                    cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidPressHammerCooldown, maid);
                    status = Behavior.Status.RUNNING;
                    return true;
                }
            } else if (logistics) {
                // 无配方 → 视为产物 → 移到输出（移产物不播反馈）
                if (moveToOutput(depot, outputInv)) {
                    cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidPressHammerCooldown, maid);
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
            MaidHelpers.Look.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidHelpers.Filter.findFilterStack(maid);
            if (CreateHandMadeApi.pressTransported(level, workPos, filter).isSuccess()) {
                // 反馈：Belt 上没有现成 item 引用，从 handler 里取第一个非空物品做粒子
                ItemStack particleItem = findFirstBeltItem(level, workPos);
                MaidFeedback.pressSound(level, workPos);
                MaidFeedback.itemParticles(level, workPos, particleItem, 6);

                MaidFavorabilityHelper.damageToolWithLuck(tool, maid, 1);
                MaidBubbleHelper.tryShowFirstUse(maid, ToolType.PRESS_HAMMER_DEPOT, tool);
                MaidBubbleHelper.tryShow(maid, ToolType.PRESS_HAMMER_DEPOT, tool);
                cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidPressHammerCooldown, maid);
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
            if (CreateHandMadeApi.findPressingRecipe(level, simulated) == null) continue;

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

    @Override
    public void tickOrStop(ServerLevel level, EntityMaid maid, long gameTime) {
        doStop(level, maid, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, EntityMaid maid, long gameTime) {
        status = Behavior.Status.STOPPED;
    }
}