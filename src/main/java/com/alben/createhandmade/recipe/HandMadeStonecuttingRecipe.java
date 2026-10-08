package com.alben.createhandmade.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * 「手锯 · 切石」的 L3 独占配方类（T7 批次 2）。
 *
 * <p>挂在独立的自定义 type {@code create_hand_made:stonecutting_recipe} 下：原版切石机与
 * Create 的机械锯查的是 {@code minecraft:stonecutting}，因此<b>永远看不到</b>这些配方；
 * 只有手锯切石段（{@code HandMadeTool#HAND_SAW_STONECUTTING} 的候选集）能读到它们。
 * 语义与 {@code tool_recipe} 下的 6 个家族同属 L3，只是换了一个 type。</p>
 *
 * <p><b>为什么继承原版 {@link SingleItemRecipe}：</b>{@code SingleItemRecipe} 提供了一个
 * <b>public</b> 构造 {@code (RecipeType<?>, RecipeSerializer<?>, String, Ingredient, ItemStack)}，
 * 允许子类把自己挂到任意自定义 type 上；而它唯一的常用子类 {@code StonecutterRecipe} 只有
 * {@code (group, ingredient, result)} 一个构造，且把 {@code RecipeType.STONECUTTING} /
 * {@code RecipeSerializer.STONECUTTER} 写死，无法用于自定义 type。所以这里直接继承
 * {@code SingleItemRecipe} 而不是 {@code StonecutterRecipe}。</p>
 *
 * <p><b>为什么要单独一个 type 而不是并进 {@code tool_recipe}：</b>{@code tool_recipe} 的
 * serializer（{@link HandMadeToolRecipeSerializer}）整体建立在 Create 的
 * {@code StandardProcessingRecipe} 上（codec 的返回类型就是它），而本类属于原版
 * {@code SingleItemRecipe} 家族，两者无法共用一条解析路径。所以照
 * {@link HandMadeRecipeTypes#BELLOWS_RECIPE} 的先例：独立常量 + 独立 serializer。</p>
 *
 * <p><b>没有 {@code tool} 字段：</b>整个 type 就是手锯切石专用，归属是隐含的 ——
 * 与 {@code bellows_recipe} 用 {@code fan_type} 而不是 {@code tool} 是同一个思路。
 * L2 过滤按 recipe id 工作，也不需要归属字段。</p>
 *
 * <p><b>没有覆盖 {@code getToastSymbol()}：</b>它的唯一消费者是原版 {@code RecipeToast}
 * （{@code RecipeToast.java:48}），而 toast 只在配方被"解锁"进原版配方书时弹出 ——
 * 自定义 type 不属于任何配方书类别，永远不会触发。既有的 6 个 L3 家族也都没覆盖它，
 * 保持一致比为一个不可达的默认值（工作台图标）加 4 行代码更划算。</p>
 */
public class HandMadeStonecuttingRecipe extends SingleItemRecipe {

    /**
     * 构造签名必须与 {@link SingleItemRecipe.Factory} 一致
     * （{@code (String group, Ingredient ingredient, ItemStack result)}），
     * 因为 {@link HandMadeStonecuttingRecipeSerializer} 复用的正是原版
     * {@link SingleItemRecipe.Serializer}，它会用这个工厂创建实例。
     */
    public HandMadeStonecuttingRecipe(String group, Ingredient ingredient, ItemStack result) {
        super(HandMadeRecipeTypes.STONECUTTING_RECIPE.getType(),
                HandMadeRecipeTypes.STONECUTTING_RECIPE.getSerializer(),
                group, ingredient, result);
    }

    /**
     * 单品判定，与 {@code StonecutterRecipe.matches} 逐字一致
     * （{@code StonecutterRecipe.java:12-14} 只做 {@code ingredient.test(input.item())}），
     * 这里多一个 {@code !input.isEmpty()} 保护：空输入不应匹配任何配方。
     *
     * <p>{@code getType()} / {@code getSerializer()} / {@code getResultItem()} /
     * {@code getIngredients()} / {@code assemble()} / {@code canCraftInDimensions()} 全部由
     * {@code SingleItemRecipe} 按构造参数实现，这里不需要重写。</p>
     */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && this.ingredient.test(input.item());
    }
}
