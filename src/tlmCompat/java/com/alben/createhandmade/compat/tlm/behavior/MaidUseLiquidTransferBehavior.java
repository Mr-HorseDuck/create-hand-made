package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 女仆液体搬运（指杆液体模式 MODE_LIQUID）。
 *
 * <p>标记三处：输入盆、输出（Basin / Depot / Belt）、过剩输出盆。</p>
 *
 * <p>流程：
 * <ul>
 *   <li>枪空 → 从输入盆抽液到枪内</li>
 *   <li>枪有液 + 输出为 Basin → 注入输出盆</li>
 *   <li>枪有液 + 输出为 Depot → 对 Depot 上的物品走 Filling 配方，产物进输出容器</li>
 *   <li>枪有液 + 输出为 Belt → 对传送带上的物品走 Filling 配方，产物留传送带</li>
 *   <li>输出塞不下 → 注入过剩输出盆</li>
 *   <li>过剩也塞不下 → 停止</li>
 * </ul>
 */
public class MaidUseLiquidTransferBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseLiquidTransferBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }
        cooldown = MaidConfig.INSTANCE.maidInfusionGunCooldown.get();

        ItemStack gun = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(gun.getItem() instanceof InfusionGunItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos inputPos = PointerDataHelper.getLiquidInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getLiquidOutput(maid, level);
        BlockPos overflowPos = PointerDataHelper.getLiquidOverflow(maid, level);

        if (inputPos == null || outputPos == null || overflowPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!MaidRangeHelper.allWithinRange(maid, inputPos, outputPos, overflowPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!(level.getBlockEntity(inputPos) instanceof BasinBlockEntity)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (!(level.getBlockEntity(overflowPos) instanceof BasinBlockEntity)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        InfusionGunContents contents = InfusionGunItem.getContents(gun);

        MaidLookHelper.lookAtBlock(maid, contents.isEmpty() ? inputPos : outputPos);

        // ---- 枪空：从输入盆抽液 ----
        if (contents.isEmpty()) {
            boolean ok = tryExtractFromBasin(level, inputPos, maid, gun);
            if (ok) {
                // 反馈：抽液
                InfusionGunContents after = InfusionGunItem.getContents(gun);
                MaidFeedback.fluidDrainSound(level, inputPos);
                MaidFeedback.fluidParticles(level, inputPos, after.fluid(), 4);
            }
            status = ok ? Behavior.Status.RUNNING : Behavior.Status.STOPPED;
            return ok;
        }

        // ---- 枪有液：尝试注入输出 ----
        BlockEntity outBe = level.getBlockEntity(outputPos);

        // Belt 分支：对传送带上的物品注液，产物留传送带
        if (!(outBe instanceof DepotBlockEntity)
                && !(outBe instanceof BasinBlockEntity)
                && BlockEntityBehaviour.get(level, outputPos,
                TransportedItemStackHandlerBehaviour.TYPE) != null) {
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (InfusionGunItem.tryInjectItems(level, outputPos, maid, gun, filter)) {
                MaidFeedback.fluidFillSound(level, outputPos);
                MaidFeedback.fluidParticles(level, outputPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            // 没物品可注 → 走过剩
            if (InfusionGunItem.tryStoreFluid(level, overflowPos, maid, gun)) {
                MaidFeedback.fluidFillSound(level, overflowPos);
                MaidFeedback.fluidParticles(level, overflowPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // Depot 分支：对 Depot 上的物品注液
        if (outBe instanceof DepotBlockEntity) {
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (InfusionGunItem.tryInjectItems(level, outputPos, maid, gun, filter)) {
                MaidFeedback.fluidFillSound(level, outputPos);
                MaidFeedback.fluidParticles(level, outputPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            if (InfusionGunItem.tryStoreFluid(level, overflowPos, maid, gun)) {
                MaidFeedback.fluidFillSound(level, overflowPos);
                MaidFeedback.fluidParticles(level, overflowPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        // Basin 分支：纯液体转移
        if (outBe instanceof BasinBlockEntity) {
            if (InfusionGunItem.tryStoreFluid(level, outputPos, maid, gun)) {
                MaidFeedback.fluidFillSound(level, outputPos);
                MaidFeedback.fluidParticles(level, outputPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            if (InfusionGunItem.tryStoreFluid(level, overflowPos, maid, gun)) {
                MaidFeedback.fluidFillSound(level, overflowPos);
                MaidFeedback.fluidParticles(level, overflowPos, contents.fluid(), 4);
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    private static boolean tryExtractFromBasin(ServerLevel level, BlockPos pos,
                                            EntityMaid maid, ItemStack gun) {
    IFluidHandler handler = InfusionGunItem.getFluidHandler(level, pos);
    if (handler == null) return false;

    InfusionGunContents contents = InfusionGunItem.getContents(gun);
    if (contents.remaining() <= 0) return false;

    FluidStack source = FluidStack.EMPTY;
    for (int i = 0; i < handler.getTanks(); i++) {
        FluidStack inTank = handler.getFluidInTank(i);
        if (inTank.isEmpty()) continue;
        if (!contents.isEmpty()
                && !FluidStack.areFluidStackTagsEqual(contents.fluid(), inTank)) {
            continue;
        }
        source = inTank;
        break;
    }
    if (source.isEmpty()) return false;

    int want = Math.min(contents.remaining(), source.getAmount());
    if (want <= 0) return false;

    FluidStack toDrain = new FluidStack(source.getFluid(), want, source.getTag());
    FluidStack actual = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
    if (actual.isEmpty()) return false;

    InfusionGunItem.setContents(gun, contents.withFill(actual));
    InfusionGunItem.damageGun(gun, maid);

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