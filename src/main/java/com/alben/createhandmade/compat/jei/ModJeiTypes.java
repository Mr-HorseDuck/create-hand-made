package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.compat.jei.display.BellowsCookingDisplay;
import com.zurrtum.create.client.compat.jei.display.BlockCuttingDisplay;
import com.zurrtum.create.content.fluids.transfer.FillingInput;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.foundation.recipe.CreateRecipe;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 本模组在 JEI 里注册的 16 个配方类别类型。
 *
 * <p><b>UID 必须与上游（NeoForge 1.21.1 版）逐字一致</b>：JEI 的收藏/书签/历史按
 * {@code 命名空间:路径} 记录，改一个字符就会让老玩家的收藏失效。常量名同样照抄上游，
 * 方便与上游对照维护。</p>
 *
 * <p><b>泛型里绑的类</b>是「显示类型」（构造该类别时喂给 JEI 的配方对象类型），不是
 * 必须能直接喂进去的数据源类型 —— 数据源到显示类型的归一化发生在各分类的收集器里。
 * 26.1 的 Create Fly 没有上游那些旧类名，等价替换见各常量注释。</p>
 */
public final class ModJeiTypes {

    private ModJeiTypes() {
    }

    /** 类别类型 UID 的命名空间（= 模组 ID）。 */
    private static final String MODID = CreateHandMade.MODID;

    // ==================== 手锯 ====================

    /**
     * 手锯 · 切削：源 {@code AllRecipeTypes.CUTTING} + 本模组 L3 {@code HandMadeCuttingRecipe}。
     *
     * <p>泛型绑的是<b>共同父接口</b> {@link CreateSingleStackRollableRecipe} 而不是具体的
     * {@code CuttingRecipe}：L3 的 {@code HandMadeCuttingRecipe} 也 implements 这个接口，
     * 而 JEI 的 {@code IRecipeType<T>} 是不变型 —— 绑具体 record 的话 L3 配方塞不进去。
     * {@code CRUSHER_MORTAR_CRUSHING} 同理（它本来就是为归一化 CRUSHING/MILLING 建的）。</p>
     */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> HAND_SAW_CUTTING =
            holderType("hand_saw_cutting");

    /**
     * 手锯 · 切石（原版 {@code minecraft:stonecutting} + 本模组 L3
     * {@code create_hand_made:stonecutting_recipe}）。
     *
     * <p>上游这里绑的是 {@code BlockCuttingCategory.CondensedBlockCuttingRecipe}（Create 的
     * 折叠配方类），26.1 改成 record {@link BlockCuttingDisplay}：折叠逻辑仍写在类别内部
     * （照抄 26.1 {@code BlockCuttingCategory.getRecipes} 的 map 折叠 + 15 槽上限），
     * 折叠产物就是这些 display 实例。</p>
     *
     * <p>注意这里<b>没有</b> {@code RecipeHolder} 包装：{@code RecipeHolder<T>} 的 T 有
     * {@code extends Recipe<?>} 上界，而 {@code BlockCuttingDisplay} 是纯显示 record、不是
     * 配方，装不进 holder。Create 自己的 {@code BLOCK_CUTTING} 类型同样直接绑 display 类型。</p>
     */
    public static final IRecipeType<BlockCuttingDisplay> HAND_SAW_STONECUTTING =
            displayType("hand_saw_stonecutting", BlockCuttingDisplay.class);

    // ==================== 冲压锤 ====================

    /** 冲压锤 · 置物台 / 传送带（PRESSING）+ 本模组 L3 {@code HandMadePressingRecipe}。 */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> HAND_PRESS_DEPOT =
            holderType("hand_press_depot");

    /** 冲压锤 · 工作盆 · 打包（COMPACTING）+ 本模组 L3 {@code HandMadeToolRecipe}。 */
    public static final IRecipeType<RecipeHolder<BasinRecipe>> HAND_PRESS_BASIN =
            holderType("hand_press_basin");

