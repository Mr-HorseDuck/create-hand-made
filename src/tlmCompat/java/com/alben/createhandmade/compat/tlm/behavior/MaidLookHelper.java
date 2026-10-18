package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.PointerDataHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class MaidLookHelper {

    private static final float YAW_SPEED = 30.0F;
    private static final float PITCH_SPEED = 30.0F;

    private MaidLookHelper() {}

    public static void lookAtBlock(EntityMaid maid, @Nullable BlockPos pos) {
        if (pos == null) return;
        Mob mob = (Mob) maid;
        mob.getLookControl().setLookAt(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                YAW_SPEED, PITCH_SPEED);
    }

    public static void lookAtVec(EntityMaid maid, @Nullable Vec3 vec) {
        if (vec == null) return;
        Mob mob = (Mob) maid;
        mob.getLookControl().setLookAt(
                vec.x, vec.y, vec.z,
                YAW_SPEED, PITCH_SPEED);
    }

    public static void lookAtWork(EntityMaid maid, ServerLevel level) {
        lookAtBlock(maid, PointerDataHelper.getWork(maid, level));
    }

    public static void lookAtLiquidOutput(EntityMaid maid, ServerLevel level) {
        lookAtBlock(maid, PointerDataHelper.getLiquidOutput(maid, level));
    }
}