package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.alben.createhandmade.item.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
 * 标记三处：输入盆、输出（Basin 或 Depot）、过剩输出盆。
 *
 * 流程：
 *   枪空 → 从输入盆抽液到枪内
 *   枪有液 + 输出为 Basin → 注入输出盆
 *   枪有液 + 输出为 Depot → 对 Depot 上的物品走 Filling 配方
 *   输出塞不下 → 注入过剩输出盆
 *   过剩也塞不下 → 停止
 *
 * 冷却由 MaidConfig.maidInfusionGunCooldown 控制（与灌注枪行为共用）。
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
        // ★ 从配置读冷却，复用灌注枪的冷却项
        cooldown = MaidConfig.INSTANCE.maidInfusionGunCooldown.get();

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack gun = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(gun.getItem() instanceof InfusionGunItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 读指杆液体标记
        BlockPos inputPos = PointerDataHelper.getLiquidInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getLiquidOutput(maid, level);
        BlockPos overflowPos = PointerDataHelper.getLiquidOverflow(maid, level);

        if (inputPos == null || outputPos == null || overflowPos == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 交互距离校验
        if (!MaidRangeHelper.allWithinRange(maid, inputPos, outputPos, overflowPos)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 输入必须是 Basin
        if (!(level.getBlockEntity(inputPos) instanceof BasinBlockEntity)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // 过剩输出必须是 Basin
        if (!(level.getBlockEntity(overflowPos) instanceof BasinBlockEntity)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        InfusionGunContents contents = InfusionGunItem.getContents(gun);

        // ---- 枪空：从输入盆抽液 ----
        if (contents.isEmpty()) {
            boolean ok = tryExtractFromBasin(level, inputPos, maid, gun);
            status = ok ? Behavior.Status.RUNNING : Behavior.Status.STOPPED;
            return ok;
        }

        // ---- 枪有液：尝试注入输出 ----
        BlockEntity outBe = level.getBlockEntity(outputPos);

        if (outBe instanceof DepotBlockEntity) {
            // Depot 模式：走 Filling 配方灌注 Depot 上的物品
            FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);
            if (InfusionGunItem.tryInjectItems(level, outputPos, maid, gun, filter)) {
                status = Behavior.Status.RUNNING;
                return true;
            }
            // 没物品可灌注 → 走过剩
            if (InfusionGunItem.tryStoreFluid(level, overflowPos, maid, gun)) {
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (outBe instanceof BasinBlockEntity) {
            // Basin 模式：纯液体转移
            if (InfusionGunItem.tryStoreFluid(level, outputPos, maid, gun)) {
                status = Behavior.Status.RUNNING;
                return true;
            }
            // 输出满 → 走过剩
            if (InfusionGunItem.tryStoreFluid(level, overflowPos, maid, gun)) {
                status = Behavior.Status.RUNNING;
                return true;
            }
            status = Behavior.Status.STOPPED;
            return false;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    /**
     * 从 Basin 抽液到枪内。
     *
     * ★ 不调 InfusionGunItem.tryExtractTick：
     *   那个方法有 EXTRACT_DAMAGED 的 per-session 机制（只在玩家 releaseUsing 时清理），
     *   女仆不会触发 releaseUsing，会导致抽液只扣一次耐久。
     *   这里直接操作 IFluidHandler，每次抽液都正确扣耐久。
     */
    private static boolean tryExtractFromBasin(ServerLevel level, BlockPos pos,
                                                EntityMaid maid, ItemStack gun) {
        IFluidHandler handler = InfusionGunItem.getFluidHandler(level, pos);
        if (handler == null) return false;

        InfusionGunContents contents = InfusionGunItem.getContents(gun);
        if (contents.remaining() <= 0) return false;

        // 找枪能接收的液体（同类液体，或枪为空）
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

        level.playSound(null, pos, FluidHelper.getFillSound(actual),
                SoundSource.PLAYERS, 0.5f, 1.0f + level.random.nextFloat() * 0.2f);

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