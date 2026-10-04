package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.item.HandSawItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

public class MaidUseHandSawBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;
    private int cooldown = 0;

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return "MaidUseHandSawBehavior";
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long gameTime) {
        if (cooldown > 0) {
            cooldown--;
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack tool = maid.getMainHandItem();

        if (!(tool.getItem() instanceof HandSawItem)
                && Config.INSTANCE.maidAutoSwitchTool.get()) {
            MaidToolHelper.tryEquipFromInventory(maid, HandSawItem.class);
            tool = maid.getMainHandItem();
        }

        if (!(tool.getItem() instanceof HandSawItem)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        ItemStack off = maid.getOffhandItem();
        if (off.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, off);
        if (recipes.isEmpty()) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        // ★ 从女仆背包查找过滤器
        FilterItemStack filter = MaidFilterHelper.findFilterStack(maid);

        // ★ 过滤不通过时返回 false，不设冷却
        if (!HandSawItem.executeCut(level, maid, tool, filter)) {
            status = Behavior.Status.STOPPED;
            return false;
        }

        cooldown = Config.INSTANCE.maidHandSawCooldown.get();
        status = Behavior.Status.RUNNING;
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