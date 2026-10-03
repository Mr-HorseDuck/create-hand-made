package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.compat.tlm.behavior.MaidUseHandSawBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUseInfusionGunBehavior;
import com.alben.createhandmade.compat.tlm.behavior.MaidUsePressHammerBehavior;
import com.alben.createhandmade.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 「手搓」工作模式。
 * 女仆会自动寻找附近的工作盆 / 置物台 / 传送带，并用手中工具加工。
 */
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
        return List.of(
                Pair.of(5, new MaidUsePressHammerBehavior()),
                Pair.of(6, new MaidUseInfusionGunBehavior()),
                Pair.of(7, new MaidUseHandSawBehavior())
        );
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    /** ★ 1.20.1 的 IMaidTask 要求实现此方法，返回 null 表示使用默认音效 */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }
}