package com.alben.createhandmade.recipe;

import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 「碾磨家族」的 L3 独占配方类，由研钵（{@link HandMadeTool#MORTAR}）与
 * 碾钵（{@link HandMadeTool#CRUSHER_MORTAR}）共用。
 *
 * <p><b>为什么继承 {@link AbstractCrushingRecipe}：</b>这是"换父类"路径的核心 ——
 * {@code MillingRecipe} / {@code CrushingRecipe} 的公共构造把 type 写死成
 * {@code AllRecipeTypes.MILLING} / {@code CRUSHING}，无法子类化出自定义 type；
 * 但它们的共同父类 {@code AbstractCrushingRecipe} 提供了
 * {@code public AbstractCrushingRecipe(IRecipeTypeInfo, ProcessingRecipeParams)}
 * （{@code AbstractCrushingRecipe.java:11-13}），可以把本模组的
 * {@link HandMadeRecipeTypes#TOOL_RECIPE} 传进去，从而让 {@code getType()} 返回
 * {@code create_hand_made:tool_recipe} —— Create 的磨石 / 粉碎轮查的是自己的类型，
 * 永远看不到这些配方。</p>
 *
 * <p><b>代价：</b>它不是 {@code MillingRecipe} 也不是 {@code CrushingRecipe}，
 * 所以工具侧与 JEI 侧的 {@code instanceof MillingRecipe} 判定需要放宽
 * （见 {@code MortarItem} / {@code CreateHandMadeJEI}）。</p>
 *
 * <p><b>与 {@link HandMadeToolRecipe} 的关系：</b>两者唯一的公共父类是 Create 的
 * {@code StandardProcessingRecipe<RecipeInput>}，所以 serializer 以它为返回类型，
 * 并通过 {@link HandMadeToolRecipeLike} 读写 {@code tool} 归属。</p>
 */
public class HandMadeCrushingRecipe extends AbstractCrushingRecipe implements HandMadeToolRecipeLike {

    /**
     * 上限取「研磨 4」与「粉碎 7」的并集。
     *
     * <p>本类同时服务两个工具，而 {@code ProcessingRecipe.validate()} 只认类上的固定上限：
     * {@code MillingRecipe.getMaxOutputCount() = 4}、{@code CrushingRecipe} 是 7
     * （{@code MillingRecipe.java:27-30}、{@code CrushingRecipe.java:26-29}）。
     * 取 7 是为了不误拒碾钵的多产物配方；研钵侧多写几个产物也只是"宽松"，不会出错
     * （运行时消费走 {@code rollResults}，不做数量校验）。</p>
     */
    private static final int MAX_OUTPUT_COUNT = 7;

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    public HandMadeCrushingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    /**
     * 单品判定，与 {@code MillingRecipe.matches} / {@code CrushingRecipe.matches} 逐字一致
     * （{@code MillingRecipe.java:19-25}、{@code CrushingRecipe.java:18-24}）：
     * 空输入不匹配，否则只看第一个原料是否接受槽位 0 的物品。
     *
     * <p>{@code AbstractCrushingRecipe} 自己没有实现 {@code matches}（它只提供了
     * 输入上限与"允许时长"），所以必须在这里实现。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.isEmpty()) {
            return false;
        }
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    @Override
    protected int getMaxOutputCount() {
        return MAX_OUTPUT_COUNT;
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
