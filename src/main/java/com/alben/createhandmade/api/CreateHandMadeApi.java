package com.alben.createhandmade.api;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.compat.MaidApiHooks;
import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.item.PressHammerItem;
import com.alben.createhandmade.item.StirringStaffItem;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import com.alben.createhandmade.item.contents.MortarContents;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Create: Hand Made 对外 API 的稳定入口。
 *
 * <p>跨模组请优先绑定本类：这里的签名视为稳定，内部转发到具体工具类。
 * 需要新能力时在本类新增方法，不破坏已有调用方。</p>
 *
 * <p>{@link #API_VERSION} 破坏性变更时递增；新增方法不算破坏性变更。</p>
 *
 * <p>本类提供<b>查询 / 读写组件</b>级别能力，以及<b>受控的写操作入口</b>
 * （执行加工、注入/存储流体、应用鼓风等）。写操作的副作用见各方法 Javadoc，
 * 调用方必须保证在服务端调用。</p>
 */
public final class CreateHandMadeApi {

    /**
     * API 契约版本。
     *
     * <p>2：{@link #recipesFor} 等查询的返回列表改为**不可变**（此前是可变的 ArrayList，
     * 调用方就地排序不会报错，现在会抛 {@code UnsupportedOperationException}）；
     * 所有写操作在客户端调用时改为静默拒绝（返回 false / 无操作），不再有副作用。</p>
     */
    public static final int API_VERSION = 2;

    private CreateHandMadeApi() {
    }

    // ==================== 配方查询 ====================

    /**
     * 某工具在给定世界里可用的全部基础配方（L1 + L3，顺序即游戏内的查询优先顺序）。
     *
     * <p>转发到 {@link HandMadeRecipePool#getBaseRecipes}，和 1.21 语义一致：
     * L1 按工具对应的 Create RecipeType 全量拉取，L3 按 tool 字段过滤，
     * 不调 {@code matches()}，不依赖输入物品。</p>
     *
     * <p><b>不含 L2 过滤</b>：数据包 / KubeJS 禁用的配方仍在列表里。要拿到最终可用列表请用
     * {@link #recipesForFiltered}；只想知道"这件物品能不能加工"请用 {@link #findRecipeFor}。</p>
     */
    public static List<Recipe<?>> recipesFor(ToolType tool, Level level) {
        if (level == null) return List.of();
        // ★ 返回不可变列表，和 recipesForFiltered / handSawRecipes 保持一致：
        //   以前这里返回可变的 ArrayList，调用方既能改也能传出去，容易踩到"改完影响别人"的坑。
        return List.copyOf(HandMadeRecipePool.getBaseRecipes(tool, level));
    }

    /**
     * 同 {@link #recipesFor}，但把被数据包 / KubeJS（L2）禁用的配方剔除掉。
     *
     * <p>这是"到底能做什么"的口径。反查索引（配方 → ID）由
     * {@link HandMadeRecipePool} 按 RecipeManager 缓存、资源重载时失效，重复调用不会每次重建；
     * 但它仍会完整遍历一遍候选列表，别放在每 tick 的热路径里。</p>
     */
    public static List<Recipe<?>> recipesForFiltered(ToolType tool, Level level) {
        List<Recipe<?>> base = HandMadeRecipePool.getBaseRecipes(tool, level);
        if (base.isEmpty()) return List.of();
        return List.copyOf(HandMadeRecipePool.applyFilter(tool, level, base));
    }

    /**
     * 按输入物品查配方：这件物品在该工具下走哪个配方；没有则 null。
     *
     * <p>内部已经把 {@code ItemStackHandler} + {@link RecipeWrapper} 组装好、按游戏内
     * 优先顺序跑完 {@code matches}，接入方不必碰 Forge 的 Container 细节。</p>
     *
     * <p>能回答"只看一件物品"的工具：手锯切削、手锯切石、冲压锤（Depot）、研钵、碾钵。
     * 其余工具返回 null，因为它们光看一件物品判不出来：</p>
     * <ul>
     *   <li>{@code PRESS_HAMMER_BASIN} / {@code STIRRING_STAFF} 要整盆材料 —— 用
     *       {@link #pressBasin} / {@link #findStirringRecipe}；</li>
     *   <li>{@code INFUSION_GUN} 还要看枪里/罐里的流体；</li>
     *   <li>{@code POINTER} 要"被加工物 + 手持工具"两件输入 —— 用
     *       {@link #findPointerRecipe}；</li>
     *   <li>{@code BELLOWS} 要指定介质类型 —— 用 {@link #bellowsCanProcess}。</li>
     * </ul>
     */
    @Nullable
    public static Recipe<?> findRecipeFor(ToolType tool, Level level, ItemStack input) {
        if (level == null || input == null || input.isEmpty()) return null;

        // 逐个工具走它自己的查找器：优先级、额外特判、L2 过滤都和游戏内一致
        return switch (tool) {
            case HAND_SAW -> {
                List<Recipe<?>> cutting = HandSawItem.getCuttingRecipes(level, input);
                yield cutting.isEmpty() ? null : cutting.get(0);
            }
            case HAND_SAW_STONECUTTING -> firstMatch(recipesForFiltered(tool, level), input, level);
            case PRESS_HAMMER_DEPOT -> PressHammerItem.findPressingRecipe(level, input);
            case MORTAR -> MortarItem.findRecipe(level, input);
            case CRUSHER_MORTAR -> CrusherMortarItem.findRecipe(level, input);
            default -> null;
        };
    }

    /**
     * 在给定配方列表里按顺序找第一个能匹配 {@code input} 的配方。
     *
     * <p>1.20.1 的 {@code Recipe} 是 {@code Recipe<C extends Container>}，用通配符持有后
     * 不能直接调 {@code matches}，这里做一次受控的 raw 转换；列表全部来自本模组的
     * RecipeManager 查询，容器形状与 {@link RecipeWrapper} 兼容。</p>
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Recipe<?> firstMatch(List<Recipe<?>> recipes, ItemStack input, Level level) {
        if (recipes.isEmpty()) return null;

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        for (Recipe<?> recipe : recipes) {
            if (((Recipe) recipe).matches(wrapper, level)) return recipe;
        }
        return null;
    }

    /** 该配方是否被数据包 / KubeJS 的过滤器对本工具禁用。 */
    public static boolean isRecipeDisabled(ToolType tool, ResourceLocation recipeId) {
        return HandMadeRecipePool.isRecipeDisabled(tool, recipeId);
    }

    /**
     * 反查配方 ID（1.20.1 的裸 {@link Recipe} 没有 {@code id()}）。
     *
     * <p>内部按 {@link net.minecraft.world.item.crafting.RecipeManager} 缓存了一张
     * 「配方 → ID」表，资源 / 脚本重载时自动失效，所以可以放心在循环里对每条配方调用；
     * 不要自己写 {@code getRecipeIds() + byKey()} 反查，那是 O(n²)。</p>
     *
     * <p>动态配方（如 {@code SequencedAssemblyRecipe}）没有稳定 ID，返回 {@code null}。</p>
     */
    @Nullable
    public static ResourceLocation recipeIdOf(Level level, Recipe<?> recipe) {
        if (level == null) return null;
        return HandMadeRecipePool.recipeIdOf(level, recipe);
    }

    // ==================== 风箱介质 ====================

    /** 该物品能充当哪些风箱处理类型。 */
    public static List<ResourceLocation> bellowsMediaTypes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        return List.copyOf(BellowsMediaRegistry.resolveAll(stack));
    }

    /** 该物品是否能作为风箱介质。 */
    public static boolean isBellowsMedia(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return BellowsMediaRegistry.isMedia(stack);
    }

    /** 某风箱处理类型对应的介质物品列表。 */
    public static List<ItemStack> bellowsMediaItems(ResourceLocation typeId) {
        if (typeId == null) return List.of();
        return List.copyOf(BellowsMediaRegistry.getMediaFor(typeId));
    }

    /** 某风箱处理类型是否被配置禁用。 */
    public static boolean isBellowsTypeDisabled(ResourceLocation typeId) {
        if (typeId == null) return false;
        return BellowsMediaRegistry.isTypeDisabled(typeId);
    }

    // ==================== 灌注枪 ====================

    /** 灌注枪容量（mB）。 */
    public static int infusionGunCapacity() {
        return InfusionGunContents.CAPACITY;
    }

    /** 读灌注枪内的流体（永不返回 null；空枪返回 FluidStack.EMPTY）。 */
    public static InfusionGunContents infusionGunContents(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return new InfusionGunContents(FluidStack.EMPTY);
        return InfusionGunItem.getContents(gun);
    }

    /** 写灌注枪内的流体；contents 为空时移除组件。 */
    public static void setInfusionGunContents(ItemStack gun, @Nullable InfusionGunContents contents) {
        if (gun == null || gun.isEmpty()) return;
        InfusionGunItem.setContents(gun, contents);
    }

    /** 读取世界里某个位置的流体源（只读，不消耗、不改变世界）。 */
    public static FluidStack fluidSourceAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return FluidStack.EMPTY;
        return InfusionGunItem.getFluidFromSource(level, pos);
    }

    // ==================== 研钵 / 碾钵 ====================

    /** 读研钵内的内容物；空碗返回 null。 */
    @Nullable
    public static MortarContents mortarContents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return MortarContents.fromStack(stack);
    }

    /** 写研钵内的内容物；传 null 表示清空。 */
    public static void setMortarContents(ItemStack stack, @Nullable MortarContents contents) {
        if (stack == null || stack.isEmpty()) return;
        if (contents == null) {
            MortarContents.clearFromStack(stack);
        } else {
            contents.writeToStack(stack);
        }
    }

    /** 按当前配置计算这次加工需要多少 tick（不含起手）。 */
    public static int mortarProcessingTicks(boolean crusherMortar) {
        // 走 HandMadeConfig 门面 = 走 SyncedServerConfig：客户端拿到的是服务端同步下来的权威值，
        // 而不是本地 SERVER 配置的默认值（多人游戏里后者根本读不到）。
        return crusherMortar
                ? HandMadeConfig.crusherMortarProcessingTicks()
                : HandMadeConfig.mortarProcessingTicks();
    }

    // ==================== 手锯 ====================

    /**
     * 手锯在某输入上可用的切削配方（L1 + L3，顺序即游戏内优先顺序）。
     *
     * <p><b>返回的列表不可变</b>（内部按「配方表 + 输入物品」缓存，直接复用同一实例，
     * 所以调用方不要就地修改）。想改顺序请自己拷贝一份。</p>
     */
    public static List<Recipe<?>> handSawRecipes(Level level, ItemStack input) {
        return HandSawItem.getCuttingRecipes(level, input);
    }

    // ==================== 冲压锤 ====================

    /** 冲压锤在某输入上可用的压制配方；无匹配返回 null。 */
    @Nullable
    public static Recipe<?> findPressingRecipe(Level level, ItemStack input) {
        if (level == null || input == null || input.isEmpty()) return null;
        return PressHammerItem.findPressingRecipe(level, input);
    }

    /**
     * 冲压锤工作盆冲压。
     *
     * <p><b>只在服务端调用。</b>消耗输入、产出、播放粒子/音效。</p>
     *
     * @param filter 过滤产物，null 表示不过滤
     */
    public static boolean pressBasin(Level level, BlockPos pos, @Nullable FilterItemStack filter) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null) return false;
        return PressHammerItem.tryPressBasin(level, pos, filter);
    }

    /**
     * 冲压锤传送带冲压。
     *
     * <p><b>只在服务端调用。</b>消耗输入、产出、播放粒子/音效。</p>
     *
     * @param filter 过滤产物，null 表示不过滤
     */
    public static boolean pressTransported(Level level, BlockPos pos, @Nullable FilterItemStack filter) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null) return false;
        return PressHammerItem.tryPressTransported(level, pos, filter);
    }

    // ==================== 研钵 / 碾钵（写操作） ====================

    /**
     * 研钵 / 碾钵一次性研磨。
     *
     * <p><b>只在服务端调用。</b>消耗输入、产出结果、扣耐久、播放粒子/音效。</p>
     *
     * @param crusherMortar true 走碾钵，false 走研钵
     * @param filter        过滤产物，null 表示不过滤
     * @return 是否成功加工
     */
    public static boolean grindMortarOnce(Level level, LivingEntity entity, ItemStack mortar,
                                          @Nullable IItemHandler inputInv,
                                          @Nullable IItemHandler outputInv,
                                          @Nullable FilterItemStack filter,
                                          boolean crusherMortar) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide) return false;
        if (entity == null || mortar == null || mortar.isEmpty()) return false;
        return crusherMortar
                ? CrusherMortarItem.tryGrindOnce(level, entity, mortar, inputInv, outputInv, filter)
                : MortarItem.tryGrindOnce(level, entity, mortar, inputInv, outputInv, filter);
    }

    // ==================== 灌注枪（写操作） ====================

    /** 方块在该位置的流体处理器；无返回 null。只读查询，不改变世界。 */
    @Nullable
    public static IFluidHandler fluidHandlerAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return InfusionGunItem.getFluidHandler(level, pos);
    }

    /**
     * 灌注枪对传送带物品注入。
     *
     * <p><b>只在服务端调用。</b>消耗枪内流体、转化传送带物品、扣耐久、广播粒子。</p>
     *
     * @param filter 过滤产物，null 表示不过滤
     * @return 是否成功注入
     */
    public static boolean injectItems(Level level, BlockPos pos, LivingEntity entity,
                                      ItemStack gun, @Nullable FilterItemStack filter) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null) return false;
        if (entity == null || gun == null || gun.isEmpty()) return false;
        return InfusionGunItem.tryInjectItems(level, pos, entity, gun, filter);
    }

    /**
     * 灌注枪把流体存入方块。
     *
     * <p><b>只在服务端调用。</b>扣耐久、播放音效。</p>
     */
    public static boolean storeFluid(Level level, BlockPos pos, LivingEntity entity, ItemStack gun) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null) return false;
        if (entity == null || gun == null || gun.isEmpty()) return false;
        return InfusionGunItem.tryStoreFluid(level, pos, entity, gun);
    }

    /**
     * 灌注枪从方块抽取一 tick 流体。
     *
     * <p><b>只在服务端调用。</b>首次成功抽取时扣耐久。</p>
     */
    public static void extractFluidTick(Level level, BlockPos pos, LivingEntity entity,
                                        ItemStack gun, int rate) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null) return;
        if (entity == null || gun == null || gun.isEmpty()) return;
        InfusionGunItem.tryExtractTick(level, pos, entity, gun, rate);
    }

    // ==================== 指杆 ====================

    /** 指杆：在某目标上、用某工具执行的应用配方；无匹配返回 null。 */
    @Nullable
    public static Recipe<?> findPointerRecipe(Level level, ItemStack target, ItemStack tool) {
        if (level == null || target == null || target.isEmpty()) return null;
        if (tool == null || tool.isEmpty()) return null;
        return PointerItem.findRecipe(level, target, tool);
    }

    /** 该配方是否是指杆可执行的应用配方。 */
    public static boolean isPointerApplicationRecipe(@Nullable Recipe<?> recipe) {
        return PointerItem.isApplicationRecipe(recipe);
    }

    // ==================== 搅拌杖 ====================

    /** 搅拌杖在工作盆上可用的混合配方；无匹配返回 null。 */
    @Nullable
    public static Recipe<?> findStirringRecipe(Level level, BasinBlockEntity basin) {
        if (level == null || basin == null) return null;
        return StirringStaffItem.findMatchingRecipe(level, basin);
    }

    // ==================== 风箱 ====================

    /** 风箱介质能否处理某物品。只读查询。 */
    public static boolean bellowsCanProcess(Level level, ItemStack media, FanProcessingType type) {
        if (level == null || media == null || media.isEmpty() || type == null) return false;
        return BellowsItem.canProcess(level, media, type);
    }

    /**
     * 风箱对目标应用鼓风效果。
     *
     * <p><b>只在服务端调用。</b>跨模组触发上游效果，可能转化物品。</p>
     */
    public static boolean applyBellows(Level level, BlockPos pos, FanProcessingType type) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (level == null || level.isClientSide || pos == null || type == null) return false;
        return BellowsItem.applyToTarget(level, pos, type);
    }

    // ==================== 通用 ====================

    /**
     * 扣工具耐久。只在服务端调用。
     *
     * <p>实体是车万女仆时按好感度走"概率免耐久"，和女仆行为内部完全一致（以前这里是无条件
     * {@code hurtAndBreak}，和女仆自己加工的手感对不上）；其它实体直接 {@code hurtAndBreak}。
     * 没装 TLM 时就是裸扣耐久。</p>
     */
    public static void damageTool(ItemStack stack, LivingEntity entity) {
        // ★ 写操作只认服务端：客户端调用会改动本地状态（幻影物品 / 耐久不同步），直接拒绝
        if (stack == null || stack.isEmpty() || entity == null) return;
        if (entity.level().isClientSide) return;
        if (MaidApiHooks.damageToolWithMaidRules(stack, entity, 1)) return;
        InfusionGunItem.damageGun(stack, entity);
    }
}