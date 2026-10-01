package com.alben.createhandmade.recipe;

import org.jetbrains.annotations.Nullable;

/**
 * L3 独占配方的公共视图。
 *
 * <p>{@link HandMadeToolRecipeSerializer} 需要在<b>不知道具体家族类</b>的前提下读写
 * {@code tool} 归属：独占配方按家族分成不同的配方类（basin 家族是
 * {@link HandMadeToolRecipe}，碾磨家族是 {@link HandMadeCrushingRecipe}），
 * 它们唯一的公共父类是 Create 的 {@code StandardProcessingRecipe}，
 * 而 {@code tool} 是本模组自己的字段，父类上没有。</p>
 *
 * <p>所以用这个接口把「归属工具」这一件事抽出来，serializer 侧统一
 * {@code ((HandMadeToolRecipeLike) recipe).setTool(...)} / {@code getTool()}。
 * 将来 T5c 批次 2-4 新增家族类时，只要实现本接口即可复用同一套 serializer 逻辑。</p>
 */
public interface HandMadeToolRecipeLike {

    /** 本配方归属哪个工具；由 serializer 在 decode 时写入。 */
    @Nullable
    HandMadeTool getTool();

    void setTool(@Nullable HandMadeTool tool);
}
