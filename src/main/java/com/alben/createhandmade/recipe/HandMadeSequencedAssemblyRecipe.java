package com.alben.createhandmade.recipe;

import java.util.List;
import java.util.Optional;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;

/**
 * 手搓专属序列组装（{@code create_hand_made:hand_sequenced_assembly}）。
 *
 * <h2>为什么不直接注册一个 type 复用原版配方</h2>
 * <p>Create 的 {@link SequencedAssemblyRecipe} 把 type 写死在 {@code getType()} 里
 * （返回 {@code AllRecipeTypes.SEQUENCED_ASSEMBLY}），并且它的三个静态查询入口
 * （{@code getRecipe(Level, ItemStack, RecipeType, Class)} 等）内部都用
 * <b>Create 自己的 type</b> 去 {@code getAllRecipesFor(...)}。所以「只换注册的 RecipeType」
 * 是做不到的：配方管理器里同一个 {@code Recipe} 实例的 {@code getType()} 决定它挂在哪个桶里，
 * 而查询侧也写死了桶名。</p>
 *
 * <p>因此本类<b>继承</b>原版配方以复用其数据结构（{@code ingredient / sequence / loops /
 * transitionalItem / resultPool} 都是 {@code protected}）、语言键与
 * {@code SequencedAssemblyRecipe.addToTooltip} 的进度提示，但把三处「私有逻辑」
 * （步进 {@code getStep} / 命中判定 {@code appliesTo} / 前进 {@code advance} + 结果抽取
 * {@code rollResult}）在子类里重新实现 —— 它们原本都是 {@code private}，无法复用。</p>
 *
 * <h2>过渡物品机制</h2>
 * <p><b>直接复用原版的 DataComponent</b>：Create 6.x 的进度本来就不是 NBT，而是
 * {@code AllDataComponents.SEQUENCED_ASSEMBLY} 上的一个 record
 * {@code SequencedAssembly(id, step, progress)}。我们沿用同一个组件带来两个好处：</p>
 * <ul>
 *   <li>Create 注册的进度 tooltip 处理器会照常显示「第 N / M 步」；</li>
 *   <li>原版机器<b>拿不到</b>它：机器的 {@code SequencedAssemblyRecipe.getRecipe(...)} 只遍历
 *       {@code create:sequenced_assembly} 桶，并按 {@code 组件里的 id == 该配方 id} 过滤，
 *       我们的 id 不在那个桶里，构不成命中。</li>
 * </ul>
 *
 * <h2>步骤约束</h2>
 * <p>步骤仍然必须是「{@code ProcessingRecipe} + {@code IAssemblyRecipe}」
 * （{@code SequencedRecipe.CODEC} 的硬性要求），本模组的 L3 家族类
 * （{@link HandMadeAssemblyRecipe} 的实现）天然满足。serializer 会额外校验
 * 步骤类型，把「写了 Create 的步骤」这种错误在加载期就报出来，而不是运行时卡住。</p>
 */
public class HandMadeSequencedAssemblyRecipe extends SequencedAssemblyRecipe {

    /** 本类自己的 serializer（父类那个字段的类型是 Create 的 serializer，不能放我们的）。 */
    private final HandMadeSequencedAssemblyRecipeSerializer handSerializer;

    public HandMadeSequencedAssemblyRecipe(HandMadeSequencedAssemblyRecipeSerializer handSerializer) {
        // 父类构造要求一个 Create 的 serializer 实例；我们重写 getSerializer()，
        // 这个值只用于满足父类字段，不会被当作真正的 serializer 使用。
        super(sequencedAssemblySerializerRef());
        this.handSerializer = handSerializer;
    }

    private static SequencedAssemblyRecipeSerializer sequencedAssemblySerializerRef() {
        return AllRecipeTypes.SEQUENCED_ASSEMBLY.getSerializer();
    }

    /** 本模组自己的配方类型（泛型版，方便 {@code getAllRecipesFor} / {@code getRecipeFor}）。 */
    @SuppressWarnings("unchecked")
    public static RecipeType<HandMadeSequencedAssemblyRecipe> recipeType() {
        return (RecipeType<HandMadeSequencedAssemblyRecipe>) (RecipeType<?>) HandMadeRecipeTypes
                .HAND_SEQUENCED_ASSEMBLY.getType();
    }

    @Override
    public RecipeType<?> getType() {
        return recipeType();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return handSerializer;
    }

    // ==================== 解码后的装配（由 serializer 调用） ====================

