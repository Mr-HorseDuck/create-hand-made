package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 「冲压家族」的 L3 独占配方类，服务冲压锤 · 置物台
 * （{@link HandMadeTool#PRESS_HAMMER_DEPOT}）。
 *
 * <p><b>为什么直接继承 {@link StandardProcessingRecipe}：</b>Create 的
 * {@code PressingRecipe} 唯一的公共构造把 type 写死成 {@code AllRecipeTypes.PRESSING}
 * （{@code PressingRecipe.java:29-31}），无法子类化出自定义 type；但它的父类
 * {@code StandardProcessingRecipe} 提供了
 * {@code public StandardProcessingRecipe(IRecipeTypeInfo, ProcessingRecipeParams)}
 * （{@code StandardProcessingRecipe.java:18-20}），可以把本模组的
 * {@link HandMadeRecipeTypes#TOOL_RECIPE} 传进去。于是 {@code getType()} 返回
 * {@code create_hand_made:tool_recipe} —— Create 的机械压床查的是 {@code create:pressing}，
 * 永远看不到这些配方。</p>
 *
 * <p>能力上限与 {@code PressingRecipe} 对齐（见各方法注释），因此本类与 Create 的压床配方
 * 在语义上等价，只是 type 不同。</p>
 */
public class HandMadePressingRecipe extends StandardProcessingRecipe<RecipeInput>
        implements HandMadeToolRecipeLike {

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    public HandMadePressingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    /**
     * 单品判定，与 {@code PressingRecipe.matches} 逐字一致
     * （{@code PressingRecipe.java:33-39}）：空输入不匹配，否则只看第一个原料是否接受槽位 0 的物品。
     *
     * <p>输入用最通用的 {@link RecipeInput}：工具侧传进来的是 {@code SingleRecipeInput}，
     * 而 Create 的 {@code PressingRecipe} 泛型参数也是它的子类型，两者兼容。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.isEmpty()) {
            return false;
        }
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    /** 与 {@code PressingRecipe.getMaxInputCount()} 一致（{@code PressingRecipe.java:41-44}）。 */
    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    /** 与 {@code PressingRecipe.getMaxOutputCount()} 一致（{@code PressingRecipe.java:46-49}）。 */
    @Override
    protected int getMaxOutputCount() {
        return 2;
    }

    /**
     * 与 {@code PressingRecipe} 一致：它<b>没有</b>覆盖 {@code canSpecifyDuration()}，
     * 因此继承 {@code ProcessingRecipe} 的默认值 {@code false}
     * （{@code ProcessingRecipe.java:72-74}）。
     *
     * <p>这里显式写出来是为了把"冲压独占配方不允许 {@code processing_time}"这条语义摆在明面上
     * （写了时长会在 {@code validate()} 里被拒绝），也避免将来 Create 改默认值时静默改变行为。</p>
     */
    @Override
    protected boolean canSpecifyDuration() {
        return false;
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
