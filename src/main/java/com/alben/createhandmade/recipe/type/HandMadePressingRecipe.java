package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * 冲压家族的 L3 独占配方，服务冲压锤 · 置物台（ToolType.PRESS_HAMMER_DEPOT）。
 *
 * 继承 PressingRecipe —— matches / 上限 / canSpecifyDuration 全部沿用父类。
 */
public class HandMadePressingRecipe extends PressingRecipe
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadePressingRecipe(ProcessingRecipeParams params) {
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