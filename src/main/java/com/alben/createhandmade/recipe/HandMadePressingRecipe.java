package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 冲压家族的 L3 独占配方类，服务冲压锤 · 置物台（ToolType.PRESS_HAMMER_DEPOT）。
 *
 * 继承 ProcessingRecipe（而非 PressingRecipe），因为 PressingRecipe 的构造
 * 把 type 写死成 AllRecipeTypes.PRESSING。
 *
 * tool 字段由 HandMadeToolRecipeSerializer 读写，本类只做存取。
 */
public class HandMadePressingRecipe extends ProcessingRecipe<RecipeWrapper>
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadePressingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv.isEmpty()) return false;
        return !ingredients.isEmpty() && ingredients.get(0).test(inv.getItem(0));
    }

    @Override
    protected int getMaxInputCount() { return 1; }

    @Override
    protected int getMaxOutputCount() { return 2; }

    @Override
    protected boolean canSpecifyDuration() { return false; }

    @Nullable @Override
    public ToolType getTool() { return tool; }

    @Override
    public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}