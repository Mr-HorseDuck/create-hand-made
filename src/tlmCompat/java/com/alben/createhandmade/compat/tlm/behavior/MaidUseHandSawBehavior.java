package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.HandSawItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.List;

/**
 * 女仆按指杆标记进行手锯流转（置物台 + 手锯）：
 *   置物台空 → 从输入容器补 1 个原料
 *   置物台有原料 → 手锯切削，产物入输出容器
 *   置物台有产物 → 移入输出容器
 *
 * 单次最多处理 1 个，冷却由 MaidConfig.maidHandSawCooldown 控制。
 * ★ 结构与 MaidUsePointerBehavior 完全一致，只是把冲压换成切削。
 * ★ 产物计算走 MaidCutHelper（tlmCompat 内部），main 侧 HandSawItem 无改动。
 */
public class MaidUseHandSawBehavior implements BehaviorControl<EntityMaid> {

    private static final int MAX_OPERATIONS_PER_ROUND = 1;

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
        // ★ 从配置读冷却
        cooldown = MaidConfig.INSTANCE.maidHandSawCooldown.get();

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof HandSawItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 读指杆标记
        BlockPos workPos = PointerDataHelper.getWork(maid, level);
        BlockPos inputPos = PointerDataHelper.getInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getOutput(maid, level);

        if (workPos == null || inputPos == null || outputPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 交互距离校验
        if (!MaidRangeHelper.allWithinRange(maid, workPos, inputPos, outputPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 工作方块必须是置物台
        BlockEntity be = level.getBlockEntity(workPos);
        if (!(be instanceof DepotBlockEntity depot)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 输入输出容器
        IItemHandler inputInv = getItemHandler(level, inputPos);
        IItemHandler outputInv = getItemHandler(level, outputPos);
        if (inputInv == null || outputInv == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

        // 一轮内循环：补 1 → 切削 1 → 移出 1
        for (int n = 0; n < MAX_OPERATIONS_PER_ROUND; n++) {
            ItemStack held = depot.getHeldItem();

            if (held.isEmpty()) {
                if (!fillDepotOne(level, depot, inputInv)) break;
                continue;
            }

            List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, held);
            if (recipes.isEmpty()) {
                // 无切削配方 → 视为产物 → 移出
                if (!moveToOutput(depot, outputInv)) break;
                continue;
            }

            // 有配方 → 切削，产物入输出容器
            if (!cutDepotItem(level, depot, outputInv, tool, maid, filter)) break;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    /**
     * 从输入容器取 1 个"有切削配方"的物品放入置物台。
     * ★ 只取 1 个，避免剩余原料卡住。
     */
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

    /**
     * 切削置物台上的物品，产物放入输出容器。
     * ★ 产物计算走 MaidCutHelper，main 侧无改动。
     */
    private static boolean cutDepotItem(ServerLevel level, DepotBlockEntity depot,
                                         IItemHandler outputInv, ItemStack saw,
                                         EntityMaid maid, FilterItemStack filter) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        List<ItemStack> results = MaidCutHelper.computeCutResults(level, saw, held, filter);
        if (results.isEmpty()) return false;

        // 清空 depot，产物逐个插入输出容器
        depot.setHeldItem(ItemStack.EMPTY);

        ItemStack leftover = ItemStack.EMPTY;
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            ItemStack remaining = insertAll(outputInv, result, false);
            if (!remaining.isEmpty()) {
                leftover = remaining;
                break;
            }
        }

        if (!leftover.isEmpty()) {
            depot.setHeldItem(leftover);
        }
        depot.notifyUpdate();
        depot.sendData();

        // 切削成功，扣耐久
        saw.hurtAndBreak(1, maid, e -> {});

        level.playSound(null, depot.getBlockPos(), SoundEvents.WOOD_BREAK,
                SoundSource.BLOCKS, 0.7f, 1.2f);

        return leftover.isEmpty();
    }

    /**
     * 依次尝试所有槽，返回未被插入的部分。
     */
    private static ItemStack insertAll(IItemHandler inv, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < inv.getSlots() && !remaining.isEmpty(); i++) {
            remaining = inv.insertItem(i, remaining, simulate);
        }
        return remaining;
    }

    /**
     * 把置物台上的物品移入输出容器。
     */
    private static boolean moveToOutput(DepotBlockEntity depot, IItemHandler outputInv) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        ItemStack remaining = insertAll(outputInv, held, false);

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