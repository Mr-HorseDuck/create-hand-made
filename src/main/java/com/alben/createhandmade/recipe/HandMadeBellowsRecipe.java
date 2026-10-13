package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 风箱的 L3 独占配方类，服务 create_hand_made:bellows_recipe 类型。
 *
 * 继承 ProcessingRecipe（与其他 L3 家族一致），挂到独立的 BELLOWS_RECIPE type。
 * fan_type 不在 params 里，由 serializer 读 JSON 后写入。
 *
 * 上限与 Create 的鼓风配方对齐（输入 1 / 输出 12）。
 * 不允许 processing_time（风箱瞬发）。
 * 不允许流体输入（风箱的"介质"是副手物品，不在配方里）。
 */
public class HandMadeBellowsRecipe extends ProcessingRecipe<RecipeWrapper> {

    @Nullable
    private FanType fanType;

    public HandMadeBellowsRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.BELLOWS_RECIPE_INFO, params);
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (ingredients.isEmpty() || inv.getContainerSize() < 1) return false;
        if (inv.isEmpty()) return false;
        return ingredients.get(0).test(inv.getItem(0));
    }

    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 12; }
    @Override protected boolean canSpecifyDuration() { return false; }
    @Override protected int getMaxFluidInputCount() { return 0; }

    @Nullable public FanType getFanType() { return fanType; }
    public void setFanType(@Nullable FanType fanType) { this.fanType = fanType; }
}