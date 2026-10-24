package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.recipe.type.HandMadeApplicationRecipe;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 女仆指杆部署行为（默认模式）。
 *
 * <p>工作方块上的物品由玩家自己放，女仆只负责：
 * <ul>
 *   <li>从输入容器取 1 个原料</li>
 *   <li>对 Depot / Belt 上的物品部署（L3 优先，L1 兜底，复用 {@link PointerItem#findRecipe}）</li>
 *   <li>Depot 产物移到输出；Belt 产物留在传送带</li>
 *   <li>部署失败 → 原料退回输入</li>
 * </ul>
 */
public class MaidUsePointerDeployBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUsePointerDeployBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof PointerItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (MaidBatchCraftingBlocker.checkAndRefuse(maid, tool)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos workPos = PointerDataHelper.getWork(maid, level);
        BlockPos inputPos = PointerDataHelper.getInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getOutput(maid, level);

        if (workPos == null || inputPos == null || outputPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!MaidRangeHelper.allWithinRange(maid, workPos, inputPos, outputPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        IItemHandler inputInv = getItemHandler(level, inputPos);
        if (inputInv == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockEntity be = level.getBlockEntity(workPos);

        // ---- Belt：部署后产物留在传送带 ----
        if (!(be instanceof DepotBlockEntity)
                && BlockEntityBehaviour.get(level, workPos,
                TransportedItemStackHandlerBehaviour.TYPE) != null) {
            MaidLookHelper.lookAtBlock(maid, workPos);
            if (deployOnBelt(level, workPos, inputInv, tool, maid)) {
                MaidFeedback.deploySound(level, workPos);
                MaidFeedback.itemParticles(level, workPos, findFirstBeltItem(level, workPos), 6);

                cooldown = MaidConfig.INSTANCE.maidPointerDeployCooldown.get();
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Depot：产物移到输出 ----
        if (!(be instanceof DepotBlockEntity depot)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        IItemHandler outputInv = getItemHandler(level, outputPos);
        if (outputInv == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        MaidLookHelper.lookAtBlock(maid, workPos);

        // 尝试部署
        int inputSlot = findDeployableInputSlot(level, inputInv, held);
        if (inputSlot >= 0) {
            ItemStack extracted = inputInv.extractItem(inputSlot, 1, false);
            if (!extracted.isEmpty()) {
                Recipe<?> recipe = PointerItem.findRecipe(level, held, extracted);
                List<ItemStack> results = applyDeploy(level, held, recipe);

                if (!results.isEmpty()) {
                    boolean allInserted = true;
                    for (ItemStack result : results) {
                        ItemStack remaining = insertAll(outputInv, result);
                        if (!remaining.isEmpty()) {
                            inputInv.insertItem(inputSlot, extracted, false);
                            allInserted = false;
                            break;
                        }
                    }
                    if (allInserted) {
                        // 只减 1 个，不是清空整组
                        ItemStack remainingHeld = held.copy();
                        remainingHeld.shrink(1);
                        if (remainingHeld.isEmpty()) {
                            depot.setHeldItem(ItemStack.EMPTY);
                        } else {
                            depot.setHeldItem(remainingHeld);
                        }
                        depot.notifyUpdate();
                        depot.sendData();
                        returnOrConsumeInput(maid, inputInv, inputSlot, extracted, recipe);
                        tool.hurtAndBreak(1, maid, e -> {});

                        MaidFeedback.deploySound(level, workPos);
                        MaidFeedback.itemParticles(level, workPos, held, 6);

                        cooldown = MaidConfig.INSTANCE.maidPointerDeployCooldown.get();
                        status = Behavior.Status.RUNNING;
                        return true;
                    }
                } else {
                    // 部署失败，原料退回
                    inputInv.insertItem(inputSlot, extracted, false);
                }
            }
        }

        // 没有匹配的部署配方 → 视为产物 → 移到输出
        if (moveToOutput(depot, outputInv)) {
            cooldown = MaidConfig.INSTANCE.maidPointerDeployCooldown.get();
            status = Behavior.Status.RUNNING;
            return true;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    // ================= Belt 部署 =================

    private static boolean deployOnBelt(ServerLevel level, BlockPos pos,
                                        IItemHandler inputInv, ItemStack tool, EntityMaid maid) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            int inputSlot = findDeployableInputSlot(level, inputInv, stack);
            if (inputSlot < 0) return TransportedResult.doNothing();

            ItemStack extracted = inputInv.extractItem(inputSlot, 1, false);
            if (extracted.isEmpty()) return TransportedResult.doNothing();

            Recipe<?> recipe = PointerItem.findRecipe(level, stack, extracted);
            List<ItemStack> results = applyDeploy(level, stack, recipe);

            if (results.isEmpty()) {
                inputInv.insertItem(inputSlot, extracted, false);
                return TransportedResult.doNothing();
            }

            success[0] = true;

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : results) {
                if (result.isEmpty()) continue;
                TransportedItemStack copy = item.copy();
                copy.stack = result;
                outputs.add(copy);
            }

            ItemStack left = stack.copy();
            left.shrink(1);

            if (outputs.isEmpty()) {
                if (left.isEmpty()) return TransportedResult.removeItem();
                TransportedItemStack leftStack = item.copy();
                leftStack.stack = left;
                return TransportedResult.convertTo(leftStack);
            }

            if (left.isEmpty()) {
                return TransportedResult.convertTo(outputs);
            }
            TransportedItemStack leftStack = item.copy();
            leftStack.stack = left;
            return TransportedResult.convertToAndLeaveHeld(outputs, leftStack);
        });

        if (success[0]) {
            tool.hurtAndBreak(1, maid, e -> {});
        }
        return success[0];
    }

    /** 从传送带取第一个非空物品做粒子参考（不消耗）。 */
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

    // ================= 部署应用 =================

    private static List<ItemStack> applyDeploy(ServerLevel level, ItemStack target, @Nullable Recipe<?> recipe) {
        if (recipe == null) return List.of();
        if (!PointerItem.isApplicationRecipe(recipe)) return List.of();

        List<ItemStack> results = RecipeApplier.applyRecipeOn(
                level, target.copyWithCount(1), recipe, true);
        if (results == null || results.isEmpty()) return List.of();

        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack r : results) {
            if (!r.isEmpty()) nonEmpty.add(r);
        }
        return nonEmpty;
    }

    private static void returnOrConsumeInput(LivingEntity maid, IItemHandler inputInv,
                                             int slot, ItemStack extracted, Recipe<?> recipe) {
        boolean keep = false;
        if (recipe instanceof ItemApplicationRecipe iar) {
            keep = iar.shouldKeepHeldItem();
        } else if (recipe instanceof HandMadeApplicationRecipe har) {
            keep = har.shouldKeepHeldItem();
        }

        if (keep) {
            inputInv.insertItem(slot, extracted, false);
            return;
        }

        if (extracted.getMaxDamage() > 0) {
            extracted.hurtAndBreak(1, maid, e -> {});
            inputInv.insertItem(slot, extracted, false);
        }
        // 非耐久物品：已取出，消耗掉
    }

    // ================= 辅助 =================

    private static int findDeployableInputSlot(ServerLevel level, IItemHandler inputInv, ItemStack target) {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack slot = inputInv.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            Recipe<?> recipe = PointerItem.findRecipe(level, target, slot.copyWithCount(1));
            if (PointerItem.isApplicationRecipe(recipe)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean moveToOutput(DepotBlockEntity depot, IItemHandler outputInv) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        ItemStack remaining = insertAll(outputInv, held);
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

    private static ItemStack insertAll(IItemHandler inv, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < inv.getSlots() && !remaining.isEmpty(); i++) {
            remaining = inv.insertItem(i, remaining, false);
        }
        return remaining;
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