package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 风箱的 L3 独占配方类，服务 {@code create_hand_made:bellows_recipe} 类型。
 *
 * <p><b>为什么直接继承 {@link StandardProcessingRecipe}：</b>与其它 6 个 L3 家族同样的理由
 * —— Create 各具体配方类（{@code HauntingRecipe} / {@code SplashingRecipe} 等）的公共构造
 * 把 {@code RecipeType} 写死成自己的枚举（{@code HauntingRecipe.java:15-17}），子类化会让配方
 * 落进 {@code create:haunting}，Create 的鼓风机也就读得到了，独占性直接失效。
 * {@link StandardProcessingRecipe} 提供了接受自定义 {@code IRecipeTypeInfo} 的 public 构造
 * （{@code StandardProcessingRecipe.java:18}），于是 {@code getType()} 返回
 * {@code create_hand_made:bellows_recipe} —— Create 的机器与鼓风机都查不到。</p>
 *
 * <p><b>{@link #fanType} 不在 params 里：</b>{@code ProcessingRecipeParams} 的字段是
 * {@code protected}（{@code ProcessingRecipeParams.java:27-32}），外部包读不到；
 * 所以它由 {@link HandMadeBellowsRecipeSerializer} 自己从 JSON 读 {@code fan_type} 后写入。</p>
 *
 * <p><b>上限的取值依据：</b></p>
 * <ul>
 *   <li>输入 1 / 输出 12 —— 与 Create 的鼓风配方一致
 *       （{@code HauntingRecipe.java:27-35}、{@code SplashingRecipe.java:27-35}）；</li>
 *   <li>不允许 {@code processing_time} —— 风箱是<b>瞬发</b>的（松手一次结算，没有 tick 累计；
 *       Create 鼓风的时长来自全局 config {@code fanProcessingTime}，不是配方字段）。
 *       写了会在 {@code validate()} 里被拒（{@code ProcessingRecipe.java:108}）；</li>
 *   <li>不允许流体输入 —— 与 {@code ProcessingRecipe} 的默认值一致
 *       （{@code ProcessingRecipe.java:76-78}），显式写出来是为了把语义摆在明面上；
 *       风箱的"介质"是副手物品，不在配方里。</li>
 * </ul>
 */
public class HandMadeBellowsRecipe extends StandardProcessingRecipe<RecipeInput> {

    /** 这条独占配方属于哪种鼓风；由 {@link HandMadeBellowsRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private FanType fanType;

    public HandMadeBellowsRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.BELLOWS_RECIPE, params);
    }

    /**
     * 单品判定，照抄 Create 的鼓风配方
     * （{@code HauntingRecipe.java:19-25} / {@code SplashingRecipe.java:19-25}）：
     * 输入为空则 false，否则只测第一个原料。
     *
     * <p>比 Create 那两份多了两个守卫：Create 的入参是 {@code SingleRecipeInput}（永远有 1 槽），
     * 而这里为了复用现有 L3 家族的泛型用的是 {@link RecipeInput}，槽数由调用方决定；
     * 另外 {@code ProcessingRecipe.validate()} 只检查上限、<b>不检查下限</b>
     * （{@code ProcessingRecipe.java:84-115}），所以一条 0 原料的配方也能加载成功 ——
     * 两个守卫保证这种情况在匹配阶段返回 false，而不是抛 IndexOutOfBounds。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (ingredients.isEmpty() || input.size() < 1) {
            return false;
        }
        if (input.isEmpty()) {
            return false;
        }
        return ingredients.get(0).test(input.getItem(0));
    }

    /** 与 {@code HauntingRecipe.getMaxInputCount()} 一致（{@code HauntingRecipe.java:27-30}）。 */
    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    /** 与 {@code HauntingRecipe.getMaxOutputCount()} 一致（{@code HauntingRecipe.java:32-35}）。 */
    @Override
    protected int getMaxOutputCount() {
        return 12;
    }

    /**
     * 风箱瞬发，不接受 {@code processing_time}；同时也是 {@code ProcessingRecipe} 的默认值
     * （{@code ProcessingRecipe.java:72-74}），显式写出来避免将来 Create 改默认值时静默变行为。
     */
    @Override
    protected boolean canSpecifyDuration() {
        return false;
    }

    /** 禁止流体原料（= 默认值，见类注释）。 */
    @Override
    protected int getMaxFluidInputCount() {
        return 0;
    }

    @Nullable
    public FanType getFanType() {
        return fanType;
    }

    public void setFanType(@Nullable FanType fanType) {
        this.fanType = fanType;
    }
}
