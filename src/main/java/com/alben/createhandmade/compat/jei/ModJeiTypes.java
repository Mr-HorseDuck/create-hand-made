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
    /** 风箱 · 第三方兼容（fan_type 属于第三方模组的 bellows_recipe） */
        @SuppressWarnings("unchecked")
        public static final RecipeType<ProcessingRecipe<RecipeWrapper>> BELLOWS_THIRD_PARTY =
                RecipeType.create("create_hand_made", "bellows_third_party",
                        (Class<ProcessingRecipe<RecipeWrapper>>) (Class<?>) ProcessingRecipe.class);
}