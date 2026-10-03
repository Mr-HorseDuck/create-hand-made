package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.item.PressHammerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MaidUsePressHammerBehavior implements BehaviorControl<EntityMaid> {

    private static final double SEARCH_RADIUS = 8.0;

    private Behavior.Status status = Behavior.Status.STOPPED;

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
        ItemStack tool = maid.getMainHandItem();
        if (!(tool.getItem() instanceof PressHammerItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos target = findNearbyTarget(level, maid);
        if (target == null) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        boolean success = PressHammerItem.tryPressBasin(level, target)
                || PressHammerItem.tryPressTransported(level, target);

        if (success) {
            tool.hurtAndBreak(1, maid, e -> {});
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

    private BlockPos findNearbyTarget(ServerLevel level, EntityMaid maid) {
        BlockPos maidPos = maid.blockPosition();
        int r = (int) SEARCH_RADIUS;

        for (BlockPos pos : BlockPos.betweenClosed(
                maidPos.offset(-r, -r, -r), maidPos.offset(r, r, r))) {

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BasinBlockEntity basin && !basin.isEmpty()) return pos.immutable();
            if (be instanceof DepotBlockEntity depot && !depot.getHeldItem().isEmpty()) return pos.immutable();
            if (BlockEntityBehaviour.get(level, pos,
                    TransportedItemStackHandlerBehaviour.TYPE) != null) return pos.immutable();
        }
        return null;
    }
}