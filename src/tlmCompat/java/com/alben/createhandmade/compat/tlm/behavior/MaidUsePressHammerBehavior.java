package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.alben.createhandmade.item.PressHammerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

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

        // ★ 强转 LivingEntity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof PressHammerItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

        BlockPos basinPos = findNearbyBasin(level, maid);
        if (basinPos != null && PressHammerItem.tryPressBasin(level, basinPos, filter)) {
            tool.hurtAndBreak(1, maid, e -> {});
            cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
            status = Behavior.Status.RUNNING;
            return true;
        }

        BlockPos depotPos = findNearbyDepot(level, maid);
        if (depotPos != null && PressHammerItem.tryPressTransported(level, depotPos, filter)) {
            tool.hurtAndBreak(1, maid, e -> {});
            cooldown = MaidConfig.INSTANCE.maidPressHammerCooldown.get();
            status = Behavior.Status.RUNNING;
            return true;
        }

        status = Behavior.Status.STOPPED;
        return false;
    }

    @Override
    public void tickOrStop(ServerLevel level, EntityMaid maid, long gameTime) {
        doStop(level, maid, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, EntityMaid maid, long gameTime) {
        status = Behavior.Status.STOPPED;
    }

    private BlockPos findNearbyBasin(ServerLevel level, EntityMaid maid) {
        BlockPos maidPos = ((Entity) maid).blockPosition();
        int r = (int) MaidConfig.INSTANCE.maidSearchRadius.get().doubleValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-r, -r, -r), maidPos.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BasinBlockEntity basin && !basin.isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private BlockPos findNearbyDepot(ServerLevel level, EntityMaid maid) {
        BlockPos maidPos = ((Entity) maid).blockPosition();
        int r = (int) MaidConfig.INSTANCE.maidSearchRadius.get().doubleValue();

        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-r, -r, -r), maidPos.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepotBlockEntity depot && !depot.getHeldItem().isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }
}