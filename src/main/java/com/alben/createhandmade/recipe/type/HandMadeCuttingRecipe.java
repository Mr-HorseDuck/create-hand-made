package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * 切削家族的 L3 独占配方，服务手锯（ToolType.HAND_SAW）。
 *
 * 继承 CuttingRecipe —— matches / 上限 / canSpecifyDuration 全部沿用父类。
 * 只覆写 getType / getSerializer，把归属改到独立 type create_hand_made:tool_recipe。
 */
public class HandMadeCuttingRecipe extends CuttingRecipe
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeCuttingRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public RecipeType<?> getType() {
        return HandMadeRecipeTypes.TOOL_RECIPE.get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
    }

    @Nullable @Override public ToolType getTool() { return tool; }
    @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}