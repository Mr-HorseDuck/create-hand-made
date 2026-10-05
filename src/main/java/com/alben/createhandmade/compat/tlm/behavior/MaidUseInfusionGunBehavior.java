package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.item.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class MaidUseInfusionGunBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseInfusionGunBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = maid.getMainHandItem();

        if (!(tool.getItem() instanceof InfusionGunItem)
                && Config.INSTANCE.maidAutoSwitchTool.get()) {
            MaidToolHelper.tryEquipFromInventory(maid, InfusionGunItem.class);
            tool = maid.getMainHandItem();
        }

        if (!(tool.getItem() instanceof InfusionGunItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        InfusionGunContents contents = InfusionGunItem.getContents(tool);
        if (contents.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 从女仆背包查找过滤器
        FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

        // 1. 优先置物台
        BlockPos depotPos = findNearbyDepot(level, maid);
        if (depotPos != null && InfusionGunItem.tryInjectItems(level, depotPos, maid, tool, filter)) {
            cooldown = Config.INSTANCE.maidInfusionGunCooldown.get();
            status = Behavior.Status.RUNNING;
            return true;
        }

        // 2. 其次流体容器（存入流体）
        BlockPos basinPos = findNearbyBasin(level, maid);
        if (basinPos != null) {
            IFluidHandler handler = InfusionGunItem.getFluidHandler(level, basinPos);
            if (handler != null && InfusionGunItem.tryStoreFluid(level, basinPos, maid, tool)) {
                cooldown = Config.INSTANCE.maidInfusionGunCooldown.get();
                status = Behavior.Status.RUNNING;
                return true;
            }
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
        BlockPos maidPos = maid.blockPosition();
        int r = (int) Config.INSTANCE.maidSearchRadius.get().doubleValue();

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
        BlockPos maidPos = maid.blockPosition();
        int r = (int) Config.INSTANCE.maidSearchRadius.get().doubleValue();

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