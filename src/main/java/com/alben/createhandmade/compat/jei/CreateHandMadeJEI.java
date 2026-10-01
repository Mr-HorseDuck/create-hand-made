package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.compat.jei.category.BellowsCookingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsHauntingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsSplashingCategory;
import com.alben.createhandmade.compat.jei.category.CrusherMortarCrushingCategory;
import com.alben.createhandmade.compat.jei.category.HandInfusionGunCategory;
import com.alben.createhandmade.compat.jei.category.HandPressBasinCategory;
import com.alben.createhandmade.compat.jei.category.HandPressDepotCategory;
import com.alben.createhandmade.compat.jei.category.HandSawCategory;
import com.alben.createhandmade.compat.jei.category.MortarMillingCategory;
import com.alben.createhandmade.compat.jei.category.PointerApplicationCategory;
import com.alben.createhandmade.compat.jei.category.StirringStaffMixingCategory;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeCuttingRecipe;
import com.alben.createhandmade.recipe.HandMadePressingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.recipe.HandMadeToolRecipe;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.SpoutCategory;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@JeiPlugin
public class CreateHandMadeJEI implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(CreateHandMade.MODID, "jei_plugin");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();
    private IIngredientManager ingredientManager;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        allCategories.clear();   // ★ 照抄 Create
        // ==================== 手锯切削 ====================
        CreateRecipeCategory.Info<CuttingRecipe> handSawInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_SAW_CUTTING,
                Component.translatable("jei.create_hand_made.hand_saw"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.HAND_SAW.get()),
                        () -> new ItemStack(Items.OAK_LOG)),
                CreateHandMadeJEI::collectHandSawRecipes,
                List.of(() -> new ItemStack(ModItems.HAND_SAW.get()))
        );
        allCategories.add(new HandSawCategory(handSawInfo));

        // ==================== 冲压锤 · 置物台 ====================
        CreateRecipeCategory.Info<PressingRecipe> handPressDepotInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_DEPOT,
                Component.translatable("jei.create_hand_made.hand_press_depot"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(AllItems.IRON_SHEET.get())),
                CreateHandMadeJEI::collectPressingRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(new HandPressDepotCategory(handPressDepotInfo));

        // ==================== 冲压锤 · 工作盆（打包） ====================
        CreateRecipeCategory.Info<BasinRecipe> handPressBasinInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_BASIN,
                Component.translatable("jei.create_hand_made.hand_press_basin"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(AllBlocks.BASIN.get())),
                CreateHandMadeJEI::collectCompactingRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(HandPressBasinCategory.standard(handPressBasinInfo));

