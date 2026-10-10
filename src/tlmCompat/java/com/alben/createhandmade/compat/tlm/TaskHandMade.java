package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.compat.tlm.behavior.MaidUseCrusherMortarBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseHandSawBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseInfusionGunBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseLiquidTransferBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseMortarBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUsePointerBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUsePressHammerBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseStirringStaffBehavior;
import com.alben.createhandmade.item.ModItems;
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
                Pair.of(4, new MaidUsePointerBehavior()),
                Pair.of(5, new MaidUsePressHammerBehavior()),
                // ★ 液体模式必须优先于 MaidUseInfusionGunBehavior，
                //   两者都以灌注枪为主手，靠优先级区分：
                //   有液体标记时先走 LiquidTransfer，无标记时落到 InfusionGun。
                Pair.of(6, new MaidUseLiquidTransferBehavior()),
                Pair.of(7, new MaidUseInfusionGunBehavior()),
                Pair.of(8, new MaidUseStirringStaffBehavior()),
                Pair.of(9, new MaidUseHandSawBehavior()),
                Pair.of(10, new MaidUseMortarBehavior()),
                Pair.of(11, new MaidUseCrusherMortarBehavior())
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