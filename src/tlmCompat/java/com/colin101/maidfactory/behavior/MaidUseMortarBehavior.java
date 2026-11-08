package com.colin101.maidfactory.behavior;

import com.alben.createhandmade.api.CreateHandMadeApi;
import com.colin101.maidfactory.MaidConfig;
import com.colin101.maidfactory.MaidFavorabilityHelper;
import com.colin101.maidfactory.PointerDataHelper;
import com.colin101.maidfactory.MaidBubbleHelper;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.item.MortarItem;
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

public class MaidUseMortarBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseMortarBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = ((LivingEntity) maid).getItemInHand(InteractionHand.MAIN_HAND);
        if (!(tool.getItem() instanceof MortarItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        if (MaidBatchCraftingBlocker.checkAndRefuse(maid, tool)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        BlockPos inputPos = PointerDataHelper.getInput(maid, level);
        BlockPos outputPos = PointerDataHelper.getOutput(maid, level);

        IItemHandler inputInv = readItemHandler(level, inputPos);
        IItemHandler outputInv = readItemHandler(level, outputPos);

        FilterItemStack filter = MaidHelpers.Filter.findFilterStack(maid);

        if (CreateHandMadeApi.grindMortarOnce(level, maid, tool, inputInv, outputInv, filter, false)) {
            // ★ 这里是研钵，不是碾钵：以前误传 CRUSHER_MORTAR，导致
            //   "maidfactory_used_crusher_mortar" 被提前烧掉、而研钵自己的首次标记
            //   永远没人写 —— 表现为研钵第一次用不弹气泡，碾钵的首次气泡也跟着丢。
            MaidBubbleHelper.tryShowFirstUse(maid, ToolType.MORTAR, tool);
            MaidBubbleHelper.tryShow(maid, ToolType.MORTAR, tool);
            cooldown = MaidFavorabilityHelper.cooldownFor(MaidConfig.INSTANCE.maidMortarCooldown, maid);
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