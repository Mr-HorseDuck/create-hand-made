package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.compat.jei.display.BellowsCookingDisplay;
import com.alben.createhandmade.recipe.FanType;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
import com.alben.createhandmade.recipe.HandMadeFillingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipeFilters;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.content.fluids.transfer.FillingInput;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.recipe.CreateRecipe;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 本模组在 JEI 里的<b>唯一配方来源</b>。
 *
 * <p><b>禁止任何类别自己抓配方</b>：所有类别的收集器都必须经过这里，否则会出现
 * 「有的类别有配方、有的空着」这种最难查的静默失败。</p>
 *
 * <p><b>为什么不用 {@code Level.getRecipeManager()}：</b>26.1 起配方只在服务端可用
 * （见 {@code recipe/HandMadeRecipePool} 的 {@code recipeManager(...)}：
 * {@code level.isClientSide()} 时直接返回 {@code null}）。JEI 插件是纯客户端代码，
 * 能拿到的只有 JEI 自己在客户端维护的同步快照 ——
 * {@link Internal#getClientSyncedRecipes()}，它在没有任何数据时返回
 * {@link RecipeMap#EMPTY}。</p>
 *
 * <p><b>注意这个类属于两条「非公开」边界：</b></p>
 * <ul>
 *   <li>{@code mezz.jei.common.Internal} 是 JEI 的内部 API（不在 {@code mezz.jei.api} 下），
 *       未来小版本可能改签名 —— 所以这里只用到它的两个方法，改动只会落在本文件；</li>
 *   <li>{@link RecipeMap} 本身是 Minecraft 的类（{@code net.minecraft.world.item.crafting}），
 *       不是 JEI 的，所以 {@code -fabric-api} 薄壳给不了它，必须依赖完整 mod jar。</li>
 * </ul>
 *
 * <p><b>客户端版筛选语义</b>（对应服务端 {@code HandMadeRecipePool} 里的同名逻辑）：</p>
 * <ul>
 *   <li>L2 数据包过滤：{@link HandMadeRecipeFilters#isDisabled}（资源重载在客户端同样会跑）；</li>
 *   <li>切石家族额外过 {@link AllRecipeTypes#shouldIgnoreInAutomation}（照抄 26.1
 *       {@code BlockCuttingCategory.getRecipes}）；</li>
 *   <li>工作盆 / 应用家族（切片 3）<b>不</b>过 {@code AllRecipeTypes.CAN_BE_AUTOMATED}：
 *       服务端 {@code HandMadeRecipePool} 对这两族也不在池里过滤，automation 判定留在
 *       调用方（{@code PressHammerItem} / {@code PointerItem}，两处都有一句
 *       「配方池与 JEI 都不过滤这些『仅手动』配方」的注释）；</li>
 *   <li>风箱熔炼 / 烟熏家族（切片 4）额外过 {@link AllRecipeTypes#CAN_BE_AUTOMATED}（照抄 26.1
 *       {@code FanBlastingCategory} / {@code FanSmokingCategory}），<b>不</b>过 L2 过滤表 ——
 *       风箱不是 {@link HandMadeTool} 的枚举常量（它是 Create 的 {@code FanProcessingType} 一侧的
 *       工具，见 {@code HandMadeTool} 的类注释），没有 {@code HandMadeRecipeFilters} 过滤表；</li>
 *   <li>「哪些 L1 配方归哪个工具」由各分类传入的 {@code tool} 与 {@code keep} 谓词决定。</li>
 * </ul>
 *
 * <p>服务端还有 config 开关（{@code allowStonecuttingOnSaw} 等），客户端读不到
 * {@code AllConfigs.server()}（那是服务端配置对象），所以 JEI 这一层<b>不</b>重做 config 门：
 * 关掉开关的服主看到的是「类别里有配方但游戏里用不了」，这点记在 PLAN 的风险项里。</p>
 */
public final class JeiRecipeSource {

    private JeiRecipeSource() {
    }

    // ==================== 本模组 L3 独占配方类型 ====================

    /**
     * {@code create_hand_made:tool_recipe}（六家族共用的 L3 类型）。
     *
     * <p>类型参数在 JEI 这一层没有意义（同一个 type 下混装 6 种 record），所以统一收成
     * {@code RecipeType<?>}。写法照抄 {@code HandMadeRecipePool.recipesOfType(Level, RecipeType<?>)}
     * 的调用点：把泛型方法的结果直接赋给 {@code RecipeType<?>} 目标类型即可。</p>
     *
     * <p><b>为什么是方法而不是 {@code static final} 常量：</b>{@link HandMadeRecipeTypes#getType()}
     * 返回的字段在 {@code HandMadeRecipeTypes.register()} 里才被赋值。写成常量就等于在
     * {@code JeiRecipeSource} 类初始化那一刻把值抓死，一旦加载顺序变化（例如某个客户端类先碰到
     * 本类）就会永久拿到 {@code null}，表现为「L3 配方永远不在 JEI 里」这种静默失败。
     * 方法形式每次调用现取，彻底避开初始化顺序问题。</p>
     */
    public static RecipeType<?> l3Tool() {
        return HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }

    /** {@code create_hand_made:stonecutting_recipe}（手锯切石 L3）。 */
    public static RecipeType<?> l3Stonecutting() {
        return HandMadeRecipeTypes.STONECUTTING_RECIPE.getType();
    }

    /** {@code create_hand_made:bellows_recipe}（风箱 L3，四个 {@code fan_type} 共用）。 */
    public static RecipeType<?> l3Bellows() {
        return HandMadeRecipeTypes.BELLOWS_RECIPE.getType();
    }

    // ==================== 快照 ====================

    /**
     * 取客户端同步配方快照；取不到时返回 {@link RecipeMap#EMPTY}（不是 null）。
     *
     * <p>空结果在 JEI 里表现为「类别存在但没配方」，不报错也不崩溃，因此这里留一条
     * debug 日志，方便排查「为什么 JEI 里什么都没有」。</p>
     */
    public static RecipeMap recipeMap() {
        if (!Internal.hasClientSyncedRecipes()) {
            CreateHandMade.LOGGER.debug(
                    "JEI 客户端配方快照尚未同步（hasClientSyncedRecipes=false），本次按空配方处理");
            return RecipeMap.EMPTY;
        }
        RecipeMap map = Internal.getClientSyncedRecipes();
        if (map == null) {
            CreateHandMade.LOGGER.debug("JEI 客户端配方快照为 null，本次按空配方处理");
            return RecipeMap.EMPTY;
        }
        return map;
    }

    /**
     * 取某个配方类型的全部配方（已同步到客户端的那部分）。
     *
     * <p>返回不可变 List：{@code IRecipeRegistration.addRecipes} 收的是
     * {@code List}，而 {@code RecipeMap#byType} 给的是 {@code Collection}，
     * 这里统一转成不可变副本，避免下游有人就地改。</p>
     *
     * <p>泛型要两个参数是因为原版签名是
     * {@code <I extends RecipeInput, T extends Recipe<I>> byType(RecipeType<T>)}
     * —— 只看 {@code T} 推不出 {@code I}，必须显式带上。</p>
     */
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> of(
            RecipeType<T> type) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return List.of();
        }
        Collection<RecipeHolder<T>> recipes = map.byType(type);
        return recipes.isEmpty() ? List.of() : List.copyOf(recipes);
    }

    // ==================== 单输入多产物家族（切片 2） ====================

    /**
     * 单输入多产物家族的统一取数：把某个 {@link RecipeType} 下所有实现
     * {@link CreateSingleStackRollableRecipe} 的配方收进同一个列表。
     *
     * <p><b>为什么要归一化到共同父接口：</b>26.1 的 Create Fly 没有上游的
     * {@code AbstractCrushingRecipe}，L1 的 6 个 record 与 L3 的 4 个本模组 record
     * 唯一的共同父接口就是 {@code CreateSingleStackRollableRecipe}
     * （{@code ingredient()} + {@code results()}）。JEI 的 {@code IRecipeType<T>} 是不变型，
     * 所以类别只能用 {@code RecipeHolder<CreateSingleStackRollableRecipe>} 这一种显示类型，
     * 具体元素靠这里的 unchecked 转换塞进去（erasure 之后同一份字节码，安全）。</p>
     *
     * <p><b>为什么不直接调 {@code map.byType(...)}：</b>各家族的源类型泛型互不相同
     * （{@code RecipeType<CuttingRecipe>} / {@code RecipeType<MillingRecipe>} / 本模组的
     * {@code RecipeType<R>}），用 {@code byType} 每个都要单独写一遍双类型参数的推演；
     * 这里改成遍历 {@code RecipeMap.values()} 再比 {@code recipe.getType() == type}，
     * 与 {@code HandMadeRecipePool.recipesOfType} 的做法一致，也彻底避开泛型推演。
     * 代价是全表扫描一次 —— 每个类别注册时只跑一次，可以接受。</p>
     *
     * @param type 数据源类型（Create 的 {@code AllRecipeTypes.X} 或本模组 L3 类型）
     * @param tool L2 过滤用的工具；传 {@code null} 表示该家族没有 L2 过滤入口（风箱）
     * @param keep 额外的显示条件（例如 L3 风箱按 {@code fan_type} 分流）；传 {@code null} 表示全收
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void appendRollable(List<RecipeHolder<CreateSingleStackRollableRecipe>> target,
                                      RecipeType<?> type, HandMadeTool tool,
                                      Predicate<CreateSingleStackRollableRecipe> keep) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return;
        }
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof CreateSingleStackRollableRecipe recipe)) {
                continue;
            }
            if (tool != null && HandMadeRecipeFilters.isDisabled(tool, holder.id().identifier())) {
                continue;
            }
            if (keep != null && !keep.test(recipe)) {
                continue;
            }
            target.add((RecipeHolder) holder);
        }
    }

    /**
     * 只取一个类型的便捷版（单数据源家族用：手锯、研钵、冲压锤）。
     *
     * <p>注意：<b>不要</b>在多个数据源要合并时用它再 {@code addAll}，那样每次都会重扫一遍
     * 快照；合并场景请自行建 List 后连续调 {@link #appendRollable}。</p>
     */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> rollable(
            RecipeType<?> type, HandMadeTool tool) {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        appendRollable(list, type, tool, null);
        return list;
    }

    /**
     * 手锯切石家族的原始条目：原版 {@code minecraft:stonecutting} 在前、本模组
     * {@code create_hand_made:stonecutting_recipe} 在后。
     *
     * <p>这里只负责「取数 + 筛选」，<b>不</b>负责折叠 —— 折叠（同一输入的多个产物并进
     * 一个 5×3 的槽位组、超过 15 条时按模 15 并入同槽）是显示层的事，写在
     * {@code HandSawStonecuttingCategory} 里，照抄 26.1 {@code BlockCuttingCategory.getRecipes}。</p>
     */
    public static List<StonecuttingEntry> stonecutting() {
        List<StonecuttingEntry> list = new ArrayList<>();
        appendStonecutting(list, RecipeType.STONECUTTING);
        appendStonecutting(list, l3Stonecutting());
        return list;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void appendStonecutting(List<StonecuttingEntry> target, RecipeType<?> type) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return;
        }
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            // 与 26.1 BlockCuttingCategory.getRecipes 一致：被标记为「不可自动化」的切石配方不进 JEI。
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) {
                continue;
            }
            if (!(holder.value() instanceof SingleItemRecipe recipe)) {
                continue;
            }
            // L2：手锯切石是独立的一份过滤表（与手锯切削分开，见 HandMadeTool 的枚举注释）。
            if (HandMadeRecipeFilters.isDisabled(HandMadeTool.HAND_SAW_STONECUTTING,
                    holder.id().identifier())) {
                continue;
            }
            // SingleItemRecipe#result() 在 26.1 是 protected（javap 实测），跨包取不到，
            // 所以走 public 的 assemble(SingleRecipeInput) —— 它内部就是 result.create()。
            ItemStack stack = recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
            if (stack.isEmpty()) {
                continue;
            }
            target.add(new StonecuttingEntry(holder.id().identifier(), recipe.input(),
                    ItemStackTemplate.fromNonEmptyStack(stack)));
        }
    }

    /**
     * 折叠前的原始条目：一条切石配方 = 一个输入 + 一个产出。
     *
     * @param id     配方 id（折叠后取同组第一条的 id 当显示 UID）
     * @param input  输入（原版与 L3 都是 {@link Ingredient}）
     * @param result 产出（26.1 配方层是 {@link ItemStackTemplate}）
     */
    public record StonecuttingEntry(Identifier id, Ingredient input, ItemStackTemplate result) {
    }

    // ==================== 工作盆 / 应用家族（切片 3） ====================

    /**
     * 工作盆家族的取数：把某个 {@link RecipeType} 下所有实现 {@link BasinRecipe} 的配方
     * 收进同一个列表（冲压锤工作盆 / 搅拌杖混合 / 搅拌杖酿造共用）。
     *
     * <p><b>为什么能直接按接口取：</b>26.1 的 L3 {@code HandMadeToolRecipe} 已经
     * {@code implements BasinRecipe}（PLAN F3 表），所以 L1 与 L3 之间<b>不需要</b>上游那套
     * {@code wrapAsBasin} 显示代理 —— 一个 {@code Predicate<BasinRecipe>} 就能按
     * {@code tool()} 字段把 L3 分流到各家族。</p>
     *
     * <p><b>注意 {@link BasinRecipe} 上没有 {@code results()} / {@code fluidResults()}：</b>
     * 这两个 accessor 由各具体 record 自己声明（{@code javap -p} 实测：接口只声明
     * {@code getIngredientSize/ingredients/fluidIngredients/heat/apply}）。Create 自己的
     * {@code CompactingCategory / MixingCategory} 之所以能直接调，是因为它们的类型参数是
     * <b>具体 record</b>；本模组的显示类型必须是接口（L3 既不是 CompactingRecipe 也不是
     * MixingRecipe），所以产物列表统一走 {@code BasinSlots} 取。</p>
     *
     * @param type 数据源类型（{@code AllRecipeTypes.COMPACTING/MIXING/POTION} 或 L3 类型）
     * @param tool L2 过滤用的工具；传 {@code null} 表示不过滤
     * @param keep 额外条件（例如 L3 按 {@code tool} 分流）；传 {@code null} 表示全收
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void appendBasin(List<RecipeHolder<BasinRecipe>> target, RecipeType<?> type,
                                   HandMadeTool tool, Predicate<BasinRecipe> keep) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return;
        }
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof BasinRecipe recipe)) {
                continue;
            }
            if (tool != null && HandMadeRecipeFilters.isDisabled(tool, holder.id().identifier())) {
                continue;
            }
            if (keep != null && !keep.test(recipe)) {
                continue;
            }
            target.add((RecipeHolder) holder);
        }
    }

    /**
     * 应用家族（指杆）的取数：{@link ItemApplicationRecipe} 在 26.1 是<b>接口</b>
     * （{@code DeployerApplicationRecipe} / {@code ManualApplicationRecipe} /
     * 本模组 L3 {@code HandMadeApplicationRecipe} 都 implements 它），所以和
     * {@link #appendBasin} 一样按接口归一，无需任何代理。
     *
     * <p>与 Create 的 {@code DeployingCategory.getRecipes} 一致：DEPLOYING 在前、
     * ITEM_APPLICATION 在后（游戏内 {@code PointerItem.findRecipe} 也是这个优先级），
     * L3 配方的归属工具是 {@code HandMadeTool.POINTER}。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void appendApplication(List<RecipeHolder<ItemApplicationRecipe>> target,
                                         RecipeType<?> type, HandMadeTool tool,
                                         Predicate<ItemApplicationRecipe> keep) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return;
        }
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof ItemApplicationRecipe recipe)) {
                continue;
            }
            if (tool != null && HandMadeRecipeFilters.isDisabled(tool, holder.id().identifier())) {
                continue;
            }
            if (keep != null && !keep.test(recipe)) {
                continue;
            }
            target.add((RecipeHolder) holder);
        }
    }

    // ==================== 归一化家族（切片 4） ====================

    /**
     * 风箱熔炼（{@link FanType#BLASTING}）与风箱烟熏（{@link FanType#SMOKING}）的归一化取数。
     *
     * <p><b>去重规则逐句照抄 26.1 的 {@code FanBlastingCategory.getRecipes / addRecipe}</b>
     * （{@code javap -p -c} 核对，指令级对照）：</p>
     * <ol>
     *   <li>{@code BLASTING} 里的条目：若<b>存在同输入的 {@code SMOKING} 配方</b>（且那条可自动化），
     *       则排除；</li>
     *   <li>{@code SMELTING} 里的条目：若<b>存在同输入的 {@code BLASTING} 或 {@code SMOKING} 配方</b>
     *       （且可自动化），则排除；</li>
     *   <li>「同输入」用 {@code new SingleRecipeInput(取原料的第一件物品)} +
     *       {@code recipe.matches(input, level)} 判定（26.1 已没有上游的
     *       {@code CreateJEI.doInputsMatch}）；</li>
     *   <li>自身与「被找到的那条」都要过 {@link AllRecipeTypes#CAN_BE_AUTOMATED}。</li>
     * </ol>
     *
     * <p><b>顺序</b>也与 Create 一致：{@code BLASTING} 全量在前、{@code SMELTING} 在后；
     * 烟熏类别（{@code FanSmokingCategory.getRecipes}）只扫 {@code SMOKING}、不做去重。
     * 两侧的 {@code SingleItemRecipe} 都会被转成 {@link BellowsCookingDisplay}（单输入 + 单产物）。</p>
     *
     * <p><b>本模组 L3 追加在最后</b>：{@code create_hand_made:bellows_recipe} 里
     * {@code fan_type} 与参数相同的那一支，多产物只取第一个（烹饪语义）。
     * L3 不参与上面的去重 —— 它是一条独立的「手工风箱专属」配方，游戏内
     * {@code BellowsItem.findL3BellowsRecipe} 也是在 Create 的鼓风链路之外单独查它的。</p>
     *
     * <p><b>没有 level 时</b>（客户端快照已到但还没进世界）：没法做 {@code matches} 去重，
     * 于是<b>跳过去重、照常显示</b>（显示的输入是 {@link Ingredient} 本身，不需要物品栈）——
     * 宁可多显示几条重复的，也不要返回空列表：JEI 里的空类别是最难查的静默失败。</p>
     *
     * @param fanType 只取这个鼓风类型的 L3 配方，并据此决定扫哪几个原版烹饪类型
     */
    public static List<BellowsCookingDisplay> bellowsCooking(FanType fanType) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return List.of();
        }

        List<RecipeHolder<?>> blasting = singleItemRecipes(map, RecipeType.BLASTING);
        List<RecipeHolder<?>> smoking = singleItemRecipes(map, RecipeType.SMOKING);

        ClientLevel level = Minecraft.getInstance().level;
        ContextMap context = level == null ? null : SlotDisplayContext.fromLevel(level);

        List<BellowsCookingDisplay> list = new ArrayList<>();
        if (fanType == FanType.BLASTING) {
            for (RecipeHolder<?> holder : blasting) {
                addCooking(list, holder, level, context, null, smoking);
            }
            for (RecipeHolder<?> holder : singleItemRecipes(map, RecipeType.SMELTING)) {
                addCooking(list, holder, level, context, blasting, smoking);
            }
        } else if (fanType == FanType.SMOKING) {
            for (RecipeHolder<?> holder : smoking) {
                addCooking(list, holder, level, context, null, null);
            }
        }
        appendBellowsCookingL3(list, map, fanType);
        return list;
    }

    /** 取某个原版烹饪类型下的全部 {@code SingleItemRecipe}（沿用本文件「遍历 values + 比类型」的写法）。 */
    private static List<RecipeHolder<?>> singleItemRecipes(RecipeMap map, RecipeType<?> type) {
        List<RecipeHolder<?>> list = new ArrayList<>();
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof SingleItemRecipe)) {
                continue;
            }
            list.add(holder);
        }
        return list;
    }

    /**
     * 一条原版烹饪配方 → 一条显示配方（对应 26.1 {@code FanBlastingCategory.addRecipe} 的 7 步）。
     *
     * @param blastingPool 需要排除的 {@code BLASTING} 池；{@code null} 表示不按它去重
     * @param smokingPool  需要排除的 {@code SMOKING} 池；{@code null} 表示不按它去重
     */
    private static void addCooking(List<BellowsCookingDisplay> target, RecipeHolder<?> holder,
                                   @Nullable ClientLevel level, @Nullable ContextMap context,
                                   @Nullable Collection<RecipeHolder<?>> blastingPool,
                                   @Nullable Collection<RecipeHolder<?>> smokingPool) {
        // ① 配方自身必须可自动化（CREATE 的 AUTOMATION_IGNORE_TAG）。
        if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
            return;
        }
        if (!(holder.value() instanceof SingleItemRecipe recipe)) {
            return;
        }
        // ②③④ 同输入去重（需要 level + ContextMap；没有就跳过 —— 见方法注释）。
        //      注意输入物品栈<b>只用于去重</b>，显示的输入是 Ingredient 本身，
        //      所以解析不出来的时候只需要放弃去重，不需要放弃这条配方。
        if (level != null && context != null && (blastingPool != null || smokingPool != null)) {
            ItemStack inputStack = resolveInputStack(recipe.input(), context);
            if (inputStack.isEmpty()) {
                return;
            }
            SingleRecipeInput input = new SingleRecipeInput(inputStack);
            if (blastingPool != null && hasMatching(blastingPool, input, level)) {
                return;
            }
            if (smokingPool != null && hasMatching(smokingPool, input, level)) {
                return;
            }
        }
        // ⑤ 单产物：SingleItemRecipe#result() 在 26.1 是 protected（跨包取不到），
        //    与 appendStonecutting 一样走 public 的 assemble(...)（其实现就是 result.create()）。
        ItemStack output = recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        if (output.isEmpty()) {
            return;
        }
        target.add(new BellowsCookingDisplay(holder.id().identifier(), recipe.input(),
                ItemStackTemplate.fromNonEmptyStack(output)));
    }

    /**
     * 池子里是否存在「同输入且可自动化」的配方。
     *
     * <p>等价于 26.1 的 {@code pool.stream().filter(h -> ((XxxRecipe) h.value()).matches(input, level))
     * .findFirst().filter(CAN_BE_AUTOMATED).isPresent()}：先找到第一条匹配的，再要求它可自动化。</p>
     */
    private static boolean hasMatching(Collection<RecipeHolder<?>> pool, SingleRecipeInput input,
                                       ClientLevel level) {
        for (RecipeHolder<?> candidate : pool) {
            if (!(candidate.value() instanceof SingleItemRecipe recipe)) {
                continue;
            }
            if (recipe.matches(input, level) && AllRecipeTypes.CAN_BE_AUTOMATED.test(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 {@link Ingredient} 解析出「第一件物品」（只用于拼 {@link SingleRecipeInput} 做 matches）。
     *
     * <p>26.1 的 {@code Ingredient} implements {@code FabricIngredient}，自定义原料
     * （{@code DefaultCustomIngredients.components(...)} 之类）没有普通条目，
     * 只能靠 {@code display().resolveForFirstStack(context)} 解析 —— Create 的
     * {@code FanBlastingCategory.addRecipe} 也是这么分支的（字节码里是
     * {@code getCustomIngredient()} 判空后走 {@code customIngredient.display()}）。
     * 这里用统一的 {@code display()} 入口，少一个分支也少一处 Fabric API 依赖。</p>
     *
     * <p>不用 {@code Ingredient#items()}：26.1 已把它标为 deprecated
     * （{@code -Xlint:deprecation} 实测）。</p>
     */
    private static ItemStack resolveInputStack(Ingredient ingredient, ContextMap context) {
        return ingredient.display().resolveForFirstStack(context);
    }

    /** 本模组 L3 {@code bellows_recipe} 里 {@code fan_type} 匹配的那一支（多产物只取第一个）。 */
    private static void appendBellowsCookingL3(List<BellowsCookingDisplay> target, RecipeMap map,
                                               FanType fanType) {
        RecipeType<?> type = l3Bellows();
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) {
                continue;
            }
            if (recipe.fanType() != fanType) {
                continue;
            }
            List<ProcessingOutput> results = recipe.results();
            if (results.isEmpty()) {
                continue;
            }
            // 烹饪语义只有单产物：多产物时只取第一个。概率（chance）在这一层被抹掉 ——
            // 显示类型里的产物槽只有一个，概率仍留在配方 JSON 里（与 AbstractCookingRecipe 一样）。
            ItemStack stack = results.getFirst().create();
            if (stack.isEmpty()) {
                continue;
            }
            target.add(new BellowsCookingDisplay(holder.id().identifier(), recipe.ingredient(),
                    ItemStackTemplate.fromNonEmptyStack(stack)));
        }
    }

    /**
     * 灌注枪（#6）的取数：把某个 {@link RecipeType} 下所有实现
     * {@link CreateRecipe CreateRecipe&lt;FillingInput&gt;} 的配方收进同一个列表。
     *
     * <p><b>为什么按接口归一：</b>26.1 的 {@code FillingRecipe}（L1）与本模组 L3
     * {@code HandMadeFillingRecipe} 都 {@code implements CreateRecipe<FillingInput>}（{@code javap} 实测），
     * 所以显示类型绑这个接口即可，不需要上游那种「把 L3 包装成 FillingRecipe」的代理。
     * 三个 accessor（{@code result() / ingredient() / fluidIngredient()}）是各自 record 的分量、
     * 不在接口上，分派写在 {@code HandInfusionGunCategory} 里。</p>
     *
     * @param type 数据源类型（{@code AllRecipeTypes.FILLING} 或本模组 L3 类型）
     * @param tool L2 过滤用的工具；传 {@code null} 表示不过滤
     * @param keep 额外条件（例如 L3 按 {@code tool} 分流）；传 {@code null} 表示全收
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void appendFilling(List<RecipeHolder<CreateRecipe<FillingInput>>> target,
                                     RecipeType<?> type, HandMadeTool tool,
                                     Predicate<CreateRecipe<FillingInput>> keep) {
        RecipeMap map = recipeMap();
        if (map == RecipeMap.EMPTY) {
            return;
        }
        for (RecipeHolder<?> holder : map.values()) {
            if (holder.value().getType() != type) {
                continue;
            }
            if (!(holder.value() instanceof CreateRecipe<?> recipe)) {
                continue;
            }
            if (tool != null && HandMadeRecipeFilters.isDisabled(tool, holder.id().identifier())) {
                continue;
            }
            if (keep != null && !keep.test((CreateRecipe<FillingInput>) recipe)) {
                continue;
            }
            target.add((RecipeHolder) holder);
        }
    }
}