    /** 冲压锤 · 工作盆 · 自动摆放（4/9 合 1）★ 新增。 */
    public static final IRecipeType<RecipeHolder<BasinRecipe>> HAND_PRESS_BASIN_AUTO_SQUARE =
            holderType("hand_press_basin_auto_square");

    // ==================== 灌注枪 ====================

    /**
     * 灌注枪 · 注液（FILLING）+ 本模组 L3 {@code HandMadeFillingRecipe}。
     *
     * <p>泛型绑的是<b>接口</b> {@code CreateRecipe<FillingInput>} 而不是具体的
     * {@code FillingRecipe}：26.1 的 {@code FillingRecipe}（L1）与本模组 L3
     * {@code HandMadeFillingRecipe} 都 {@code implements CreateRecipe<FillingInput>}
     * （{@code javap} 实测），而 JEI 的 {@code IRecipeType<T>} 是不变型 —— 绑具体 record 的话
     * L3 配方塞不进去（切片 1 绑 {@code RecipeHolder<FillingRecipe>} 时 L3 就会缺席）。
     * 三个 accessor（{@code result / ingredient / fluidIngredient}）不在接口上，
     * 由 {@code HandInfusionGunCategory} 按具体 record 分派。UID 未改。</p>
     */
    public static final IRecipeType<RecipeHolder<CreateRecipe<FillingInput>>> HAND_INFUSION_GUN =
            holderType("hand_infusion_gun");

    // ==================== 研钵 / 碾钵 ====================

    /** 研钵 · 研磨（MILLING）+ 本模组 L3 {@code HandMadeCrushingRecipe}。 */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> MORTAR_MILLING =
            holderType("mortar_milling");

    /**
     * 碾钵 · 粉碎 + 研磨（CRUSHING 在前、MILLING 在后）。
     *
     * <p>上游用的是 {@code AbstractCrushingRecipe}（CRUSHING 与 MILLING 的共同父类），
     * 26.1 的 Create Fly 没有这个类，共同父类换成
     * {@link CreateSingleStackRollableRecipe} —— 与游戏内碾钵「先粉碎、后研磨」的能力一致。
     * UID 保持不变。</p>
     */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> CRUSHER_MORTAR_CRUSHING =
            holderType("crusher_mortar_crushing");

    // ==================== 搅拌杖 ====================

    /** 搅拌杖 · 混合（MIXING）+ 本模组 L3 {@code HandMadeToolRecipe}。 */
    public static final IRecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_MIXING =
            holderType("stirring_staff_mixing");

    /** 搅拌杖 · 自动无序合成 ★ 新增（上游绑 BasinRecipe：工作台配方要在工作盆布局里显示）。 */
    public static final IRecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_AUTO_SHAPELESS =
            holderType("stirring_staff_auto_shapeless");

    /** 搅拌杖 · 自动酿造 ★ 新增（POTION）。 */
    public static final IRecipeType<RecipeHolder<BasinRecipe>> STIRRING_STAFF_AUTO_BREWING =
            holderType("stirring_staff_auto_brewing");

    // ==================== 指杆 ====================

    /** 指杆 · 物品应用（DEPLOYING + ITEM_APPLICATION + 本模组 L3）。 */
    public static final IRecipeType<RecipeHolder<ItemApplicationRecipe>> POINTER_APPLICATION =
            holderType("pointer_application");

    // ==================== 风箱 ====================

