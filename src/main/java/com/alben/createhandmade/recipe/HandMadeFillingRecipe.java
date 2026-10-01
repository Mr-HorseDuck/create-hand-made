package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * 「注液家族」的 L3 独占配方类，服务灌注枪（{@link HandMadeTool#INFUSION_GUN}）。
 *
 * <p><b>为什么直接继承 {@link StandardProcessingRecipe}：</b>Create 的
 * {@code FillingRecipe} 唯一的公共构造把 type 写死成 {@code AllRecipeTypes.FILLING}
 * （{@code FillingRecipe.java:29-31}），无法子类化出自定义 type；但它的父类
 * {@code StandardProcessingRecipe} 提供了接受 {@code IRecipeTypeInfo} 的 public 构造
 * （{@code StandardProcessingRecipe.java:18-20}）。于是 {@code getType()} 返回
 * {@code create_hand_made:tool_recipe} —— Create 的注液器（Spout）查的是
 * {@code create:filling}，永远看不到这些配方。</p>
 *
 * <p>能力上限与 {@code FillingRecipe} 对齐（见各方法注释）。本类必须带<b>恰好一个流体输入</b>：
 * 工具侧的流体判定走 {@link #getRequiredFluid()}，没有流体输入的配方在加载期就会被
 * {@code ProcessingRecipe.validate()} 之外的使用路径炸掉 —— 实测由 {@code getRequiredFluid()}
 * 的 {@link IllegalStateException} 兜底。</p>
 */
public class HandMadeFillingRecipe extends StandardProcessingRecipe<RecipeInput>
        implements HandMadeToolRecipeLike {

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    public HandMadeFillingRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    /**
     * 单品判定，与 {@code FillingRecipe.matches} 一致（{@code FillingRecipe.java:33-37}）：
     * 只看第一个原料是否接受槽位 0 的物品。
     *
     * <p>与 Milling / Cutting / Pressing 的版本不同，Create 那份<b>没有</b> {@code inv.isEmpty()}
     * 检查；这里额外加了 {@code !ingredients.isEmpty()} 保护（否则空原料列表会在
     * {@code get(0)} 处抛 IndexOutOfBounds），对正常配方语义完全一致。</p>
     *
     * <p>注意：<b>流体判定不在这里</b> —— Create 与工具侧都是先 {@code matches} 再单独
     * {@code getRequiredFluid().test(fluid)}，本类沿用同样的分工。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        return !ingredients.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    /**
     * 本配方要求的流体，照抄 {@code FillingRecipe.getRequiredFluid}
     * （{@code FillingRecipe.java:54-58}）：取第一个流体原料，为空时抛异常。
     *
     * <p>灌注枪的工具侧依赖它做流体判定与消耗量计算，所以必须是 public。</p>
     */
    public SizedFluidIngredient getRequiredFluid() {
        if (fluidIngredients.isEmpty()) {
            throw new IllegalStateException("HandMade filling recipe has no fluid ingredient!");
        }
        return fluidIngredients.get(0);
    }

    /** 与 {@code FillingRecipe.getMaxInputCount()} 一致（{@code FillingRecipe.java:39-42}）。 */
    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    /** 与 {@code FillingRecipe.getMaxOutputCount()} 一致（{@code FillingRecipe.java:44-47}）。 */
    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    /** 与 {@code FillingRecipe.getMaxFluidInputCount()} 一致（{@code FillingRecipe.java:49-52}）。 */
    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    /**
     * 与 {@code FillingRecipe} 一致：它<b>没有</b>覆盖 {@code canSpecifyDuration()}，
     * 因此继承 {@code ProcessingRecipe} 的默认值 {@code false}
     * （{@code ProcessingRecipe.java:72-74}）。
     *
     * <p>显式写出来是为了把"注液独占配方不允许 {@code processing_time}"这条语义摆在明面上
     * （写了会在 {@code validate()} 里被拒绝），也避免将来 Create 改默认值时静默改变行为。</p>
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
