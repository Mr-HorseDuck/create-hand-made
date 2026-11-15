package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.compat.jei.category.BellowsCookingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsHauntingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsSplashingCategory;
import com.alben.createhandmade.compat.jei.category.CrusherMortarCrushingCategory;
import com.alben.createhandmade.compat.jei.category.HandInfusionGunCategory;
import com.alben.createhandmade.compat.jei.category.HandPressBasinCategory;
import com.alben.createhandmade.compat.jei.category.HandPressDepotCategory;
import com.alben.createhandmade.compat.jei.category.HandSawCategory;
import com.alben.createhandmade.compat.jei.category.HandSawStonecuttingCategory;
import com.alben.createhandmade.compat.jei.category.MortarMillingCategory;
import com.alben.createhandmade.compat.jei.category.PointerApplicationCategory;
import com.alben.createhandmade.compat.jei.category.StirringStaffMixingCategory;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.FanType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * 本模组的 JEI 插件入口。
 *
 * <p><b>加载方式</b>：由 {@code fabric.mod.json} 的 {@code "jei_mod_plugin"} entrypoint
 * 拉起来（键名与 Create Fly 自己的 {@code fabric.mod.json} 一致）。因此
 * {@code depends} 里<b>不</b>声明 {@code jei}：没装 JEI 时这个类根本不会被加载，
 * 反向也要求 {@link CreateHandMade} / {@code CreateHandMadeClient} <b>不得</b>引用
 * {@code compat.jei.*}，否则未装 JEI 的客户端会 NoClassDefFoundError。</p>
 *
 * <p><b>当前进度（最终）</b>：16 个 {@code IRecipeType} 里已注册 <b>14 个类别</b> ——
 * 切片 2（单输入多产物家族 7 类：手锯切削、手锯切石、研钵研磨、碾钵粉碎、冲压锤置物台、
 * 风箱缠魂、风箱洗涤）+ 切片 3（工作盆家族 3 类 + 指杆 1 类：冲压锤工作盆、搅拌杖混合、
 * 搅拌杖自动酿造、指杆应用）+ 切片 4（归一化家族 3 类：风箱熔炼、风箱烟熏、灌注枪）。
 * 剩下 2 个（{@code HAND_PRESS_BASIN_AUTO_SQUARE} / {@code STIRRING_STAFF_AUTO_SHAPELESS}）
 * 因 R5 未注册 —— 判定依据见 {@link #registerCategories} 里的注释。</p>
 *
 * <p><b>注册的三段约定</b>：</p>
 * <ol>
 *   <li>{@code registerCategories}：每个类型一个类别实例；</li>
 *   <li>{@code registerRecipes}：配方一律由类别的静态 {@code getRecipes()} 提供，而那些方法
 *       又只调 {@link JeiRecipeSource}（唯一配方来源）—— 本类<b>不</b>自己抓配方；</li>
 *   <li>{@code registerRecipeCatalysts}：给每个类别挂本模组的工具物品，这样在 JEI 里点亮
 *       「手锯」「研钵」等物品就能反查对应类别。</li>
 * </ol>
 */
@JeiPlugin
public class CreateHandMadeJEI implements IModPlugin {

    /** 插件 UID：与模组 ID 同名，保证 JEI 配置/调试信息里可读。 */
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "jei_plugin");

    // 类别实例：注册时直接复用（除 BellowsCookingCategory 的 fanType / 介质字段外都无状态，
    // 文档在各自己类里）。
    private static final HandSawCategory HAND_SAW = new HandSawCategory();
    private static final HandSawStonecuttingCategory HAND_SAW_STONECUTTING =
            new HandSawStonecuttingCategory();
    private static final MortarMillingCategory MORTAR_MILLING = new MortarMillingCategory();
    private static final CrusherMortarCrushingCategory CRUSHER_MORTAR_CRUSHING =
            new CrusherMortarCrushingCategory();
    private static final HandPressDepotCategory HAND_PRESS_DEPOT = new HandPressDepotCategory();
    private static final BellowsHauntingCategory BELLOWS_HAUNTING = new BellowsHauntingCategory();
    private static final BellowsSplashingCategory BELLOWS_SPLASHING = new BellowsSplashingCategory();
    private static final HandPressBasinCategory HAND_PRESS_BASIN = new HandPressBasinCategory();
    private static final StirringStaffMixingCategory STIRRING_STAFF_MIXING =
            new StirringStaffMixingCategory();
    private static final StirringStaffMixingCategory.Brewing STIRRING_STAFF_AUTO_BREWING =
            new StirringStaffMixingCategory.Brewing();
    private static final PointerApplicationCategory POINTER_APPLICATION =
            new PointerApplicationCategory();
    // 归一化家族（切片 4）：一个类两个实例，靠 fanType + 介质图标区分。
    private static final BellowsCookingCategory BELLOWS_BLASTING =
            new BellowsCookingCategory(FanType.BLASTING, Items.LAVA_BUCKET);
    private static final BellowsCookingCategory BELLOWS_SMOKING =
            new BellowsCookingCategory(FanType.SMOKING, Items.CAMPFIRE);
    private static final HandInfusionGunCategory HAND_INFUSION_GUN = new HandInfusionGunCategory();

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /**
     * 注册配方类别。
     *
     * <p>切片 2 交付的 7 个：手锯切削、手锯切石、研钵研磨、碾钵粉碎、冲压锤置物台、
     * 风箱缠魂、风箱洗涤；切片 3 追加 4 个：冲压锤工作盆、搅拌杖混合、搅拌杖自动酿造、指杆应用
     * （这 4 个由 lead 手动补注册，见 PLAN 的「C 切片3 / 切片4 变更记录」）；切片 4 追加 3 个：
     * 风箱熔炼、风箱烟熏、灌注枪。{@code addRecipeCategories} 是变长参数，可以直接把实例传进去。</p>
     *
     * <p><b>R5：26.1 无工作台→工作盆转换入口，待后续</b> —— 所以
     * {@code HAND_PRESS_BASIN_AUTO_SQUARE}（#5 自动摆放）与
     * {@code STIRRING_STAFF_AUTO_SHAPELESS}（#10 自动无序合成）两个类型<b>本期不注册</b>
     * （{@link ModJeiTypes} 里两个常量保留，UID 留给以后）。判定依据（{@code javap -p} 实测
     * {@code com.zurrtum.create.content.processing.basin.BasinRecipe} 的<b>全部</b>成员）：
     * 静态方法只有 {@code matchCraftingRecipe(BasinInput, ShapelessRecipe|ShapedRecipe, Level)}、
     * {@code applyCraftingRecipe(...)}、{@code tryCraft(...)}、{@code prepareCraft(...)}、
     * {@code prepareFluidCraft(...)}、{@code addRecipeRemainder(...)}，字段只有
     * {@code SHAPED_CACHE} / {@code SHAPELESS_CACHE}（都是
     * {@code Map<CraftingRecipe, List<SizedIngredient>>} 缓存）——
     * <b>没有任何 {@code convertShaped} / {@code convertShapeless} 之类的
     * 「工作台配方 → {@code BasinRecipe}」转换方法</b>（上游 NeoForge 1.21.1 有）。而这两个类型的
     * 泛型是 {@code RecipeHolder<BasinRecipe>}，需要的是一个 {@code BasinRecipe} 实例；
     * {@code CraftingRecipe} 并不实现它，26.1 也没有把工作台配方包成 {@code BasinRecipe} 的入口。
     * 不许自造 3×3 布局或自造 {@code BasinRecipe} 包装：那会显示游戏里并不存在的配方。
     * 游戏内这两个能力（冲压锤压 4/9 合 1、搅拌杖跑无序合成）本身仍照常工作，
     * JEI 里玩家可以看原版合成类别。</p>
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                HAND_SAW,
                HAND_SAW_STONECUTTING,
                MORTAR_MILLING,
                CRUSHER_MORTAR_CRUSHING,
                HAND_PRESS_DEPOT,
                BELLOWS_HAUNTING,
                BELLOWS_SPLASHING,
                HAND_PRESS_BASIN,
                STIRRING_STAFF_MIXING,
                STIRRING_STAFF_AUTO_BREWING,
                POINTER_APPLICATION,
                BELLOWS_BLASTING,
                BELLOWS_SMOKING,
                HAND_INFUSION_GUN);
    }

    /**
     * 注册各类别的配方集合。
     *
     * <p>{@code addRecipes} 收的是 {@code java.util.List}（不是 {@code Collection}），
     * 而各家族的 {@code getRecipes()} 已经返回 {@code List}，直接传即可。</p>
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ModJeiTypes.HAND_SAW_CUTTING, HandSawCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.HAND_SAW_STONECUTTING,
                HandSawStonecuttingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.MORTAR_MILLING, MortarMillingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.CRUSHER_MORTAR_CRUSHING,
                CrusherMortarCrushingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.HAND_PRESS_DEPOT, HandPressDepotCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.BELLOWS_HAUNTING, BellowsHauntingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.BELLOWS_SPLASHING, BellowsSplashingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.HAND_PRESS_BASIN, HandPressBasinCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.STIRRING_STAFF_MIXING,
                StirringStaffMixingCategory.getRecipes());
        registration.addRecipes(ModJeiTypes.STIRRING_STAFF_AUTO_BREWING,
                StirringStaffMixingCategory.Brewing.getRecipes());
        registration.addRecipes(ModJeiTypes.POINTER_APPLICATION,
                PointerApplicationCategory.getRecipes());
        // 归一化家族：熔炼 / 烟熏共用 BellowsCookingCategory，靠 FanType 决定数据源与显示类型。
        registration.addRecipes(ModJeiTypes.BELLOWS_BLASTING,
                BellowsCookingCategory.getRecipes(FanType.BLASTING));
        registration.addRecipes(ModJeiTypes.BELLOWS_SMOKING,
                BellowsCookingCategory.getRecipes(FanType.SMOKING));
        registration.addRecipes(ModJeiTypes.HAND_INFUSION_GUN, HandInfusionGunCategory.getRecipes());
    }

    /**
     * 注册「催化剂」：让玩家点游戏内物品能反查到手作工具的类别。
     *
     * <p>手锯两个类别（切削 / 切石）共用同一个物品 —— 就像游戏里同一个手锯既能切削也能切石。
     * 风箱四个类别（熔炼 / 烟熏 / 缠魂 / 洗涤）同理，都挂 {@code ModItems.BELLOWS}；
     * 灌注枪挂 {@code ModItems.INFUSION_GUN}。</p>
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ModJeiTypes.HAND_SAW_CUTTING, ModItems.HAND_SAW);
        registration.addCraftingStation(ModJeiTypes.HAND_SAW_STONECUTTING, ModItems.HAND_SAW);
        registration.addCraftingStation(ModJeiTypes.MORTAR_MILLING, ModItems.MORTAR);
        registration.addCraftingStation(ModJeiTypes.CRUSHER_MORTAR_CRUSHING, ModItems.CRUSHER_MORTAR);
        registration.addCraftingStation(ModJeiTypes.HAND_PRESS_DEPOT, ModItems.PRESS_HAMMER);
        registration.addCraftingStation(ModJeiTypes.BELLOWS_HAUNTING, ModItems.BELLOWS);
        registration.addCraftingStation(ModJeiTypes.BELLOWS_SPLASHING, ModItems.BELLOWS);
        registration.addCraftingStation(ModJeiTypes.HAND_PRESS_BASIN, ModItems.PRESS_HAMMER);
        registration.addCraftingStation(ModJeiTypes.STIRRING_STAFF_MIXING, ModItems.STIRRING_STAFF);
        registration.addCraftingStation(ModJeiTypes.STIRRING_STAFF_AUTO_BREWING,
                ModItems.STIRRING_STAFF);
        registration.addCraftingStation(ModJeiTypes.POINTER_APPLICATION, ModItems.POINTER);
        registration.addCraftingStation(ModJeiTypes.BELLOWS_BLASTING, ModItems.BELLOWS);
        registration.addCraftingStation(ModJeiTypes.BELLOWS_SMOKING, ModItems.BELLOWS);
        registration.addCraftingStation(ModJeiTypes.HAND_INFUSION_GUN, ModItems.INFUSION_GUN);
    }
}
