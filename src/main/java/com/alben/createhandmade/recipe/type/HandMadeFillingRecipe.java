package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * 注液家族的 L3 独占配方，服务灌注枪（ToolType.INFUSION_GUN）。
 *
 * 继承 FillingRecipe —— matches / 上限 / getRequiredFluid 全部沿用父类。
 */
public class HandMadeFillingRecipe extends FillingRecipe
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeFillingRecipe(ProcessingRecipeParams params) {
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