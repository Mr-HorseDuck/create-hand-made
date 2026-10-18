package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import org.jetbrains.annotations.Nullable;

/**
 * 工作盆家族的 L3 独占配方类，服务冲压锤（ToolType.PRESS_HAMMER_BASIN）
 * 和搅拌杖（ToolType.STIRRING_STAFF）。
 *
 * 继承 BasinRecipe（而非 ProcessingRecipe），因为 BasinRecipe 提供了
 * protected BasinRecipe(IRecipeTypeInfo, ProcessingRecipeParams) 构造，
 * 可以把本模组的 TOOL_RECIPE_INFO 传进去，让 getType() 返回
 * create_hand_made:tool_recipe 而不是 create:basin。
 *
 * 白拿的两个好处：
 *   - 工具侧的 BasinRecipe.match(basin, recipe) / apply(...) 对它是直接可用的
 *   - JEI 现有类别的 instanceof BasinRecipe 判定也能识别它
 *
 * 注意：BasinRecipe.matches(Container, Level) 恒返回 false（Create 的原设计），
 * 匹配走静态 BasinRecipe.match。所以本类不需要覆写 matches。
 */
public class HandMadeToolRecipe extends BasinRecipe
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeToolRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    @Nullable @Override
    public ToolType getTool() { return tool; }

    @Override
    public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}