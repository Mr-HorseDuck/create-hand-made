package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 切削家族的 L3 独占配方类，服务手锯（ToolType.HAND_SAW）。
 *
 * 继承 ProcessingRecipe（而非 CuttingRecipe），因为 CuttingRecipe 的构造
 * 把 type 写死成 AllRecipeTypes.CUTTING。这里传自己的 TOOL_RECIPE_INFO。
 *
 * tool 字段由 HandMadeToolRecipeSerializer 在解析完成后统一写入，
 * 本类只做存取，不覆写 readAdditional / writeAdditional。
 */
public class HandMadeCuttingRecipe extends ProcessingRecipe<RecipeWrapper>
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeCuttingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    // ==================== 匹配 ====================

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (inv.isEmpty()) return false;
        return !ingredients.isEmpty() && ingredients.get(0).test(inv.getItem(0));
    }

    // ==================== 上限 ====================

    @Override
    protected int getMaxInputCount() { return 1; }

    @Override
    protected int getMaxOutputCount() { return 4; }

    @Override
    protected boolean canSpecifyDuration() { return true; }

    // ==================== HandMadeToolRecipeLike ====================

    @Nullable @Override
    public ToolType getTool() { return tool; }

    @Override
    public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}