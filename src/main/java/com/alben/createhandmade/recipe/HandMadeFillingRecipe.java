package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 注液家族的 L3 独占配方类，服务灌注枪（ToolType.INFUSION_GUN）。
 *
 * 继承 ProcessingRecipe（而非 FillingRecipe），因为 FillingRecipe 的构造
 * 把 type 写死成 AllRecipeTypes.FILLING。
 *
 * 必须带恰好一个流体输入，由 HandMadeToolRecipeSerializer 在 fromJson 时校验。
 */
public class HandMadeFillingRecipe extends ProcessingRecipe<RecipeWrapper>
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeFillingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv.isEmpty()) return false;
        return !ingredients.isEmpty() && ingredients.get(0).test(inv.getItem(0));
    }

    /** 取第一个流体原料，为空时抛异常。 */
    public FluidIngredient getRequiredFluid() {
        if (fluidIngredients.isEmpty()) {
            throw new IllegalStateException("HandMade filling recipe has no fluid ingredient!");
        }
        return fluidIngredients.get(0);
    }

    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected int getMaxFluidInputCount() { return 1; }
    @Override protected boolean canSpecifyDuration() { return false; }

    @Nullable @Override public ToolType getTool() { return tool; }
    @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}