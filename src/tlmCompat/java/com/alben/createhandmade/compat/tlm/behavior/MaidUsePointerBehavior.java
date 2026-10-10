package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.PressHammerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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

/**
 * 女仆按指杆标记进行物流流转（置物台 + 冲压锤）：
 *   置物台空 → 从输入容器补 1 个原料
 *   置物台有原料 → 冲压
 *   置物台有产物 → 移入输出容器
 *
 * 单次最多处理 1 个，冷却由 MaidConfig.maidPressHammerCooldown 控制。
 */
public class MaidUsePointerBehavior implements BehaviorControl<EntityMaid> {

    private static final int MAX_OPERATIONS_PER_ROUND = 1;

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUsePointerBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }
        // ★ 从配置读冷却，而不是硬编码
        cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof PressHammerItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 读标记
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

        // 一轮内循环处理：补 1 → 冲压 1 → 移出 1
        for (int n = 0; n < MAX_OPERATIONS_PER_ROUND; n++) {
            ItemStack held = depot.getHeldItem();

            if (held.isEmpty()) {
                // 补 1 个原料
                if (!fillDepotOne(level, depot, inputInv)) break;
                continue;
            }

            Recipe<?> recipe = PressHammerItem.findPressingRecipe(level, held);
            if (recipe == null) {
                // 无配方 → 视为产物 → 移出
                if (!moveToOutput(depot, outputInv)) break;
                continue;
            }

            // 有配方 → 冲压（此时 held 只有 1 个）
            if (!PressHammerItem.tryPressTransported(level, workPos, filter)) break;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    /**
     * 从输入容器取 1 个可冲压物品放入置物台。
     * ★ 只取 1 个，避免 convertToAndLeaveHeld 导致剩余原料卡住。
     */
    private static boolean fillDepotOne(ServerLevel level, DepotBlockEntity depot, IItemHandler inputInv) {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack slot = inputInv.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            ItemStack simulated = inputInv.extractItem(i, 1, true);
            if (simulated.isEmpty()) continue;

            Recipe<?> recipe = PressHammerItem.findPressingRecipe(level, simulated);
            if (recipe == null) continue;

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
     * 把置物台上的物品移入输出容器。
     * @return 成功移出返回 true；输出容器满则返回 false
     */
    private static boolean moveToOutput(DepotBlockEntity depot, IItemHandler outputInv) {
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        ItemStack remaining = held.copy();
        for (int i = 0; i < outputInv.getSlots(); i++) {
            remaining = outputInv.insertItem(i, remaining, false);
            if (remaining.isEmpty()) break;
        }

        if (!remaining.isEmpty()) {
            // 输出容器满，放回置物台
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