package com.colin101.maidfactory;

import com.colin101.maidfactory.behavior.MaidUseBellowsBehavior;
import com.colin101.maidfactory.behavior.MaidUseCrusherMortarBehavior;
import com.colin101.maidfactory.behavior.MaidUseHandSawBehavior;
import com.colin101.maidfactory.behavior.MaidUseInfusionGunBehavior;
import com.colin101.maidfactory.behavior.MaidUseLiquidTransferBehavior;
import com.colin101.maidfactory.behavior.MaidUseMortarBehavior;
import com.colin101.maidfactory.behavior.MaidUsePointerDeployBehavior;
import com.colin101.maidfactory.behavior.MaidUsePressHammerBehavior;
import com.colin101.maidfactory.behavior.MaidUseStirringStaffBehavior;
import com.alben.createhandmade.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class TaskHandMade implements IMaidTask {

    public static final ResourceLocation UID =
            new ResourceLocation("create_hand_made", "hand_made");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(ModItems.PRESS_HAMMER.get());
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // ★ 必须返回可变列表：TLM 会在返回值上调用 .add(...) 追加自己的行为
        return Lists.newArrayList(
                Pair.of(5, new MaidUsePressHammerBehavior()),
                Pair.of(6, new MaidUseLiquidTransferBehavior()),
                // 灌注枪的"自己找附近置物台/工作盆注液"兜底：6 号行为管玩家用指杆指定的目标，
                // 这条管没指定时的就近加工；两者互斥（本行为在指定过目标时直接让位）。
                Pair.of(7, new MaidUseInfusionGunBehavior()),
                Pair.of(8, new MaidUseStirringStaffBehavior()),
                Pair.of(9, new MaidUseHandSawBehavior()),
                Pair.of(10, new MaidUseMortarBehavior()),
                Pair.of(11, new MaidUseCrusherMortarBehavior()),
                Pair.of(12, new MaidUseBellowsBehavior()),
                Pair.of(13, new MaidUsePointerDeployBehavior())
        );
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean enableEating(EntityMaid maid) {
        return false;
    }
}