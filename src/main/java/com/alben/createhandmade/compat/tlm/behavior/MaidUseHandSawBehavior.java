package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.item.HandSawItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

public class MaidUseHandSawBehavior implements BehaviorControl<EntityMaid> {

    private Behavior.Status status = Behavior.Status.STOPPED;

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
        ItemStack tool = maid.getMainHandItem();
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

        HandSawItem.executeCut(level, maid, tool);
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