    /**
     * 风箱 · 熔炼（#13）：原版 {@code SMELTING} / {@code BLASTING} 去重后的条目
     * + 本模组 L3 {@code bellows_recipe}（{@code fan_type = blasting}），统一归一成
     * {@link BellowsCookingDisplay}。
     *
     * <p><b>为什么换显示类型（UID 未动）：</b>切片 1 把这两个常量绑成了
     * {@code RecipeHolder<AbstractCookingRecipe>}，但 L3 的 {@code HandMadeBellowsRecipe}
     * <b>不是</b>烹饪配方（它 {@code implements CreateSingleStackRollableRecipe}），
     * 装不进那个类型。两侧唯一共有的语义是「单输入 + 单产物」，所以仿照 Create 自己的
     * {@code BlockCuttingDisplay} 形态新建纯显示 record {@link BellowsCookingDisplay}，
     * 类型直接绑它 —— <b>没有 {@code RecipeHolder} 包装</b>：
     * {@code RecipeHolder<T>} 的 {@code T} 有 {@code extends Recipe<?>} 上界，纯显示 record 装不进 holder
     * （与 {@code HAND_SAW_STONECUTTING} 同一个理由）。</p>
     */
    public static final IRecipeType<BellowsCookingDisplay> BELLOWS_BLASTING =
            displayType("bellows_blasting", BellowsCookingDisplay.class);

    /** 风箱 · 烟熏（#14）：原版 {@code SMOKING} + L3（{@code fan_type = smoking}），同 {@link #BELLOWS_BLASTING}。 */
    public static final IRecipeType<BellowsCookingDisplay> BELLOWS_SMOKING =
            displayType("bellows_smoking", BellowsCookingDisplay.class);

    /** 风箱 · 缠魂（HAUNTING）+ 本模组 L3 {@code HandMadeBellowsRecipe}（{@code fan_type=haunting}）。 */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> BELLOWS_HAUNTING =
            holderType("bellows_haunting");

    /** 风箱 · 洗涤（SPLASHING）+ 本模组 L3 {@code HandMadeBellowsRecipe}（{@code fan_type=splashing}）。 */
    public static final IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> BELLOWS_SPLASHING =
            holderType("bellows_splashing");

    /**
     * 造一个「纯显示 record」类型的 {@code IRecipeType}（<b>不</b>包 {@code RecipeHolder}）。
     *
     * <p>只用在显示类型不是配方的两个常量上：{@code HAND_SAW_STONECUTTING}
     * （绑 Create 的 {@code BlockCuttingDisplay}）与 {@link #BELLOWS_BLASTING} /
     * {@link #BELLOWS_SMOKING}（绑本模组自建的 {@link BellowsCookingDisplay}）。
     * {@code RecipeHolder<T>} 的 {@code T} 有 {@code extends Recipe<?>} 上界，纯显示 record
     * 装不进 holder，所以这两个家族直接绑 record 本身（与 Create 自己的
     * {@code BLOCK_CUTTING} 同形态）。{@code IRecipeType.create(Identifier, Class)} 的重载
     * 与 {@code holderType} 用的是同一个，只是不传 {@code RecipeHolder.class}。</p>
     */
    private static <T> IRecipeType<T> displayType(String path, Class<? extends T> recipeClass) {
        return IRecipeType.create(Identifier.fromNamespaceAndPath(MODID, path), recipeClass);
    }

    /**
     * 造一个「配方持有者」类型的 {@code IRecipeType}。
     *
     * <p>上游的 {@code RecipeType.createRecipeHolderType(ResourceLocation)} 在 JEI 29 里
     * 已不存在；Create 自己的等价写法是
     * {@code IRecipeType.create(uid, RecipeHolder.class)}。但它只返回
     * {@code IRecipeType<RecipeHolder>}（recipeClass 形参用的是 {@code Class<? extends T>}，
     * 传 {@code RecipeHolder.class} 时 T 只能推成裸 {@code RecipeHolder}），拿不到
     * {@code RecipeHolder<X>} 的泛型参数，所以这里必须 raw cast 一次。</p>
     *
     * <p>Erasure 之后所有 {@code IRecipeType<RecipeHolder<X>>} 在字节码里没有区别，
     * 这个 cast 是安全的；真正保证类型正确的是各分类自己的 {@code getRecipeType()} 声明。</p>
     */
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> IRecipeType<RecipeHolder<T>> holderType(String path) {
        Object type = IRecipeType.create(
                Identifier.fromNamespaceAndPath(MODID, path), RecipeHolder.class);
        return (IRecipeType<RecipeHolder<T>>) type;
    }
}
