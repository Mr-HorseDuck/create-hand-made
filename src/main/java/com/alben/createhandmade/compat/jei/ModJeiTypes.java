package com.alben.createhandmade.compat.jei;

import com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ModJeiTypes {

    public static final RecipeType<RecipeHolder<CuttingRecipe>> HAND_SAW_CUTTING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_saw_cutting"));

    /**
     * 手锯 · 切石（原版 {@code minecraft:stonecutting} + 本模组的 L3
     * {@code create_hand_made:stonecutting_recipe}）。
     *
     * <p><b>泛型为什么绑 {@link CondensedBlockCuttingRecipe}：</b>本类别复用的是 Create 的
     * {@code BlockCuttingCategory}（{@code CreateRecipeCategory<CondensedBlockCuttingRecipe>}），
     * 而 {@code CreateRecipeCategory.Info<T>} 要求
     * {@code RecipeType<RecipeHolder<T>>} 与收集器的 {@code Supplier<List<RecipeHolder<T>>>}
     * 泛型一致。所以这里绑的是<b>显示类型</b>（Create 的折叠配方类），不是本模组自己的
     * {@code HandMadeStonecuttingRecipe} —— 后者由收集器在运行时经
     * {@code BlockCuttingCategory.condenseRecipes(...)} 折进前者。</p>
     */
    public static final RecipeType<RecipeHolder<CondensedBlockCuttingRecipe>> HAND_SAW_STONECUTTING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_saw_stonecutting"));

    /** 冲压锤 · 置物台 / 传送带 */
    public static final RecipeType<RecipeHolder<PressingRecipe>> HAND_PRESS_DEPOT =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_press_depot"));

    /** 冲压锤 · 工作盆 · 打包（COMPACTING） */
    public static final RecipeType<RecipeHolder<BasinRecipe>> HAND_PRESS_BASIN =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_press_basin"));

    /** 冲压锤 · 工作盆 · 自动摆放（4/9 合 1） ★ 新增 */
    public static final RecipeType<RecipeHolder<BasinRecipe>> HAND_PRESS_BASIN_AUTO_SQUARE =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_press_basin_auto_square"));

    /** 灌注枪 · 注液 */
    public static final RecipeType<RecipeHolder<FillingRecipe>> HAND_INFUSION_GUN =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "hand_infusion_gun"));

    /** 研钵 · 研磨 */
    public static final RecipeType<RecipeHolder<MillingRecipe>> MORTAR_MILLING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "mortar_milling"));

    /**
     * 碾钵 · 粉碎 + 研磨。
     *
     * <p>类型用 {@link AbstractCrushingRecipe} 而不是 {@code CrushingRecipe}：
     * 它是 CRUSHING 与 MILLING 两类的共同父类（Create 自己的
     * {@code CrushingCategory} / {@code MillingCategory} 用的也是它），
     * 这样本类别能同时容纳两种配方 —— 与游戏内碾钵「先粉碎、后研磨」的实际能力一致。
     * UID 保持不变。</p>
     */
    public static final RecipeType<RecipeHolder<AbstractCrushingRecipe>> CRUSHER_MORTAR_CRUSHING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "crusher_mortar_crushing"));

    /** 搅拌杖 · 混合（MIXING） */
    public static final RecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_MIXING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "stirring_staff_mixing"));

    /** 搅拌杖 · 自动无序合成 ★ 新增 */
    public static final RecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_AUTO_SHAPELESS =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "stirring_staff_auto_shapeless"));

    /** 搅拌杖 · 自动酿造 ★ 新增 */
    public static final RecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_AUTO_BREWING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "stirring_staff_auto_brewing"));

    /** 指杆 · 物品应用 */
    public static final RecipeType<RecipeHolder<ItemApplicationRecipe>> POINTER_APPLICATION =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "pointer_application"));

    /** 风箱 · 熔炼 */
    public static final RecipeType<RecipeHolder<AbstractCookingRecipe>> BELLOWS_BLASTING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "bellows_blasting"));

    /** 风箱 · 烟熏 */
    public static final RecipeType<RecipeHolder<AbstractCookingRecipe>> BELLOWS_SMOKING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "bellows_smoking"));

    /** 风箱 · 缠魂 */
    public static final RecipeType<RecipeHolder<HauntingRecipe>> BELLOWS_HAUNTING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "bellows_haunting"));

    /** 风箱 · 洗涤 */
    public static final RecipeType<RecipeHolder<SplashingRecipe>> BELLOWS_SPLASHING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_hand_made", "bellows_splashing"));
}