    /**
     * 把 serializer 解出来的字段写进父类的 protected 字段，并完成「步骤继承序列组装上下文」。
     *
     * <p>父类的对应逻辑在 {@code SequencedRecipe#initFromSequencedAssembly} 里，
     * 但那个方法是 <b>包私有</b>，子类/外部包调不到，所以在 {@link #initStep} 里重写一遍。</p>
     */
    void applyDecoded(Ingredient ingredient, List<SequencedRecipe<?>> steps,
                      ProcessingOutput transitionalItem, List<ProcessingOutput> results, int loops,
                      boolean initSteps) {
        this.ingredient = ingredient;
        this.transitionalItem = transitionalItem;
        this.sequence = steps;
        this.resultPool.clear();
        this.resultPool.addAll(results);
        this.loops = loops;
        if (!initSteps) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            initStep(steps.get(i), i == 0);
        }
    }

    /**
     * 把一步的 {@code ingredients[0]} 换成过渡物品（首步是「过渡物品 ∪ 起始原料」）。
     *
     * <p>语义与 Create 完全一致（{@code SequencedRecipe#initFromSequencedAssembly}）：
     * 只有 {@link HandMadeAssemblyRecipe#supportsAssembly()} 为真时才改写，否则保留原样。</p>
     */
    private void initStep(SequencedRecipe<?> step, boolean isFirst) {
        if (!(step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe assembly)) {
            return;
        }
        if (!assembly.supportsAssembly()) {
            return;
        }
        Ingredient transit = Ingredient.of(getTransitionalItem());
        step.getRecipe()
                .getIngredients()
                .set(0, isFirst ? CompoundIngredient.of(transit, getIngredient()) : transit);
    }

    /** serializer 侧读用：过渡物品的完整 {@link ProcessingOutput}（父类字段是 protected）。 */
    ProcessingOutput transitionalOutput() {
        return transitionalItem;
    }

    // ==================== 步进逻辑（父类里都是 private，这里重新实现） ====================

    /** 当前进度（0 起）。没有组件即第 0 步。 */
    public int getStepFor(ItemStack input) {
        if (!input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return 0;
        }
        //noinspection DataFlowIssue
        return input.get(AllDataComponents.SEQUENCED_ASSEMBLY)
                .step();
    }

    /** 这个物品是否属于本食谱（已在流程中 → 看 id；否则看起始原料）。 */
    public boolean appliesTo(ResourceLocation id, ItemStack input) {
        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            //noinspection DataFlowIssue
            return getTransitionalItem().getItem() == input.getItem()
                    && input.get(AllDataComponents.SEQUENCED_ASSEMBLY)
                            .id()
                            .equals(id);
        }
        return getIngredient().test(input);
    }

    /** 下一个要执行的步骤。 */
    public SequencedRecipe<?> getNextStep(ItemStack input) {
        return getSequence().get(getStepFor(input) % Math.max(1, getSequence().size()));
    }

    /**
     * 前进一格：返回「带着新进度的过渡物品」；走完最后一圈则从结果池里抽产物。
     *
     * <p>与 Create 的 {@code advance} 等价（含 {@code (step + 1) / size >= loops} 的收尾判定）。</p>
     */
    public ItemStack advance(ResourceLocation id, ItemStack input, RandomSource random) {
        int step = getStepFor(input);
        int size = getSequence().size();
        int loops = getLoops();
        if (size <= 0) {
            return input.copy();
        }
        if ((step + 1) / size >= loops) {
            return rollResult(random);
        }
        ItemStack advanced = getTransitionalItem().copyWithCount(1);
        advanced.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                new SequencedAssemblyRecipe.SequencedAssembly(id, step + 1, (step + 1f) / (size * loops)));
        return advanced;
    }

    /** 结果池的加权随机（与 Create 的私有 {@code rollResult} 一致）。 */
    private ItemStack rollResult(RandomSource random) {
        float totalWeight = 0;
        for (ProcessingOutput entry : resultPool) {
            totalWeight += entry.getChance();
        }
        float number = random.nextFloat() * totalWeight;
        for (ProcessingOutput entry : resultPool) {
            number -= entry.getChance();
            if (number < 0) {
                return entry.getStack()
                        .copy();
            }
        }
        return ItemStack.EMPTY;
    }

    // ==================== 查询入口（供手搓工具使用） ====================

    /**
     * 找出「这个物品当前该做的那一步」，并把「前进后的产物」强制设为第 0 个产物。
     *
     * <p>做法与 Create 的
     * {@code SequencedAssemblyRecipe#getRecipe(Level, ItemStack, RecipeType, Class)} 完全对称，
     * 区别只是遍历 {@link #recipeType()} 这个桶（原版遍历 Create 的桶）。</p>
     *
     * @param recipeClass 期望的步骤配方类（本模组某个 L3 家族类）
     * @return 命中时返回「步骤配方」的 holder（其 {@code rollResults()} 会产出前进后的过渡物品）
     */
    public static <R extends ProcessingRecipe<?, ?>> Optional<RecipeHolder<R>> getStepRecipe(
            Level level, ItemStack item, Class<R> recipeClass) {
        List<RecipeHolder<HandMadeSequencedAssemblyRecipe>> all =
                level.getRecipeManager().getAllRecipesFor(recipeType());

        for (RecipeHolder<HandMadeSequencedAssemblyRecipe> holder : all) {
            if (!holder.value().appliesTo(holder.id(), item)) {
                continue;
            }
            SequencedRecipe<?> next = holder.value().getNextStep(item);
            ProcessingRecipe<?, ?> step = next.getRecipe();
            if (!recipeClass.isInstance(step)) {
                continue;
            }
            step.enforceNextResult(
                    () -> holder.value().advance(holder.id(), item, level.random));
            return Optional.of(new RecipeHolder<>(holder.id(), recipeClass.cast(step)));
        }
        return Optional.empty();
    }

    /** 该物品是否正处在我们的手搓流水线上（用于 tooltip / 调试）。 */
    public static boolean isHandMadeAssemblyItem(Level level, ItemStack item) {
        if (!item.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return false;
        }
        //noinspection DataFlowIssue
        ResourceLocation id = item.get(AllDataComponents.SEQUENCED_ASSEMBLY)
                .id();
        return level.getRecipeManager()
                .byKey(id)
                .map(h -> h.value() instanceof HandMadeSequencedAssemblyRecipe)
                .orElse(false);
    }
}
