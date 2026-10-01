package com.alben.createhandmade.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 「应用家族」的 L3 独占配方类，服务指杆（{@link HandMadeTool#POINTER}）。
 *
 * <p><b>为什么直接继承 {@link StandardProcessingRecipe}：</b>Create 的
 * {@code ItemApplicationRecipe} 唯一的公共构造签名是
 * {@code (AllRecipeTypes type, ItemApplicationRecipeParams params)}
 * （{@code ItemApplicationRecipe.java:23-26}）—— 参数类型是 <b>枚举</b>而不是
 * {@code IRecipeTypeInfo}，所以既不能传本模组的 type，也无法子类化出自定义 type。
 * 好在意向中的父类 {@code ProcessingRecipe} 有接受 {@code IRecipeTypeInfo} 的 public 构造
 * （{@code ProcessingRecipe.java:48}），经由 {@link StandardProcessingRecipe} 即可
 * （{@code StandardProcessingRecipe.java:18-20}）。于是 {@code getType()} 返回
 * {@code create_hand_made:tool_recipe} —— Create 的部署器（Deployer）查的是
 * {@code create:item_application} / {@code create:deploying}，看不到这些配方。</p>
 *
 * <p><b>两个字段不在 params 里：</b></p>
 * <ul>
 *   <li>{@link #tool} —— 本模组的归属字段，由 {@link HandMadeToolRecipeSerializer} 读 JSON 后写入；</li>
 *   <li>{@link #keepHeldItem} —— Create 把它放在 {@code ItemApplicationRecipeParams} 里，
 *       而那个字段与访问器都是 {@code protected}
 *       （{@code ItemApplicationRecipeParams.java:32-36}），外部包读不到；
 *       所以同样是 serializer 自己读 JSON 的 {@code keep_held_item} 键再写进来。</li>
 * </ul>
 */
public class HandMadeApplicationRecipe extends StandardProcessingRecipe<RecipeInput>
        implements HandMadeToolRecipeLike {

    /** 这条独占配方归属哪个工具；由 {@link HandMadeToolRecipeSerializer} 在 decode 后写入。 */
    @Nullable
    private HandMadeTool tool;

    /** 手持物品是否不消耗（等价于 Create 的 {@code toolNotConsumed}）。 */
    private boolean keepHeldItem;

    public HandMadeApplicationRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE, params);
    }

    /**
     * <b>2 槽判定</b>，与 {@code ItemApplicationRecipe.matches}
     * （{@code ItemApplicationRecipe.java:28-31}）一致：
     * <b>槽 0 = 被处理的目标物品</b>（置物台 / 传送带上的那个），
     * <b>槽 1 = 手持物品/工具</b>。两个都要匹配。
     *
     * <p>Create 那份直接调 {@code getProcessedItem()} / {@code getRequiredHeldItem()}，
     * 而这两个方法在原料不足时抛 {@link IllegalStateException}
     * （{@code ItemApplicationRecipe.java:47-57}）。这里改成显式守卫：原料不足或输入槽不足时
     * 返回 false（不匹配），而不是在配方匹配阶段炸掉。</p>
     */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (ingredients.size() < 2 || input.size() < 2) {
            return false;
        }
        return ingredients.get(0).test(input.getItem(0)) && ingredients.get(1).test(input.getItem(1));
    }

    /** 与 {@code ItemApplicationRecipe.getMaxInputCount()} 一致（{@code ItemApplicationRecipe.java:33-36}）。 */
    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    /** 与 {@code ItemApplicationRecipe.getMaxOutputCount()} 一致（{@code ItemApplicationRecipe.java:38-41}）。 */
    @Override
    protected int getMaxOutputCount() {
        return 4;
    }

    /**
     * 与 {@code ItemApplicationRecipe} 一致：它<b>没有</b>覆盖 {@code canSpecifyDuration()}，
     * 因此继承 {@code ProcessingRecipe} 的默认值 {@code false}
     * （{@code ProcessingRecipe.java:72-74}）。
     *
     * <p>显式写出来是为了把"应用独占配方不允许 {@code processing_time}"这条语义摆在明面上
     * （写了会在 {@code validate()} 里被拒绝），也避免将来 Create 改默认值时静默改变行为。</p>
     */
    @Override
    protected boolean canSpecifyDuration() {
        return false;
    }

    /** 对应的 Create 语义是 {@code ItemApplicationRecipe.shouldKeepHeldItem()}。 */
    public boolean shouldKeepHeldItem() {
        return keepHeldItem;
    }

    public void setKeepHeldItem(boolean keepHeldItem) {
        this.keepHeldItem = keepHeldItem;
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
