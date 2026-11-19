package com.alben.createhandmade.recipe;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.alben.createhandmade.compat.jei.category.sequenced.HandMadeAssemblySubCategory;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * L3 独占配方接入 Create **序列组装**（{@code create:sequenced_assembly}）的公共实现。
 *
 * <h2>为什么需要它</h2>
 * <p>Create 的 {@code SequencedRecipe#CODEC} 只接受同时满足两个条件的步骤配方：</p>
 * <ol>
 *   <li>{@code instanceof ProcessingRecipe} —— 我们的 6 个家族类都满足（它们继承
 *       Create 的 {@code StandardProcessingRecipe} / {@code BasinRecipe} /
 *       {@code AbstractCrushingRecipe}）；</li>
 *   <li>{@code instanceof IAssemblyRecipe} —— 此前<b>不</b>满足，所以整合包作者在
 *       {@code sequence} 里写 {@code create_hand_made:tool_recipe} 时，
 *       {@code SequencedRecipe.CODEC} 直接抛
 *       {@code "HandMadeXxxRecipe is not supported in Sequenced Assembly"}，
 *       整条序列组装配方加载失败。</li>
 * </ol>
 *
 * <h2>机器能不能执行我们的步骤：不能</h2>
 * <p>Create 的机器按「<b>自己的</b> type + 自己的配方类」查找序列组装步骤
 * （{@code SequencedAssemblyRecipe#getRecipe(level, inv, AllRecipeTypes.X.getType(), XRecipe.class)}），
 * 四台机器分别是：</p>
 * <ul>
 *   <li>部署器 {@code create:deploying} + {@code DeployerApplicationRecipe}</li>
 *   <li>机械压床 {@code create:pressing} + {@code PressingRecipe}</li>
 *   <li>机械锯 {@code create:cutting} + {@code CuttingRecipe}</li>
 *   <li>注液器 {@code create:filling} + {@code FillingRecipe}</li>
 * </ul>
 * <p>我们的 L3 配方 type 恒为 {@code create_hand_made:tool_recipe}，配方类也不是上述四个具体类
 * （见各家族类的 Javadoc），两道判定都过不了 —— 机器<b>永远不会</b>执行我们的步骤。
 * 这正是 L3「独占」的设计意图：机器读不到，只能手搓。</p>
 *
 * <p><b>必须知道的风险：</b>如果整合包作者把我们的配方写成序列组装的一步，又没有对应的手搓工具支持
 * （见 {@code PressHammerItem} / {@code HandSawItem} / {@code PointerItem} /
 * {@code InfusionGunItem} / {@code MortarItem} / {@code CrusherMortarItem} 里的
 * {@code SequencedAssemblyRecipe.getRecipe(...)} 调用），过渡物品会<b>原地停住</b>，
 * 不会自动前进，也不会报错。所以「能解析」不等于「能玩」。</p>
 *
 * <h2>supportsAssembly() 为什么必须是 true</h2>
 * <p>{@code SequencedRecipe#initFromSequencedAssembly} 会把本配方
 * {@code ingredients[0]} 替换成序列组装的<b>过渡物品</b>（第一步是「过渡物品 ∪ 起始原料」）。
 * 我们各家族的第 0 槽恰好都是「被加工的物品」，替换后 {@code matches()} 才会认过渡物品，
 * 手搓路径才能命中。返回 {@code false} 会让这一步永远匹配不上过渡物品。</p>
 */
public interface HandMadeAssemblyRecipe extends IAssemblyRecipe, HandMadeToolRecipeLike {

    /** 允许 Create 把 {@code ingredients[0]} 改写为过渡物品（见类注释）。 */
    @Override
    default boolean supportsAssembly() {
        return true;
    }

    /**
     * 序列组装 tooltip / JEI 里显示的「这一步要做什么」。
     *
     * <p>格式：{@code <工具>}，有额外需求时追加 {@code · <附加物/流体>} —— 例如
     * {@code 指杆 · 燧石}、{@code 灌注枪 · 250 mB 水}，让玩家拿着过渡物品就知道下一步要准备什么
     * （Create 自己的写法是 “deploying item: sandpaper” / “spout filling fluid: water”）。</p>
     *
     * <p>Create 的四个实现在这里都标了 {@code @OnlyIn(Dist.CLIENT)}；我们这里只用
     * {@link Component#translatable} 拼语言键，没有客户端类引用，因此不注解，
     * 保持 common 侧随时可调用。</p>
     */
    @Override
    default Component getDescriptionForAssembly() {
        Component detail = this instanceof ProcessingRecipe<?, ?> recipe
                ? HandMadeAssembly.stepDetail(recipe)
                : null;
        return HandMadeAssembly.stepDescription(getTool(), detail);
    }

    /** 「这一步需要的机器」＝归属工具物品（JEI 显示用）。 */
    @Override
    default void addRequiredMachines(Set<ItemLike> list) {
        ItemLike toolItem = HandMadeAssembly.toolItem(getTool());
        if (toolItem != null) {
            list.add(toolItem);
        }
    }

    /** 除第 0 槽（已被替换为过渡物品）之外的额外物品需求；默认没有。 */
    @Override
    default void addAssemblyIngredients(List<Ingredient> list) {
    }

    /** 额外流体需求；默认没有（注液家族自行覆盖）。 */
    @Override
    default void addAssemblyFluidIngredients(List<SizedFluidIngredient> list) {
    }

    /**
     * JEI 子类别：画一个「归属工具物品」的图标，表示这一步要靠手搓。
     *
     * <p>注意 {@code IAssemblyRecipe#getJEISubCategory()} 的<b>返回类型本身</b>就引用了
     * Create 的客户端类 {@code SequencedAssemblySubCategory} —— 这是 Create 自己的设计
     * （{@code PressingRecipe} 等 common 配方类同样如此），因此这里沿用同一模式：
     * 只有 JEI 在客户端调用时才会真正加载 {@link HandMadeAssemblySubCategory}。</p>
     */
    @Override
    default Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> HandMadeAssemblySubCategory::new;
    }
}
