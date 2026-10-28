package com.alben.createhandmade.compat.jei;

import com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;

/**
 * ★ Forge 1.20.1 版本。
 *
 * 泛型使用 Create 原配方类作为公共父类型：
 *   - HandMadeCuttingRecipe        extends CuttingRecipe
 *   - HandMadePressingRecipe       extends PressingRecipe
 *   - HandMadeFillingRecipe        extends FillingRecipe
 *   - HandMadeApplicationRecipe    extends ItemApplicationRecipe
 *   - HandMadeCrushingRecipe       extends AbstractCrushingRecipe
 *   - HandMadeToolRecipe（basin）  extends BasinRecipe
 * 因此 JEI 类别泛型无需升到 ProcessingRecipe<RecipeWrapper>。
 */
public class ModJeiTypes {

    /** 手锯切削（L1 Cutting + L3 HandMadeCuttingRecipe） */
    public static final RecipeType<CuttingRecipe> HAND_SAW_CUTTING =
            RecipeType.create("create_hand_made", "hand_saw_cutting", CuttingRecipe.class);

    /** 手锯 · 切石（condensed，一页最多 15 个产物） */
    public static final RecipeType<CondensedBlockCuttingRecipe> HAND_SAW_STONECUTTING =
            RecipeType.create("create_hand_made", "hand_saw_stonecutting",
                    CondensedBlockCuttingRecipe.class);

    /** 冲压锤 · 置物台 / 传送带（L1 Pressing + L3 HandMadePressingRecipe） */
    public static final RecipeType<PressingRecipe> HAND_PRESS_DEPOT =
            RecipeType.create("create_hand_made", "hand_press_depot", PressingRecipe.class);

    /** 冲压锤 · 工作盆 · 打包（COMPACTING） */
    public static final RecipeType<BasinRecipe> HAND_PRESS_BASIN =
            RecipeType.create("create_hand_made", "hand_press_basin", BasinRecipe.class);

    /** 冲压锤 · 工作盆 · 自动摆放（4/9 合 1） */
    public static final RecipeType<BasinRecipe> HAND_PRESS_BASIN_AUTO_SQUARE =
            RecipeType.create("create_hand_made", "hand_press_basin_auto_square", BasinRecipe.class);

    /** 灌注枪 · 注液（L1 Filling + L3 HandMadeFillingRecipe） */
    public static final RecipeType<FillingRecipe> HAND_INFUSION_GUN =
            RecipeType.create("create_hand_made", "hand_infusion_gun", FillingRecipe.class);

    /**
     * 研钵 · 研磨（L1 Milling + L3 HandMadeCrushingRecipe）
     * 公共父类是 AbstractCrushingRecipe。
     */
    public static final RecipeType<AbstractCrushingRecipe> MORTAR_MILLING =
            RecipeType.create("create_hand_made", "mortar_milling",
                    AbstractCrushingRecipe.class);

    /**
     * 碾钵 · 粉碎
     * 同时容纳 CrushingRecipe / MillingRecipe / HandMadeCrushingRecipe，
     * 公共父类是 AbstractCrushingRecipe。
     */
    public static final RecipeType<AbstractCrushingRecipe> CRUSHER_MORTAR_CRUSHING =
            RecipeType.create("create_hand_made", "crusher_mortar_crushing",
                    AbstractCrushingRecipe.class);

    /** 搅拌杖 · 混合（MIXING） */
    public static final RecipeType<BasinRecipe> STIRRING_STAFF_MIXING =
            RecipeType.create("create_hand_made", "stirring_staff_mixing", BasinRecipe.class);

    /** 搅拌杖 · 自动无序合成 */
    public static final RecipeType<BasinRecipe> STIRRING_STAFF_AUTO_SHAPELESS =
            RecipeType.create("create_hand_made", "stirring_staff_auto_shapeless", BasinRecipe.class);

    /** 搅拌杖 · 自动酿造 */
    public static final RecipeType<BasinRecipe> STIRRING_STAFF_AUTO_BREWING =
            RecipeType.create("create_hand_made", "stirring_staff_auto_brewing", BasinRecipe.class);

    /** 指杆 · 物品应用（L1 ItemApplication + L3 HandMadeApplicationRecipe） */
    public static final RecipeType<ItemApplicationRecipe> POINTER_APPLICATION =
            RecipeType.create("create_hand_made", "pointer_application", ItemApplicationRecipe.class);

    /** 风箱 · 熔炼 */
    public static final RecipeType<AbstractCookingRecipe> BELLOWS_BLASTING =
            RecipeType.create("create_hand_made", "bellows_blasting", AbstractCookingRecipe.class);

    /** 风箱 · 烟熏 */
    public static final RecipeType<AbstractCookingRecipe> BELLOWS_SMOKING =
            RecipeType.create("create_hand_made", "bellows_smoking", AbstractCookingRecipe.class);

    /** 风箱 · 缠魂 */
    public static final RecipeType<HauntingRecipe> BELLOWS_HAUNTING =
            RecipeType.create("create_hand_made", "bellows_haunting", HauntingRecipe.class);

    /** 风箱 · 洗涤 */
    public static final RecipeType<SplashingRecipe> BELLOWS_SPLASHING =
            RecipeType.create("create_hand_made", "bellows_splashing", SplashingRecipe.class);
                        (Class<ProcessingRecipe<RecipeWrapper>>) (Class<?>) ProcessingRecipe.class);
    /**
 * 风箱 · 第三方兼容：{@code create_hand_made:bellows_recipe} 里
 * {@code fan_type} 属于<b>第三方模组</b>（命名空间不是 {@code create}）的配方。
 *
 * <p>与上面 4 个 {@code BELLOWS_*} 的区别：那 4 个绑定的是 Create 的配方类
 * （{@code AbstractCookingRecipe} / {@code HauntingRecipe} / {@code SplashingRecipe}），
 * 因为它们要复用 Create 的类别布局、并把本模组的 L3 配方包成显示代理；
 * 本类别直接以 {@code HandMadeBellowsRecipe} 为泛型 —— 它本身是
 * {@code ProcessingRecipe} 子类，能自己提供原料与产物，无需代理。</p>
 *
 * <p>原版 4 种类型<b>不收</b>：它们在各自的类别里已经显示过，重复收会变成两页同样的配方。</p>
 */
public static final RecipeType<HandMadeBellowsRecipe> BELLOWS_EXCLUSIVE =
        RecipeType.create(
                "create_hand_made", "bellows_exclusive",
                HandMadeBellowsRecipe.class);
}
