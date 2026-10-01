package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import org.jetbrains.annotations.Nullable;

/**
 * 手工工具独占配方的配方类（L3）。
 *
 * <p>它继承 {@link BasinRecipe} 而不是直接继承 {@code StandardProcessingRecipe}，
 * 原因是 Create 只给这一条家族留了可用的扩展口：
 * {@code protected BasinRecipe(IRecipeTypeInfo type, ProcessingRecipeParams params)}
 * （{@code BasinRecipe.java:187}）—— 子类可以把自己的 {@link HandMadeRecipeTypes}
 * 传进去，从而让 {@code getType()} 返回 {@code create_hand_made:tool_recipe}
 * 而不是被写死的 {@code create:basin}。</p>
 *
 * <p>Create 的其它配方类（{@code MillingRecipe} / {@code PressingRecipe} /
 * {@code CuttingRecipe} / {@code FillingRecipe} 等）唯一的公共构造把 type
 * 硬编码成了自己的 {@code AllRecipeTypes}，因此<b>无法</b>既保持
 * {@code instanceof} 语义又换 type —— 那些家族的 L3 需要另想办法
 * （见 T4 调研报告）。</p>
 *
 * <p>同时继承 {@code BasinRecipe} 还带来两个白拿的好处：</p>
 * <ul>
 *   <li>工具侧的 {@code BasinRecipe.match(basin, recipe)} / {@code apply(...)}
 *       对它是直接可用的，工作盆类工具无需改动；</li>
 *   <li>JEI 现有类别的类型过滤 {@code BasinRecipe.class.isInstance(...)} 也能识别它。</li>
 * </ul>
 *
 * <p><b>{@link #tool} 为什么用 setter 而不是构造参数：</b>
 * Create 的 {@code StandardProcessingRecipe.Serializer} 的工厂是
 * {@code R create(ProcessingRecipeParams params)}，只接受一个参数；
 * 而 {@code tool} 不在 {@code ProcessingRecipeParams} 里，所以只能在
 * decode 之后由 {@link HandMadeToolRecipeSerializer} 手动 {@code setTool(...)}。</p>
 */
public class HandMadeToolRecipe extends BasinRecipe implements HandMadeToolRecipeLike {

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    public HandMadeToolRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    @Nullable
    public HandMadeTool getTool() {
        return tool;
    }

    public void setTool(@Nullable HandMadeTool tool) {
        this.tool = tool;
    }
}
