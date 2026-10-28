package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 碾磨家族的 L3 独占配方类，由研钵（ToolType.MORTAR）与
 * 碾钵（ToolType.CRUSHER_MORTAR）共用。
 *
 * 继承 AbstractCrushingRecipe（MillingRecipe / CrushingRecipe 的公共父类），
 * 因为它提供了接受 IRecipeTypeInfo 的 public 构造，可以把本模组的
 * TOOL_RECIPE_INFO 传进去，让 getType() 返回 create_hand_made:tool_recipe。
 *
 * 代价：它不是 MillingRecipe 也不是 CrushingRecipe，
 * 所以工具侧与 JEI 侧的 instanceof MillingRecipe 判定需要放宽。
 */
public class HandMadeCrushingRecipe extends AbstractCrushingRecipe
        implements HandMadeToolRecipeLike {

    /**
     * 上限取「研磨 4」与「粉碎 7」的并集。
     * 本类同时服务两个工具，取最大值不误拒碾钵的多产物配方。
     */
    private static final int MAX_OUTPUT_COUNT = 7;

    @Nullable
    private ToolType tool;

    public HandMadeCrushingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv.isEmpty()) return false;
        return !ingredients.isEmpty() && ingredients.get(0).test(inv.getItem(0));
    }

    @Override
    protected int getMaxOutputCount() {
        return MAX_OUTPUT_COUNT;
    }

    @Nullable @Override
    public ToolType getTool() { return tool; }

    @Override
    public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}