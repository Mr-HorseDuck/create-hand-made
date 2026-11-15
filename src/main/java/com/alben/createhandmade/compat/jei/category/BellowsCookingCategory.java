package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.compat.jei.display.BellowsCookingDisplay;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.FanType;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 风箱 · 熔炼（#13）与风箱 · 烟熏（#14）两个类别。
 *
 * <p><b>一个类、两个实例</b>：26.1 的 {@code FanBlastingCategory} 与 {@code FanSmokingCategory}
 * 布局<b>逐字节相同</b>（{@code javap -p -c} 对照：两者都是输入槽 {@code (21,48)}、
 * 产物槽 {@code (141,48)}、{@code JEI_SHADOW}(46,27)、{@code JEI_LIGHT}(65,39)、
 * {@code JEI_LONG_ARROW}(54,51)、装饰模型原点 {@code (56,4)}、高度 72），差别只有
 * 「数据源 / 标题 / 图标」。所以这里用一个类 + 构造参数 {@link FanType} 区分，
 * 注册时仍是<b>两个独立的 {@code IRecipeType}</b>（{@link ModJeiTypes#BELLOWS_BLASTING} /
 * {@link ModJeiTypes#BELLOWS_SMOKING}，UID 与上游一致）。</p>
 *
 * <p><b>为什么需要归一化显示类型（为什么不直接显示原版烹饪配方）：</b>
 * 这两个类别的数据源两侧 —— 原版 {@code SMELTING/BLASTING/SMOKING}
 * 与本模组 L3 {@code HandMadeBellowsRecipe} —— 没有共同的配方类型：
 * 前者是 {@code AbstractCookingRecipe}，后者 implements {@code CreateSingleStackRollableRecipe}。
 * JEI 的 {@code IRecipeType<T>} 不变型，一个类别只能有一个 T，所以两侧统一压成
 * 「单输入 + 单产物」的纯显示 record {@link BellowsCookingDisplay}
 * （形态照抄 Create 的 {@code BlockCuttingDisplay}）。这正是 PLAN §2 F3 里
 * 「保留显示代理的只有风箱熔炼 / 烟熏这一处」那句话的落地方式 ——
 * 只是 26.1 不需要上游那种 {@code asBellowsCookingProxy} 包装类，一个 record 就够。</p>
 *
 * <p><b>去重规则</b>（两个类别共用，写在 {@link JeiRecipeSource#bellowsCooking(FanType)} 里）：
 * {@code BLASTING} 的条目排除「有同输入 {@code SMOKING}」的、{@code SMELTING} 的条目排除
 * 「有同输入 {@code BLASTING} 或 {@code SMOKING}」的，判定用
 * {@code SingleRecipeInput} + {@code matches(input, level)}，且自身与被找到的那条都要过
 * {@code AllRecipeTypes.CAN_BE_AUTOMATED}；烟熏类别只扫 {@code SMOKING}、不去重。
 * L3 配方按 {@code fan_type} 分流，多产物只取第一个（烹饪语义单产物）。</p>
 *
 * <p><b>装饰的中介物（lava / fire）画在哪里：</b>Create 是用
 * {@code FanRenderState(Matrix3x2f, 56, 4, BlockState)} 一次性画「鼓风机 + 熔岩/火」的 3D 模型，
 * 本模组没有那套模型，所以按前几个切片的做法把模型原点换成风箱物品图标
 * （{@code graphics.item(...)}，坐标仍是 56,4）。介质（熔岩桶 / 营火）只体现在<b>类别图标</b>
 * （{@link TwoIconRenderer} 的第二个图标）上 —— 不再单独在面板里画一个小图标，
 * 免得和 {@code JEI_LONG_ARROW}(54,51) 与 {@code JEI_LIGHT}(65,39) 重叠。</p>
 */
public class BellowsCookingCategory extends CreateCategory<BellowsCookingDisplay> {

    private static final String TITLE_KEY_BLASTING = "jei.create_hand_made.bellows_blasting";
    private static final String TITLE_KEY_SMOKING = "jei.create_hand_made.bellows_smoking";

    // 三处坐标与 FanBlastingCategory / FanSmokingCategory 完全一致，不要改。
    private static final int INPUT_X = 21;
    private static final int INPUT_Y = 48;
    private static final int OUTPUT_X = 141;
    private static final int OUTPUT_Y = 48;
    private static final int SHADOW_X = 46;
    private static final int SHADOW_Y = 27;
    private static final int LIGHT_X = 65;
    private static final int LIGHT_Y = 39;
    private static final int ARROW_X = 54;
    private static final int ARROW_Y = 51;
    /** 装饰：Create 的 {@code FanRenderState} 原点 (56,4)，这里放风箱图标。 */
    private static final int DECOR_X = 56;
    private static final int DECOR_Y = 4;
    /** 与两个 Create 类别一致（都是 72）。 */
    private static final int HEIGHT = 72;

    private final FanType fanType;
    /** 介质物品：熔炼 = 熔岩桶，烟熏 = 营火（对应 Create 图标里的第二个图标）。 */
    private final Item medium;

    /**
     * @param fanType 这个实例服务哪一种鼓风（决定类型 / 标题 / 数据源）
     * @param medium  图标里的介质物品（不进槽位，只做类别图标）
     */
    public BellowsCookingCategory(FanType fanType, Item medium) {
        this.fanType = fanType;
        this.medium = medium;
    }

    /**
     * 源：{@code JeiRecipeSource} 是唯一配方来源。
     *
     * <p>静态方法而不是实例方法：注册期在 {@code registerRecipes} 里按类型各取一次，
     * 与其它类别的 {@code getRecipes()} 形态保持一致。</p>
     */
    public static List<BellowsCookingDisplay> getRecipes(FanType fanType) {
        return JeiRecipeSource.bellowsCooking(fanType);
    }

    @Override
    public IRecipeType<BellowsCookingDisplay> getRecipeType() {
        return fanType == FanType.BLASTING ? ModJeiTypes.BELLOWS_BLASTING : ModJeiTypes.BELLOWS_SMOKING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(
                fanType == FanType.BLASTING ? TITLE_KEY_BLASTING : TITLE_KEY_SMOKING);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (螺旋桨, 熔岩桶) / (螺旋桨, 营火)；这里换成 (风箱, 同一介质)。
        return new TwoIconRenderer(ModItems.BELLOWS, medium);
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    /**
     * JEI 的配方唯一标识：直接用原生配方 id（显示 record 里带着它）。
     *
     * <p>纯显示类型没有 {@code RecipeHolder} 兜底，不覆写就拿不到稳定 uid，
     * 收藏 / 书签 / 历史在重载后会漂移 —— Create 的 {@code BlockCuttingCategory}
     * 也是这么做的。</p>
     */
    @Override
    public Identifier getIdentifier(BellowsCookingDisplay recipe) {
        return recipe.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BellowsCookingDisplay recipe, IFocusGroup focuses) {
        // 逐句照抄 26.1 FanBlastingCategory / FanSmokingCategory 的 setRecipe：
        // 输入槽 (21,48) 用输入原料；产物槽 (141,48) 直接 add(ItemStackTemplate)。
        builder.addInputSlot(INPUT_X, INPUT_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.input());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.result());
    }

    @Override
    public void draw(BellowsCookingDisplay recipe, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, SHADOW_X, SHADOW_Y);
        AllGuiTextures.JEI_LIGHT.render(graphics, LIGHT_X, LIGHT_Y);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, ARROW_X, ARROW_Y);
        // Create 在这里画「鼓风机 + 熔岩/火」的 3D 模型（FanRenderState，原点 (56,4)）；
        // 手工风箱画自己的图标，介质见类注释（只出现在类别图标里）。
        graphics.item(new ItemStack(ModItems.BELLOWS), DECOR_X, DECOR_Y);
    }
}
