package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.HandSawItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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
 * 女仆手锯行为。
 *
 * <p>只从指杆标记读坐标，支持两种工作方块：
 * <ul>
 *   <li><b>Depot</b>：完整物流——空则从输入取 1 个原料放上去；有原料则切削；有产物则移到输出。</li>
 *   <li><b>Belt</b>：只切削传送带上已有的物品，不取料、不输出，产物留在传送带。</li>
 * </ul>
 */
public class MaidUseHandSawBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseHandSawBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof HandSawItem)) {
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
                if (fillDepotOne(level, depot, inputInv)) {
                    cooldown = MaidConfig.INSTANCE.maidHandSawCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
                status = Behavior.Status.STOPPED;
                return false;
            }

            List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, held);
            if (!recipes.isEmpty()) {
                if (cutDepotItem(level, depot, outputInv, tool, maid, filter)) {
                    cooldown = MaidConfig.INSTANCE.maidHandSawCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
            } else {
                if (moveToOutput(depot, outputInv)) {
                    cooldown = MaidConfig.INSTANCE.maidHandSawCooldown.get();
                    status = Behavior.Status.RUNNING;
                    return true;
                }
            }

            status = Behavior.Status.STOPPED;
            return false;
        }

        // ---- Belt：只切削，不取料不输出 ----
        if (BlockEntityBehaviour.get(level, workPos,
                TransportedItemStackHandlerBehaviour.TYPE) != null) {
            MaidLookHelper.lookAtBlock(maid, workPos);
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (cutBeltItems(level, workPos, tool, maid, filter)) {
                cooldown = MaidConfig.INSTANCE.maidHandSawCooldown.get();
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    // ================= Belt 切削 =================

    private static boolean cutBeltItems(ServerLevel level, BlockPos pos,
                                        ItemStack saw, EntityMaid maid, FilterItemStack filter) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};
        ItemStack[] particleItem = {ItemStack.EMPTY};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, stack);
            if (recipes.isEmpty()) return TransportedResult.doNothing();

            List<ItemStack> results = applyCut(level, stack, recipes, filter);
            if (results.isEmpty()) return TransportedResult.doNothing();

            success[0] = true;
            particleItem[0] = stack.copy();

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
            saw.hurtAndBreak(1, maid, e -> {});
            MaidFeedback.cutSound(level, pos);
            MaidFeedback.itemParticles(level, pos, particleItem[0], 8);
        }
        return success[0];
    }

    private static List<ItemStack> applyCut(ServerLevel level, ItemStack input,
                                            List<Recipe<?>> recipes, @Nullable FilterItemStack filter) {
        for (Recipe<?> recipe : recipes) {
            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, input.copyWithCount(1), recipe, true);
            if (results == null || results.isEmpty()) continue;

            if (filter != null) {
                boolean pass = false;
                for (ItemStack r : results) {
                    if (!r.isEmpty() && filter.test(level, r)) {
                        pass = true;
                        break;
                    }
                }
                if (!pass) continue;
            }

            List<ItemStack> nonEmpty = new ArrayList<>();
            for (ItemStack r : results) {
                if (!r.isEmpty()) nonEmpty.add(r);
            }
            if (!nonEmpty.isEmpty()) return nonEmpty;
        }
        return List.of();
    }

    // ================= Depot 物流辅助 =================

    private static boolean fillDepotOne(ServerLevel level, DepotBlockEntity depot, IItemHandler inputInv) {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack slot = inputInv.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            ItemStack simulated = inputInv.extractItem(i, 1, true);
            if (simulated.isEmpty()) continue;
            if (HandSawItem.getCuttingRecipes(level, simulated).isEmpty()) continue;

            ItemStack actual = inputInv.extractItem(i, 1, false);
            if (actual.isEmpty()) continue;

            depot.setHeldItem(actual);
            depot.notifyUpdate();
            depot.sendData();
            return true;
        }
        return false;
    }

    private static boolean cutDepotItem(ServerLevel level, DepotBlockEntity depot,
                                         IItemHandler outputInv, ItemStack saw,
                                         EntityMaid maid, FilterItemStack filter) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, held);
        if (recipes.isEmpty()) return false;

        List<ItemStack> results = applyCut(level, held, recipes, filter);
        if (results.isEmpty()) return false;

        // 只减 1 个，不是清空整组
        ItemStack remainingHeld = held.copy();
        remainingHeld.shrink(1);
        if (remainingHeld.isEmpty()) {
            depot.setHeldItem(ItemStack.EMPTY);
        } else {
            depot.setHeldItem(remainingHeld);
        }

        ItemStack leftover = ItemStack.EMPTY;
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            ItemStack remaining = insertAll(outputInv, result);
            if (!remaining.isEmpty()) {
                leftover = remaining;
                break;
            }
        }

        if (!leftover.isEmpty()) {
            ItemStack current = depot.getHeldItem();
            if (current.isEmpty()) {
                depot.setHeldItem(leftover);
            } else {
                depot.notifyUpdate();
                depot.sendData();
                saw.hurtAndBreak(1, maid, e -> {});
                MaidFeedback.cutSound(level, depot.getBlockPos());
                MaidFeedback.itemParticles(level, depot.getBlockPos(), held, 8);
                net.minecraft.world.Containers.dropItemStack(level,
                        depot.getBlockPos().getX(),
                        depot.getBlockPos().getY() + 1,
                        depot.getBlockPos().getZ(),
                        leftover);
                return false;
            }
        }

        depot.notifyUpdate();
        depot.sendData();

        saw.hurtAndBreak(1, maid, e -> {});

        MaidFeedback.cutSound(level, depot.getBlockPos());
        MaidFeedback.itemParticles(level, depot.getBlockPos(), held, 8);

        return leftover.isEmpty();
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