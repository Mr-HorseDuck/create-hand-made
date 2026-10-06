package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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

public class MaidUseCrusherMortarBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseCrusherMortarBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof CrusherMortarItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos inputPos = PointerDataHelper.getInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getOutput(maid, level);

        IItemHandler inputInv = readItemHandler(level, inputPos);
        IItemHandler outputInv = readItemHandler(level, outputPos);

        FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

        if (CrusherMortarItem.tryGrindOnce(level, maid, tool, inputInv, outputInv, filter)) {
            cooldown = CrusherMortarItem.FORCED_DURATION;
            status = Behavior.Status.RUNNING;
            return true;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    @Nullable
    private static IItemHandler readItemHandler(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return null;
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