// ==================== 冲压锤 · 工作盆（自动摆放 4/9 合 1） ★ 新增 ====================
        CreateRecipeCategory.Info<BasinRecipe> handPressBasinAutoSquareInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_BASIN_AUTO_SQUARE,
                Component.translatable("jei.create_hand_made.hand_press_basin_auto_square"),
                new EmptyBackground(177, 85),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(Items.CRAFTING_TABLE)),
                CreateHandMadeJEI::collectAutoSquareRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(HandPressBasinCategory.autoSquare(handPressBasinAutoSquareInfo));

        // ==================== 灌注枪 · 注液 ====================
        CreateRecipeCategory.Info<FillingRecipe> handInfusionGunInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_INFUSION_GUN,
                Component.translatable("jei.create_hand_made.hand_infusion_gun"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.INFUSION_GUN.get()),
                        () -> new ItemStack(Items.WATER_BUCKET)),
                this::collectInfusionGunRecipes,
                List.of(() -> new ItemStack(ModItems.INFUSION_GUN.get()))
        );
        allCategories.add(new HandInfusionGunCategory(handInfusionGunInfo));

        // ==================== 研钵 · 研磨 ====================
        CreateRecipeCategory.Info<MillingRecipe> mortarMillingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.MORTAR_MILLING,
                Component.translatable("jei.create_hand_made.mortar_milling"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.MORTAR.get()),
                        () -> new ItemStack(AllItems.WHEAT_FLOUR.get())),
                CreateHandMadeJEI::collectMillingRecipes,
                List.of(() -> new ItemStack(ModItems.MORTAR.get()))
        );
        allCategories.add(new MortarMillingCategory(mortarMillingInfo));

        // ==================== 碾钵 · 粉碎 + 研磨 ====================
        // 类型用 AbstractCrushingRecipe（CRUSHING 与 MILLING 的共同父类），
        // 类别显示名仍是"碾钵粉碎"，但内容同时包含两种配方。
        CreateRecipeCategory.Info<AbstractCrushingRecipe> crusherMortarCrushingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.CRUSHER_MORTAR_CRUSHING,
                Component.translatable("jei.create_hand_made.crusher_mortar_crushing"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.CRUSHER_MORTAR.get()),
                        () -> new ItemStack(AllItems.CRUSHED_GOLD.get())),
                CreateHandMadeJEI::collectCrushingRecipes,
                List.of(() -> new ItemStack(ModItems.CRUSHER_MORTAR.get()))
        );
        allCategories.add(new CrusherMortarCrushingCategory(crusherMortarCrushingInfo));

        // ==================== 搅拌杖 · 混合 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_MIXING,
                Component.translatable("jei.create_hand_made.stirring_staff_mixing"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(AllBlocks.BASIN.get())),
                CreateHandMadeJEI::collectMixingRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.standard(stirringStaffInfo));

// ==================== 搅拌杖 · 自动无序合成 ★ 新增 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffAutoShapelessInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_AUTO_SHAPELESS,
                Component.translatable("jei.create_hand_made.stirring_staff_auto_shapeless"),
                new EmptyBackground(177, 85),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(Items.CRAFTING_TABLE)),
                CreateHandMadeJEI::collectAutoShapelessRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.autoShapeless(stirringStaffAutoShapelessInfo));

