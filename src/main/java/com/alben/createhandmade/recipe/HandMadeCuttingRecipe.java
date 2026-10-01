package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 「切削家族」的 L3 独占配方类，服务手锯（{@link HandMadeTool#HAND_SAW}）。
 *
 * <p><b>为什么直接继承 {@link StandardProcessingRecipe}：</b>Create 的
 * {@code CuttingRecipe} 唯一的公共构造把 type 写死成 {@code AllRecipeTypes.CUTTING}
 * （{@code CuttingRecipe.java:29-31}），无法子类化出自定义 type；但它的父类
 * {@code StandardProcessingRecipe} 提供了接受 {@code IRecipeTypeInfo} 的 public 构造
 * （{@code StandardProcessingRecipe.java:18-20}）。于是 {@code getType()} 返回
 * {@code create_hand_made:tool_recipe} —— Create 的机械锯查的是 {@code create:cutting}，
 * 永远看不到这些配方。</p>
 *
 * <p>能力上限与 {@code CuttingRecipe} 对齐（见各方法注释）。</p>
 */
public class HandMadeCuttingRecipe extends StandardProcessingRecipe<RecipeInput>
        implements HandMadeToolRecipeLike {

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    public HandMadeCuttingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    /**
     * 单品判定，与 {@code CuttingRecipe.matches} 逐字一致
     * （{@code CuttingRecipe.java:33-39}）：空输入不匹配，否则只看第一个原料是否接受槽位 0 的物品。
     *
     * <p>Create 那侧签名是 {@code matches(RecipeWrapper, Level)}，而 {@code RecipeWrapper}
     * 实现了 {@code RecipeInput}，所以这里用更通用的 {@link RecipeInput} 即可同时接住
     * 手锯传进来的 {@code RecipeWrapper}。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.isEmpty()) {
            return false;
        }
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    /** 与 {@code CuttingRecipe.getMaxInputCount()} 一致（{@code CuttingRecipe.java:41-44}）。 */
    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    /** 与 {@code CuttingRecipe.getMaxOutputCount()} 一致（{@code CuttingRecipe.java:46-49}）。 */
    @Override
    protected int getMaxOutputCount() {
        return 4;
    }

    /** 与 {@code CuttingRecipe.canSpecifyDuration()} 一致（{@code CuttingRecipe.java:51-54}）。 */
    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    @Nullable
    @Override
    public HandMadeTool getTool() {
        return tool;
    }

    @Override
    public void setTool(@Nullable HandMadeTool tool) {
        this.tool = tool;
    }
}
