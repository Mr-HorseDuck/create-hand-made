package com.alben.createhandmade.recipe;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.potion.PotionMixingRecipes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.utility.RecipeGenericsUtil;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 手工工具的「基础配方池」——游戏内工具与 JEI 类别的统一配方来源。
 *
 * <p>重构前的状况：每个工具在自己的 {@code findXxxRecipe} 里独立查询 Create 配方，
 * JEI 又在 {@code CreateHandMadeJEI} 里用另一套写法独立收集一遍，两边容易走偏，
 * 也没有任何集中过滤的入口。本类把「收集候选配方」这一步收拢成唯一入口。</p>
 *
 * <p><b>职责边界：</b>本类只负责「收集候选列表」，<b>不做任何 input 匹配</b>。
 * 谁调用谁负责拿 {@code RecipeHolder#value()} 去和自己手上的容器/原料做匹配。
 * 这样 JEI（只展示）和游戏内工具（要匹配）可以共用同一份候选集。</p>
 *
 * <p><b>硬性约束：</b>本类及其依赖不允许 import 任何 {@code mezz.jei.*}，
 * 也不引用 Create 的 JEI compat 类（{@code com.simibubi.create.compat.jei.*}），
 * 因为游戏内工具会在<b>服务端</b>调用它，而 Create 的 JEI compat 类是纯客户端的
 * （{@code CreateJEI.getTypedRecipes} 走 {@code Minecraft.getInstance().getConnection()}）。
 * 所以这里统一用 {@code level.getRecipeManager().getAllRecipesFor(type)} 取配方，
 * 它与 {@code CreateJEI.getTypedRecipes(type)} 的数据来源和顺序完全一致，
 * 但服务端可用，也让本类在没有装 JEI 的环境下依然能正常工作。</p>
 *
 * <p>调用流程固定为两段：</p>
 * <pre>
 * 收集（本类私有方法） -> {@link HandMadeRecipeRegistry#appendExclusive} 做加法 -> 数据包禁用过滤
 * </pre>
 *
 * <p><b>TODO（未来的架构简化）：</b>考虑把
 * {@link HandMadeTool#PRESS_HAMMER_BASIN} / {@link HandMadeTool#PRESS_HAMMER_AUTO_SQUARE}
 * （以及 {@link HandMadeTool#STIRRING_STAFF} / {@link HandMadeTool#STIRRING_STAFF_AUTO_SHAPELESS}）
 * 合并成单一枚举条目：那样游戏内只需调一次就能拿到全局顺序的合并列表，JEI 侧按配方类型自行拆分。
 * 届时 {@link #mergeInGlobalOrder} 即可移除。</p>
 */
public final class HandMadeRecipePool {

    private HandMadeRecipePool() {
    }

    /**
     * 取某个「工具 + 配方类型」的基础配方列表。
     *
     * <p>返回的列表是新建的可变 {@link ArrayList}，调用方可以自由加工。
     * 列表里的 {@link RecipeHolder} 就是世界配方管理器里的原始对象（不做包装），
     * 以便调用方保留配方 id、原始配方类型等身份信息。</p>
     *
     * <p><b>顺序约定</b>（调用方依赖它来保持优先级）：</p>
     * <ul>
     *   <li>{@link HandMadeTool#CRUSHER_MORTAR}：CRUSHING 在前，MILLING 在后；</li>
     *   <li>{@link HandMadeTool#POINTER}：DEPLOYING 在前，ITEM_APPLICATION 在后；</li>
     *   <li>其余：保持配方管理器给出的相对顺序。</li>
     * </ul>
     *
     * @param tool  工具 + 配方类型组合
     * @param level 当前世界；为 null（例如 JEI 在进入世界前回调）时返回空列表
     * @return 独占追加、数据包禁用之后的最终基础配方列表
     */
    public static List<RecipeHolder<?>> getBaseRecipes(HandMadeTool tool, Level level) {
        // level 为 null 时没有任何配方可查，但流程仍然走完，保持行为可预测。
        List<RecipeHolder<?>> base = level == null
                ? new ArrayList<>()
                : switch (tool) {
                    case PRESS_HAMMER_BASIN -> collectPressHammerBasin(level);
                    case PRESS_HAMMER_AUTO_SQUARE -> collectPressHammerAutoSquare(level);
                    case PRESS_HAMMER_DEPOT -> collectPressHammerDepot(level);
                    case MORTAR -> collectMortar(level);
                    case CRUSHER_MORTAR -> collectCrusherMortar(level);
                    case HAND_SAW -> collectHandSaw(level);
                    case HAND_SAW_STONECUTTING -> collectHandSawStonecutting(level);
                    case STIRRING_STAFF -> collectStirringStaff(level);
                    case STIRRING_STAFF_AUTO_SHAPELESS -> collectStirringStaffAutoShapeless(level);
                    case STIRRING_STAFF_AUTO_BREWING -> collectStirringStaffAutoBrewing(level);
                    case POINTER -> collectPointer(level);
                    case INFUSION_GUN -> collectInfusionGun(level);
                };

        // L3：独占配方（create_hand_made:tool_recipe）。与 Create 类型分开收集、按 tool 字段分派。
        // 放在 appendExclusive 之前，保证 appendExclusive 仍是「最后做加法」的那一步；
        // 放在 L2 的 removeIf 之前，保证独占配方同样受数据包 / KubeJS 过滤管辖。
        if (level != null) {
            collectExclusiveRecipes(tool, level, base);
        }

        base = HandMadeRecipeRegistry.appendExclusive(tool, base);

        // L2：数据包过滤层。放在最后一步，所以连独占追加进来的配方也能被数据包禁用。
        // 只影响本模组工具的读取 —— 被禁用的配方仍完整留在 RecipeManager 里，Create 机器照常可用。
        base.removeIf(holder -> HandMadeRecipeFilters.isDisabled(tool, holder.id()));

        return base;
    }

    /**
     * 把若干个子集按<b>配方管理器的全局顺序</b>重新合并成一个列表。
     *
     * <p><b>为什么需要它：</b>工作盆那条路径的候选集在语义上是
     * 「COMPACTING 配方 ∪ 可压缩工作台配方」，而旧代码是用
     * {@code RecipeFinder.get(level, predicate)} 在全局配方顺序上一次性筛出来的。
     * 重构后这两类分别由 {@link HandMadeTool#PRESS_HAMMER_BASIN} 与
     * {@link HandMadeTool#PRESS_HAMMER_AUTO_SQUARE} 提供（JEI 需要它们分开成两个类别），
     * 如果直接首尾拼接，顺序就变成「先 COMPACTING 再 AUTO_SQUARE」，
     * 于是当两类同时匹配同一盆内容时（典型例子：9 铁锭 → 铁块，
     * Create 的 COMPACTING 与原版 9 合 1 工作台配方是两条不同配方）会偏向 COMPACTING，
     * 与重构前由全局顺序决定的结果可能不一致。</p>
     *
     * <p>搅拌杖的 {@link HandMadeTool#STIRRING_STAFF} +
     * {@link HandMadeTool#STIRRING_STAFF_AUTO_SHAPELESS} 同理。</p>
     *
     * <p><b>为什么必须是 {@code public}：</b>调用方 {@code PressHammerItem} 与
     * {@code StirringStaffItem} 位于 {@code com.alben.createhandmade.item} 包，
     * 而本类位于 {@code com.alben.createhandmade.recipe} 包。包私有（default）可见性
     * 跨不了包，所以这个助手只能是 {@code public static} —— 这是可见性所迫，
     * 不是要把本类的 API 面做大。</p>
     *
     * <p><b>为什么保留它（而不是让调用方自己拼）：</b>它属于「收集」职责 ——
     * 把若干个<b>已经收集好的子集</b>还原成配方管理器给出的全局顺序，
     * 既不判断哪条配方匹配、也不增删配方，因此没有越过
     * 「配方池只负责收集候选列表」的边界。而只要
     * {@link HandMadeTool#PRESS_HAMMER_BASIN} 与
     * {@link HandMadeTool#PRESS_HAMMER_AUTO_SQUARE}（以及搅拌杖那一对）
     * 仍然是分开的枚举条目，就必须由它来保证「两类同时匹配时谁先被选中」
     * 与改造前一致。将来合并成单一枚举条目后即可移除（见类注释的 TODO）。</p>
     *
     * <p><b>实现要点：</b>用 {@link IdentityHashMap} 做子集判定 —— 子集与
     * {@code level.getRecipeManager().getRecipes()} 返回的是同一批
     * {@link RecipeHolder} 实例（本类只做类型过滤，从不重新包装 holder），
     * 因此引用相等既安全又比 {@code equals} 快。整体是 O(n)，没有 O(n²) 的逐条查找。</p>
     *
     * @param level 当前世界；为 null 时返回空列表
     * @param lists 若干个子集，允许为 null 或空
     * @return 去重后按全局配方顺序排列的合并列表
     */
    @SafeVarargs
    public static List<RecipeHolder<?>> mergeInGlobalOrder(Level level, List<RecipeHolder<?>>... lists) {
        Set<RecipeHolder<?>> subset = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<RecipeHolder<?>> list : lists) {
            if (list != null) {
                subset.addAll(list);
            }
        }

        List<RecipeHolder<?>> result = new ArrayList<>(subset.size());
        if (level == null) {
            return result;
        }

        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (subset.contains(holder)) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================================================================
    // 冲压锤 (PressHammerItem)
    // ==================================================================

    /**
     * 冲压锤 · 工作盆 · 压缩（COMPACTING）。
     *
     * <p>游戏内路径：{@code PressHammerItem.tryPressBasin} ->
     * {@code RecipeFinder.get(COMPACTING_RECIPE_KEY, level, PressHammerItem::matchStaticFilters)}
     * 里 {@code recipe.getType() == AllRecipeTypes.COMPACTING.getType()} 的那一半。</p>
     *
     * <p>对外（JEI）路径：{@code CreateHandMadeJEI.collectCompactingRecipes}，
     * 即 {@link HandMadeTool#PRESS_HAMMER_BASIN} 类别。</p>
     */
    private static List<RecipeHolder<?>> collectPressHammerBasin(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.COMPACTING.getType())) {
            // CompactingRecipe 是 BasinRecipe 的子类；这里只收工作盆能处理的配方。
            if (holder.value() instanceof BasinRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    /**
     * 冲压锤 · 工作盆 · 自动摆放（4/9 合 1 的可压缩工作台配方）。
     *
     * <p>游戏内路径：{@code PressHammerItem.tryPressBasin} 的
     * {@code PressHammerItem::matchStaticFilters} 里「可压缩 CraftingRecipe」那一半，
     * 其判定与 {@code PressHammerItem.canCompress} 相同。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectAutoSquareRecipes}。</p>
     *
     * <p><b>返回的是原始 {@link CraftingRecipe} 的 holder，不做 BasinRecipe 包装。</b>
     * 原因有两个：其一，{@code BasinRecipe.convertShapeless} 内部用
     * {@code Minecraft.getInstance().level.registryAccess()}，是纯客户端调用，
     * 而本方法会在服务端被调用；其二，包装成 BasinRecipe 后会走
     * {@code BasinRecipe} 的 {@code getRemainingItems}（继承自 {@code Recipe} 的默认实现，
     * 全为 EMPTY），容器类残留的返还行为会和现在的原始 CraftingRecipe 路径不一致。
     * 需要 BasinRecipe 形态的只有 JEI 展示，由 {@code CreateHandMadeJEI} 自己包装。</p>
     */
    private static List<RecipeHolder<?>> collectPressHammerAutoSquare(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        // 与 PressHammerItem.canCompress / MechanicalPressBlockEntity.canCompress 一致：
        // 可压缩本身已经隐含了 allowShapedSquareInPress 配置开关。
        if (!AllConfigs.server().recipes.allowShapedSquareInPress.get()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            if (crafting instanceof MechanicalCraftingRecipe) continue;
            if (!MechanicalPressBlockEntity.canCompress(crafting)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;

            result.add(holder);
        }
        return result;
    }

    /**
     * 冲压锤 · 置物台 / 传送带（PRESSING）。
     *
     * <p>游戏内路径：{@code PressHammerItem.findPressingRecipe} ->
     * {@code AllRecipeTypes.PRESSING.find(new SingleRecipeInput(stack), level)}。</p>
     *
     * <p>注意：{@code AllRecipeTypes.find} 的实现就是
     * {@code level.getRecipeManager().getRecipeFor(...)}，<b>不带</b>任何 automation 过滤，
     * 所以这里返回全部 PRESSING 配方，由调用方自己决定是否再叠加
     * {@code AllRecipeTypes.CAN_BE_AUTOMATED} 判断。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectPressingRecipes}。</p>
     */
    private static List<RecipeHolder<?>> collectPressHammerDepot(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.PRESSING.getType())) {
            if (holder.value() instanceof PressingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================================================================
    // 研钵 (MortarItem) / 碾钵 (CrusherMortarItem)
    // ==================================================================

    /**
     * 研钵 · 研磨（MILLING）。
     *
     * <p>游戏内路径：{@code MortarItem#use} 与 {@code MortarItem#finishUsingItem}
     * 里的 {@code AllRecipeTypes.MILLING.find(recipeInput, level)}，
     * 等价于「按配方管理器顺序找到第一条 matches 的 MILLING 配方」。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectMillingRecipes}。</p>
     */
    private static List<RecipeHolder<?>> collectMortar(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MILLING.getType())) {
            if (holder.value() instanceof MillingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    /**
     * 碾钵 · 先粉碎后研磨。
     *
     * <p>游戏内路径：{@code CrusherMortarItem.findRecipe}，复刻自 Create 的
     * {@code CrushingWheelControllerBlockEntity.findRecipe()}：</p>
     * <pre>
     * 先 AllRecipeTypes.CRUSHING.find(...)  -> 命中就用
     * 否则 AllRecipeTypes.MILLING.find(...) -> 命中就用
     * 都不到 -> null
     * </pre>
     *
     * <p>因此这里返回「CRUSHING 全部 + MILLING 全部」的拼接列表，
     * <b>CRUSHING 必须在前</b>，调用方从前往后取第一个匹配即可保持优先级完全一致。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectCrushingRecipes}
     * （该类别只展示粉碎那一半，会自行过滤掉 MillingRecipe）。</p>
     */
    private static List<RecipeHolder<?>> collectCrusherMortar(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        // 前半段：粉碎优先。
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.CRUSHING.getType())) {
            if (holder.value() instanceof CrushingRecipe) {
                result.add(holder);
            }
        }

        // 后半段：研磨兜底。
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MILLING.getType())) {
            if (holder.value() instanceof MillingRecipe) {
                result.add(holder);
            }
        }

        return result;
    }

    // ==================================================================
    // 手锯 (HandSawItem)
    // ==================================================================

    /**
     * 手锯 · 切削（CUTTING）。
     *
     * <p>游戏内路径：{@code HandSawItem.getCuttingRecipes} ——
     * 先由 {@code SequencedAssemblyRecipe.getRecipe(...)} 处理序列组装（那部分仍留在工具里），
     * 之后才是 {@code level.getRecipeManager().getAllRecipesFor(CUTTING)} + {@code matches} +
     * {@code !AllRecipeTypes.shouldIgnoreInAutomation(holder)}。</p>
     *
     * <p>注意：automation 过滤<b>不在</b>这里做 —— JEI 类别本来就会展示那些「仅手动」配方，
     * 把过滤挪进来会改变 JEI 显示内容。调用方（工具）遍历本列表时自行叠加过滤即可。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectHandSawRecipes}。</p>
     */
    private static List<RecipeHolder<?>> collectHandSaw(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.CUTTING.getType())) {
            if (holder.value() instanceof CuttingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    /**
     * 手锯 · 原版切石（STONECUTTING）—— T7 批次 1 的新增段。
     *
     * <p>游戏内路径：{@code HandSawItem.getCuttingRecipes} 的<b>第三段</b>。
     * 手锯的三段式查询是「序列组装 → 切削 → 切石，各自早退」（T7 决策 1，方案 B1）：
     * 前两段（含序列组装）全部不命中，才会走到这里。</p>
     *
     * <p><b>为什么读原版 {@code minecraft:stonecutting} 而不是 Create 的类型：</b>
     * 动力锯（Create 的 {@code SawBlockEntity.getRecipes}）在
     * {@code allowStonecuttingOnSaw} 打开时会把原版切石配方一并纳入候选集，
     * 本段就是复刻它那一半行为；Create 自己没有 "cutting 版"的切石类型。</p>
     *
     * <p><b>门控：</b>直接跟随 Create 的 server config {@code recipes.allowStonecuttingOnSaw}
     * （见 {@link #createAllowsStonecutting()}）。放在池里而不是工具里，是为了让
     * <b>游戏内工具与 JEI 共用同一个门</b> —— 与 {@code allowShapelessInMixer}
     * （{@link #collectStirringStaffAutoShapeless}）的处理方式一致。关掉配置时这里返回空列表，
     * 手锯的行为与本次改造前逐字相同。</p>
     *
     * <p><b>两层的收集顺序：L1（原版 {@code minecraft:stonecutting}）在前、L3（本模组
     * {@code create_hand_made:stonecutting_recipe}）在后</b> —— 与 {@code tool_recipe} 的
     * "L1 优先"约定一致（见 {@code docs/RECIPE_API.md} 的「两种 L3 的匹配顺序」）。
     * 手锯取第一个匹配的配方，所以想让 L3 生效，作者要先用 L2 把对应的 L1 那条禁掉；
     * 反过来，L1 里没有的输入，L3 直接生效。</p>
     *
     * <p><b>只收 {@link StonecutterRecipe}（L1）：</b>与 {@code collectHandSaw} 只收
     * {@code CuttingRecipe} 对称。切石类型下正常只会是原版这个类（datapack / 其它 mod 都经由
     * {@code minecraft:stonecutting} 这个 serializer 构造它），这里做一次 instanceof 收窄既是
     * 防御也是类型需要。</p>
     *
     * <p><b>L3 只收 {@link HandMadeStonecuttingRecipe}：</b>同理 —— 本模组自己的 type 只会由
     * {@link HandMadeStonecuttingRecipeSerializer} 产出这个类。</p>
     *
     * <p><b>不做 automation 过滤：</b>{@code shouldIgnoreInAutomation} 判的是 Create 的
     * {@code _manual_only} 约定（{@code create:cutting} 家族才有），原版切石配方没有这个概念，
     * 动力锯那一侧也没有对切石配方做这个过滤。</p>
     *
     * <p><b>L2 自动生效：</b>本方法把 L1 与 L3 一起放进同一个列表，而
     * {@code getBaseRecipes} 的最后一步是 {@code HandMadeRecipeFilters.isDisabled(tool, id)}
     * ——按 recipe id 过滤，与配方 type 无关，所以两个层都能被数据包 / KubeJS 单独禁用。</p>
     *
     * <p><b>JEI 路径：</b>本批次（T7 批次 2）不建 JEI 类别；批次 3 会用它。</p>
     */
    private static List<RecipeHolder<?>> collectHandSawStonecutting(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        // 门控：直接跟随 Create 的 allowStonecuttingOnSaw。
        if (!createAllowsStonecutting()) {
            return result;
        }

        // L1：原版切石配方。
        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.STONECUTTING)) {
            if (holder.value() instanceof StonecutterRecipe) {
                result.add(holder);
            }
        }

        // L3：本模组自己的切石独占配方（独立 type，Create / 原版都看不到）。
        for (RecipeHolder<?> holder : recipesOfType(level, HandMadeRecipeTypes.STONECUTTING_RECIPE.getType())) {
            if (holder.value() instanceof HandMadeStonecuttingRecipe) {
                result.add(holder);
            }
        }

        return result;
    }

    /**
     * Create 的 {@code recipes.allowStonecuttingOnSaw} 当前是否为 true。
     *
     * <p><b>为什么要防御 null：</b>该值是 Create 的 <b>server config</b>，由 Create 在自身
     * 构造期经 {@code AllConfigs.register(...)} 注册（{@code AllConfigs.java:60-71}）。
     * 在 Create 注册之前，{@code AllConfigs.server()} 返回 {@code null}；而 catnip 的
     * {@code ConfigBase.CValue.get()} 在未注册时直接抛
     * {@code AssertionError("Config ... was accessed, but not registered before!")}
     * （catnip {@code ConfigBase.java:124-127}）。</p>
     *
     * <p>所以这里显式判 null：读不到的场合（极早的调用时机）按「Create 未开启」处理 ——
     * 即失败关闭（fail-closed），而不是让异常冒到调用方。正常运行时本方法总在玩家交互 /
     * JEI 收配方时被调用，那时 Create 的 config 早已加载。</p>
     *
     * <p><b>为什么每次调用都读值：</b>NeoForge 的 {@code ConfigValue#get()} 读的是当前缓存值，
     * config 重载后自动更新；而且 {@code ModConfigEvent} 只投递给 config 的所属 mod，
     * 我们<b>无法</b>订阅 Create 的 config 事件。所以「每次读」既是唯一选择也是正确做法 ——
     * 与 {@code allowShapedSquareInPress}（{@link #collectPressHammerAutoSquare}）、
     * {@code allowShapelessInMixer}、{@code allowBrewingInMixer} 三处现有读法完全一致。</p>
     */
    private static boolean createAllowsStonecutting() {
        var server = AllConfigs.server();   // Create config 未加载时为 null
        return server != null && server.recipes.allowStonecuttingOnSaw.get();
    }

    // ==================================================================
    // 搅拌杖 (StirringStaffItem)
    // ==================================================================

    /**
     * 搅拌杖 · 混合（MIXING）。
     *
     * <p>游戏内路径：{@code StirringStaffItem.findMatchingRecipe} 第 1 步里
     * {@code r.getType() == AllRecipeTypes.MIXING.getType()} 的那一半。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectMixingRecipes}。</p>
     */
    private static List<RecipeHolder<?>> collectStirringStaff(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MIXING.getType())) {
            // MixingRecipe 是 BasinRecipe 的子类，工作盆能直接处理。
            if (holder.value() instanceof BasinRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    /**
     * 搅拌杖 · 自动无序合成。
     *
     * <p>游戏内路径：{@code StirringStaffItem.findMatchingRecipe} 第 1 步里
     * {@code StirringStaffItem.matchStaticFilters} 的「无序合成」那一半：
     * 非 Shaped、原料数 &gt; 1、不可压缩、非 automation-ignore 的 CraftingRecipe。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectAutoShapelessRecipes}。</p>
     *
     * <p><b>返回的是原始 {@link CraftingRecipe} 的 holder，不做 BasinRecipe 包装</b>
     * —— 理由同 {@link #collectPressHammerAutoSquare}：客户端调用问题，
     * 以及包装后会丢掉 {@code getRemainingItems} 里的容器残留返还
     * （无序配方比 4/9 压缩配方更容易出现桶之类的容器残留，这一条在本类别上影响是实打实的）。</p>
     */
    private static List<RecipeHolder<?>> collectStirringStaffAutoShapeless(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!AllConfigs.server().recipes.allowShapelessInMixer.get()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            if (crafting instanceof ShapedRecipe) continue;
            if (crafting.getIngredients().size() <= 1) continue;
            if (MechanicalPressBlockEntity.canCompress(crafting)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;

            result.add(holder);
        }
        return result;
    }

    /**
     * 搅拌杖 · 自动酿造。
     *
     * <p>游戏内路径：{@code StirringStaffItem.findMatchingRecipe} 第 2 步，
     * 走 {@code PotionMixingRecipes.sortRecipesByItem(level)}（<b>该步逻辑保持不变</b>，
     * 仍由工具自己处理）。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectAutoBrewingRecipes}。</p>
     *
     * <p>这里用 {@code RecipeGenericsUtil.cast} 把
     * {@code RecipeHolder<MixingRecipe>} 列表泛型上推，便于统一放进
     * {@code List<RecipeHolder<?>>}。</p>
     */
    private static List<RecipeHolder<?>> collectStirringStaffAutoBrewing(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!AllConfigs.server().recipes.allowBrewingInMixer.get()) {
            return result;
        }

        result.addAll(RecipeGenericsUtil.cast(PotionMixingRecipes.createRecipes(level)));
        return result;
    }

    // ==================================================================
    // 指杆 (PointerItem) / 灌注枪 (InfusionGunItem)
    // ==================================================================

    /**
     * 指杆 · 应用（DEPLOYING + ITEM_APPLICATION）。
     *
     * <p>游戏内路径：{@code PointerItem.findRecipe} ——
     * 先由 {@code SequencedAssemblyRecipe.getRecipe(...)} 处理序列组装（仍留在工具里），
     * 之后 {@code AllRecipeTypes.DEPLOYING.find(wrapper, level).filter(CAN_BE_AUTOMATED)}，
     * 再退到 {@code AllRecipeTypes.ITEM_APPLICATION.find(wrapper, level).filter(CAN_BE_AUTOMATED)}。</p>
     *
     * <p>返回列表保证 <b>DEPLOYING 在前、ITEM_APPLICATION 在后</b>；
     * automation 过滤同样留给调用方，以免改变 JEI 显示。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectPointerRecipes}。</p>
     */
    private static List<RecipeHolder<?>> collectPointer(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        // 前半段：部署（DEPLOYING）。DeployerApplicationRecipe 是 ItemApplicationRecipe 的子类。
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.DEPLOYING.getType())) {
            if (holder.value() instanceof ItemApplicationRecipe) {
                result.add(holder);
            }
        }

        // 后半段：物品应用（ITEM_APPLICATION）。
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.ITEM_APPLICATION.getType())) {
            if (holder.value() instanceof ItemApplicationRecipe) {
                result.add(holder);
            }
        }

        return result;
    }

    /**
     * 灌注枪 · 注液（FILLING）。
     *
     * <p>游戏内路径：{@code InfusionGunItem.findFillingRecipe} ——
     * 先由 {@code SequencedAssemblyRecipe.getRecipe(...)} 处理序列组装（仍留在工具里），
     * 之后遍历 {@code level.getRecipeManager().getAllRecipesFor(FILLING)}，
     * 用 {@code fr.matches(input, level) && fr.getRequiredFluid().test(fluid)} 判定。</p>
     *
     * <p>JEI 路径：{@code CreateHandMadeJEI.collectInfusionGunRecipes}，
     * 之后由 JEI 侧追加 {@code SpoutCategory.consumeRecipes}（那部分依赖 JEI 的
     * {@code IIngredientManager}，必须留在 JEI 里）。</p>
     */
    private static List<RecipeHolder<?>> collectInfusionGun(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.FILLING.getType())) {
            if (holder.value() instanceof FillingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 收集 <b>L3 独占配方</b>（{@link HandMadeRecipeTypes#TOOL_RECIPE}）中属于本工具的那些。
     *
     * <p>独占配方活在 {@code create_hand_made:tool_recipe} 这个独立 RecipeType 下，
     * 所以 Create 的机器（查的是 {@code AllRecipeTypes.XXX.getType()}）永远看不到它们；
     * 只有本方法把它们并进工具的候选列表。</p>
     *
     * <p><b>哪些工具支持：</b>目前 <b>8 个工具 id / 6 个家族</b>：</p>
     * <ul>
     *   <li>basin：{@link HandMadeTool#PRESS_HAMMER_BASIN} / {@link HandMadeTool#STIRRING_STAFF}
     *       → {@link HandMadeToolRecipe}</li>
     *   <li>碾磨：{@link HandMadeTool#MORTAR} / {@link HandMadeTool#CRUSHER_MORTAR}
     *       → {@link HandMadeCrushingRecipe}</li>
     *   <li>冲压：{@link HandMadeTool#PRESS_HAMMER_DEPOT} → {@link HandMadePressingRecipe}</li>
     *   <li>切削：{@link HandMadeTool#HAND_SAW} → {@link HandMadeCuttingRecipe}</li>
     *   <li>注液：{@link HandMadeTool#INFUSION_GUN} → {@link HandMadeFillingRecipe}</li>
     *   <li>应用：{@link HandMadeTool#POINTER} → {@link HandMadeApplicationRecipe}</li>
     * </ul>
     * <p><b>这份清单必须与 {@code HandMadeToolRecipeSerializer.familyOf} 的键集保持一致。</b>
     * 其余工具（自动摆放 / 自动无序合成 / 自动酿造）还没有自己的独占配方类。</p>
     *
     * <p><b>归属匹配规则：</b>独占配方按 {@code tool} 字段归属，通常只给写下它的那个工具读；
     * 例外是 {@link HandMadeTool#CRUSHER_MORTAR} —— 它连 Create 的 MILLING 配方都能读
     * （L1 语义就是"先粉碎、后研磨"），所以也接受
     * {@link HandMadeTool#MORTAR} 写下的独占配方。</p>
     *
     * @param tool  目标工具
     * @param level 当前世界（非 null）
     * @param out   收集结果直接追加到这里
     */
    private static void collectExclusiveRecipes(HandMadeTool tool, Level level, List<RecipeHolder<?>> out) {
        if (!supportsExclusiveRecipes(tool)) {
            return;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, HandMadeRecipeTypes.TOOL_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeToolRecipeLike exclusive)) {
                continue;
            }
            if (!acceptsExclusiveRecipe(tool, exclusive.getTool())) {
                continue;
            }
            out.add(holder);
        }
    }

    /** 该工具是否有自己的 L3 独占配方类（与 serializer 的分派表同步）。 */
    private static boolean supportsExclusiveRecipes(HandMadeTool tool) {
        return tool == HandMadeTool.PRESS_HAMMER_BASIN
                || tool == HandMadeTool.STIRRING_STAFF
                || tool == HandMadeTool.MORTAR
                || tool == HandMadeTool.CRUSHER_MORTAR
                || tool == HandMadeTool.PRESS_HAMMER_DEPOT
                || tool == HandMadeTool.HAND_SAW
                || tool == HandMadeTool.INFUSION_GUN
                || tool == HandMadeTool.POINTER;
    }

    /** 某条独占配方（归属 {@code owner}）是否应该进入 {@code tool} 的候选集。 */
    private static boolean acceptsExclusiveRecipe(HandMadeTool tool, @Nullable HandMadeTool owner) {
        if (owner == null) {
            return false;
        }
        if (owner == tool) {
            return true;
        }
        // 碾钵 = 研钵的升级版：L1 层它就能读 Create 的 MILLING，L3 层同理
        return tool == HandMadeTool.CRUSHER_MORTAR && owner == HandMadeTool.MORTAR;
    }

    /**
     * 取某个 {@link RecipeType} 的全部配方。
     *
     * <p>等价于 Create 的 {@code CreateJEI.getTypedRecipes(type)}，但走
     * {@code level.getRecipeManager()}，因此<b>服务端可用</b>，也不依赖任何 JEI 类。
     * 因为 {@code RecipeType} 的泛型参数在这里只会被擦除，所以用原始类型做一次转换。</p>
     *
     * @param level 当前世界（非 null）
     * @param type  配方类型
     * @return 该类型的全部配方，顺序与配方管理器一致
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> recipesOfType(Level level, RecipeType<?> type) {
        // RecipeType 的泛型参数在调用处被擦除（原始类型），返回值因此退化成 List<?>，
        // 这里逐条装回 RecipeHolder<?>。
        List<?> raw = level.getRecipeManager().getAllRecipesFor((RecipeType) type);
        List<RecipeHolder<?>> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (entry instanceof RecipeHolder<?> holder) {
                result.add(holder);
            }
        }
        return result;
    }
}