// ==================== 搅拌杖 · 自动酿造 ★ 新增 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffAutoBrewingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_AUTO_BREWING,
                Component.translatable("jei.create_hand_made.stirring_staff_auto_brewing"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(Items.BREWING_STAND)),
                CreateHandMadeJEI::collectAutoBrewingRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.autoBrewing(stirringStaffAutoBrewingInfo));

        // ==================== 指杆 · 应用（部署 + 物品应用） ====================
        CreateRecipeCategory.Info<ItemApplicationRecipe> pointerApplicationInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.POINTER_APPLICATION,
                Component.translatable("jei.create_hand_made.pointer_application"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.POINTER.get()),
                        () -> new ItemStack(AllBlocks.DEPOT.get())),
                CreateHandMadeJEI::collectPointerRecipes,
                List.of(() -> new ItemStack(ModItems.POINTER.get()))
        );
        allCategories.add(new PointerApplicationCategory(pointerApplicationInfo));

        // ==================== 风箱 · 熔炼 ====================
        CreateRecipeCategory.Info<AbstractCookingRecipe> bellowsBlastingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_BLASTING,
                Component.translatable("jei.create_hand_made.bellows_blasting"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.LAVA_BUCKET)),
                CreateHandMadeJEI::collectBellowsBlastingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsCookingCategory(bellowsBlastingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.BLASTING)));

        // ==================== 风箱 · 烟熏 ====================
        CreateRecipeCategory.Info<AbstractCookingRecipe> bellowsSmokingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_SMOKING,
                Component.translatable("jei.create_hand_made.bellows_smoking"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.CAMPFIRE)),
                CreateHandMadeJEI::collectBellowsSmokingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsCookingCategory(bellowsSmokingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.SMOKING)));

        // ==================== 风箱 · 缠魂 ====================
        CreateRecipeCategory.Info<HauntingRecipe> bellowsHauntingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_HAUNTING,
                Component.translatable("jei.create_hand_made.bellows_haunting"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.SOUL_CAMPFIRE)),
                CreateHandMadeJEI::collectBellowsHauntingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsHauntingCategory(bellowsHauntingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.HAUNTING)));

        // ==================== 风箱 · 洗涤 ====================
        CreateRecipeCategory.Info<SplashingRecipe> bellowsSplashingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_SPLASHING,
                Component.translatable("jei.create_hand_made.bellows_splashing"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.WATER_BUCKET)),
                CreateHandMadeJEI::collectBellowsSplashingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsSplashingCategory(bellowsSplashingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.SPLASHING)));

        registration.addRecipeCategories(allCategories.toArray(CreateRecipeCategory[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        this.ingredientManager = registration.getIngredientManager();
        allCategories.forEach(c -> c.registerRecipes(registration));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));
    }

    // ==================== 配方收集 ====================

    /**
     * 从统一配方池收集某个工具类别的配方，并按目标类型过滤。
     *
     * <p>JEI 与游戏内工具现在共用同一份候选集（{@link HandMadeRecipePool}）：
     * 池负责「收集候选」，本方法只做类型对齐，不再重复任何筛选规则 ——
     * 那些规则（config 开关、MechanicalCraftingRecipe 排除、压缩判定、
     * automation 忽略……）都已经在池里。</p>
     *
     * <p>level 为 null（尚未进入世界）时返回空列表。</p>
     *
     * @param tool  工具 + 配方类型组合
     * @param clazz 目标配方类型，用于过滤与泛型对齐
     * @return 该类别下所有 {@code clazz} 类型的配方
     */
    private static <T extends Recipe<?>> List<RecipeHolder<T>> collectFromPool(HandMadeTool tool, Class<T> clazz) {
        List<RecipeHolder<T>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            // isInstance + cast 是类型安全的，不需要 unchecked 强转。
            if (clazz.isInstance(holder.value())) {
                result.add(new RecipeHolder<>(holder.id(), clazz.cast(holder.value())));
            }
        }
        return result;
    }

    /**
     * 从配方池收集某个工具类别的配方，并把 <b>L3 独占配方</b>代理成类别所需的 Create 配方类型。
     *
     * <p>L3 独占配方按家族使用不同的配方类（basin {@link HandMadeToolRecipe} / 碾磨
     * {@link HandMadeCrushingRecipe} / 冲压 {@link HandMadePressingRecipe} / 切削
     * {@link HandMadeCuttingRecipe}），它们都不是对应 JEI 类别所用的那个具体 Create 类，
     * 所以 {@link #collectFromPool} 的 {@code clazz.isInstance(...)} 会把独占配方丢掉。
     * 本方法在类型不匹配时用同一份 {@link ProcessingRecipeParams} 造一个<b>显示代理</b>
     * 供渲染（代理不会注册进配方管理器，也不参与游戏内匹配）。</p>
     *
     * <p>只有归属工具正好等于本类别工具的独占配方会被代理；配方池本身也按归属过滤，
     * 这里再判一次是为了让本方法不依赖池的实现细节。</p>
     *
     * <p>例外：碾钵类别用的是 {@link AbstractCrushingRecipe}（碾磨家族类的父类），
     * 独占配方天然通过 {@code clazz.isInstance(...)}，因此继续用 {@link #collectFromPool}，
     * 不需要代理。</p>
     *
     * @param tool         工具 + 配方类型组合
     * @param clazz        本类别需要的配方类型
     * @param proxyFactory 用 params 造代理对象的工厂（例如 {@code MillingRecipe::new}）
     */
    private static <T extends Recipe<?>> List<RecipeHolder<T>> collectFromPoolWithProxy(
            HandMadeTool tool, Class<T> clazz, Function<ProcessingRecipeParams, T> proxyFactory) {
        List<RecipeHolder<T>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            Object value = holder.value();

            // isInstance + cast 是类型安全的，不需要 unchecked 强转。
            if (clazz.isInstance(value)) {
                result.add(new RecipeHolder<>(holder.id(), clazz.cast(value)));
                continue;
            }

            if (value instanceof HandMadeToolRecipeLike exclusive
                    && exclusive.getTool() == tool
                    && value instanceof StandardProcessingRecipe<?> processing) {
                result.add(new RecipeHolder<>(holder.id(), proxyFactory.apply(processing.getParams())));
            }
        }
        return result;
    }

    /** 手锯 · 切削（CUTTING）。 */
    private static List<RecipeHolder<CuttingRecipe>> collectHandSawRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.HAND_SAW, CuttingRecipe.class, CuttingRecipe::new);
    }

    /** 冲压锤 · 置物台（PRESSING）。 */
    private static List<RecipeHolder<PressingRecipe>> collectPressingRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.PRESS_HAMMER_DEPOT, PressingRecipe.class, PressingRecipe::new);
    }

    /** 冲压锤 · 工作盆 · 打包（COMPACTING）。 */
    private static List<RecipeHolder<BasinRecipe>> collectCompactingRecipes() {
        return collectFromPool(HandMadeTool.PRESS_HAMMER_BASIN, BasinRecipe.class);
    }

    /**
     * 冲压锤 · 工作盆 · 自动摆放（4/9 合 1）。
     *
     * <p>配方池的 {@link HandMadeTool#PRESS_HAMMER_AUTO_SQUARE} 只提供「可压缩」的
     * CraftingRecipe（config 开关、压缩判定、automation 忽略都已在池里），
     * 这里只负责把它包成 BasinRecipe 以便 BasinCategory 渲染。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> collectAutoSquareRecipes() {
        return wrapAsBasin(HandMadeTool.PRESS_HAMMER_AUTO_SQUARE);
    }

    /** 灌注枪 · 注液（FILLING），再追加 SpoutCategory 的转换配方。 */
    private List<RecipeHolder<FillingRecipe>> collectInfusionGunRecipes() {
        List<RecipeHolder<FillingRecipe>> result =
                collectFromPoolWithProxy(HandMadeTool.INFUSION_GUN, FillingRecipe.class, FillingRecipe::new);

        if (ingredientManager != null) {
            SpoutCategory.consumeRecipes(result::add, ingredientManager);
        }

        return result;
    }

    /**
     * 研钵 · 研磨（MILLING）。
     *
     * <p>候选集来自配方池，池里除了 Create 的 {@link MillingRecipe}，还可能有 L3 独占配方
     * {@link HandMadeCrushingRecipe}（碾磨家族，它是 {@code AbstractCrushingRecipe} 的子类，
     * <b>不是</b> {@code MillingRecipe} 的子类）。本类别的类型参数是 {@code MillingRecipe}，
     * 若直接走 {@code collectFromPool}，它的 {@code clazz.isInstance(...)} 会把独占配方丢掉，
     * 所以这里对独占配方做一次<b>显示代理</b>：用同一份 {@code ProcessingRecipeParams}
     * 造一个 {@code MillingRecipe} 供渲染。</p>
     *
     * <p>代理对象只服务于 JEI 展示（{@code MillingRecipe} 的构造是 public，
     * {@code ProcessingRecipe.getParams()} 也是 public），不会注册进配方管理器，
     * 也不参与游戏内匹配。</p>
     */
    private static List<RecipeHolder<MillingRecipe>> collectMillingRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.MORTAR, MillingRecipe.class, MillingRecipe::new);
    }

    /**
     * 碾钵 · 粉碎 + 研磨。
     *
     * <p>配方池的 {@link HandMadeTool#CRUSHER_MORTAR} 本来就把 CRUSHING 与 MILLING
     * 都收进来了（游戏内是「先粉碎、后研磨」，池里 CRUSHING 在前、MILLING 在后）。
     * 这里按 {@link AbstractCrushingRecipe} 过滤 —— 它是两者的共同父类，
     * 因此两类都会进本类别，且保持"粉碎在前"的顺序。</p>
     */
    private static List<RecipeHolder<AbstractCrushingRecipe>> collectCrushingRecipes() {
        return collectFromPool(HandMadeTool.CRUSHER_MORTAR, AbstractCrushingRecipe.class);
    }

    /** 搅拌杖 · 混合（MIXING）。 */
    private static List<RecipeHolder<BasinRecipe>> collectMixingRecipes() {
        return collectFromPool(HandMadeTool.STIRRING_STAFF, BasinRecipe.class);
    }
    /**
     * 搅拌杖 · 自动无序合成。
     *
     * <p>配方池的 {@link HandMadeTool#STIRRING_STAFF_AUTO_SHAPELESS} 只提供
     * 符合条件的无序 CraftingRecipe（config 开关、ShapedRecipe 排除、原料数、
     * 压缩判定、automation 忽略都已在池里），这里只负责包成 BasinRecipe 以便渲染。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> collectAutoShapelessRecipes() {
        return wrapAsBasin(HandMadeTool.STIRRING_STAFF_AUTO_SHAPELESS);
    }

    /**
     * 搅拌杖 · 自动酿造。
     *
     * <p>直接消费配方池的 {@link HandMadeTool#STIRRING_STAFF_AUTO_BREWING}
     * —— 池内部已经做完 {@code PotionMixingRecipes.createRecipes} 与泛型上推，
     * 并含 {@code allowBrewingInMixer} 开关判断。这里只剩一次类型对齐，
     * JEI 侧的 unchecked cast 是必要的。</p>
     *
     * <p>注意：游戏内的自动酿造<b>不走</b>这条路径 —— 它用
     * {@code PotionMixingRecipes.sortRecipesByItem} 的按物品索引直查，
     * 池里的这一条只服务于本类别。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<BasinRecipe>> collectAutoBrewingRecipes() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return (List) HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF_AUTO_BREWING, level);
    }

    /** 指杆 · 应用（DEPLOYING + ITEM_APPLICATION，前者在前）。 */
    private static List<RecipeHolder<ItemApplicationRecipe>> collectPointerRecipes() {
        return collectFromPool(HandMadeTool.POINTER, ItemApplicationRecipe.class);
    }

    /**
     * 把配方池里某一类「工作台配方」包装成 {@link BasinRecipe}，供 BasinCategory 渲染。
     *
     * <p>配方池刻意返回原始 {@link CraftingRecipe} 而不是 BasinRecipe
     * （见 {@link HandMadeRecipePool} 里的说明：包装会让 {@code getRemainingItems}
     * 走 BasinRecipe 的默认实现，与游戏内原本的容器残留返还行为不一致）。
     * 包装只为 JEI 展示服务，所以放在这里做，且只在客户端执行
     * （{@code BasinRecipe.convertShapeless} 内部使用
     * {@code Minecraft.getInstance().level}）。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> wrapAsBasin(HandMadeTool tool) {
        List<RecipeHolder<BasinRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            result.add(BasinRecipe.convertShapeless(holder));
        }
        return result;
    }

    /**
     * 风箱 · 熔炼。
     *
     * <p>与 Create 原版「鼓风熔炼」类别（{@code CreateJEI} 里的 {@code fan_blasting}）
     * 的配方集完全一致，逐步对应它的 builder 链：</p>
     * <ol>
     *   <li>{@code addTypedRecipesExcluding(SMELTING, BLASTING)} —— 熔炼表里
     *       「输入与某条 BLASTING 相同」的跳过；</li>
     *   <li>{@code addTypedRecipes(BLASTING)} —— 再补上全部 BLASTING；</li>
     *   <li>{@code removeRecipes(SMOKING)} —— 输入与输出都与某条 SMOKING 相同的移除，
     *       <b>这一步才是把食物（生牛肉 → 熟牛肉之类）排掉的关键</b>；</li>
     *   <li>{@code removeNonAutomation()} —— 移除 id 以 {@code _manual_only} 结尾的配方。</li>
     * </ol>
     *
     * <p>前两步的顺序也照 Create 保留：SMELTING（已排除者）在前，BLASTING 在后。</p>
     *
     * <p><b>第 4 步与 Create 逐字一致：</b>Create 的 {@code removeNonAutomation()} 实现是
     * {@code recipes.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate())}，
     * 只看配方 id 的 {@code _manual_only} 后缀；而 {@code shouldIgnoreInAutomation}
     * 还会额外看 serializer 的 AUTOMATION_IGNORE 标签。因此这里用的是前者。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<AbstractCookingRecipe>> collectBellowsBlastingRecipes() {
        List<RecipeHolder<AbstractCookingRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        // 1. BLASTING 全表：既用作排除参照，也是最终结果的一部分。
        List<RecipeHolder<AbstractCookingRecipe>> blastingAll = new ArrayList<>();
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING)) {
            blastingAll.add((RecipeHolder) h);
        }

        // 2. SMELTING 里排除「输入与某条 BLASTING 相同」的。
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            boolean excluded = false;
            for (RecipeHolder<AbstractCookingRecipe> b : blastingAll) {
                if (CreateJEI.doInputsMatch(h.value(), b.value())) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                result.add((RecipeHolder) h);
            }
        }

        // 3. 加上全部 BLASTING。
        result.addAll(blastingAll);

        // 4. 移除「输入与输出都与某条 SMOKING 相同」的 —— 食物就是在这里被排掉的。
        List<RecipeHolder<AbstractCookingRecipe>> smokingAll = new ArrayList<>();
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING)) {
            smokingAll.add((RecipeHolder) h);
        }
        result.removeIf(r -> {
            for (RecipeHolder<AbstractCookingRecipe> s : smokingAll) {
                if (CreateJEI.doInputsMatch(r.value(), s.value())
                        && CreateJEI.doOutputsMatch(r.value(), s.value())) {
                    return true;
                }
            }
            return false;
        });

        // 5. 移除 non-automation（与 Create 的 removeNonAutomation 逐字一致）。
        result.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate());

        return result;
    }

    /**
     * 风箱 · 烟熏。
     *
     * <p>对应 Create 原版 {@code fan_smoking}：
     * {@code addTypedRecipes(SMOKING)} 之后还有一步 {@code removeNonAutomation()}。
     * 原实现只做了前半步，这里补上后半步，与 Create 一致
     * （同样用 {@code CAN_BE_AUTOMATED.negate()}，即只按 {@code _manual_only} 后缀过滤）。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<AbstractCookingRecipe>> collectBellowsSmokingRecipes() {
        List<RecipeHolder<AbstractCookingRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING)) {
            result.add((RecipeHolder) h);
        }
        result.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate());
        return result;
    }

    private static List<RecipeHolder<HauntingRecipe>> collectBellowsHauntingRecipes() {
        List<RecipeHolder<HauntingRecipe>> result = new ArrayList<>();
        List<RecipeHolder<?>> all = CreateJEI.getTypedRecipes(AllRecipeTypes.HAUNTING.getType());
        for (RecipeHolder<?> h : all) {
            if (h.value() instanceof HauntingRecipe r) {
                result.add(new RecipeHolder<>(h.id(), r));
            }
        }
        return result;
    }

    private static List<RecipeHolder<SplashingRecipe>> collectBellowsSplashingRecipes() {
        List<RecipeHolder<SplashingRecipe>> result = new ArrayList<>();
        List<RecipeHolder<?>> all = CreateJEI.getTypedRecipes(AllRecipeTypes.SPLASHING.getType());
        for (RecipeHolder<?> h : all) {
            if (h.value() instanceof SplashingRecipe r) {
                result.add(new RecipeHolder<>(h.id(), r));
            }
        }
        return result;
    }
}