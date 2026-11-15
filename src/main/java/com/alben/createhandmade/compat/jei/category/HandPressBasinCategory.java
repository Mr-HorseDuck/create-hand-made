package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.recipe.HandMadeToolRecipe;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.client.compat.jei.category.BasinCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 冲压锤 · 工作盆 · 成型 / 打包（#4）。
 *
 * <p><b>基类</b>：{@code BasinCategory<BasinRecipe>}（26.1 Create Fly 的
 * {@code com.zurrtum.create.client.compat.jei.category.BasinCategory}）。
 * 它已经实现了 {@code getHeight() = 103}，并提供五个静态 helper：
 * {@code addIngredientSlots / addResultSlots / addFluidResultSlots / addHeatSlots /
 * drawBackground} —— 全部收 {@link BasinRecipe}，所以<b>零代理</b>。</p>
 *
 * <p><b>布局逐句照抄 26.1 的 {@code CompactingCategory} 的 {@code setRecipe}/{@code draw}</b>
 * （{@code javap -p -c} 核对）：</p>
 * <ul>
 *   <li>{@code addIngredientSlots}：输入从 {@code (12, 51)} 起，按 3 列（每列 +19）向上每行 −19；
 *       物品与流体输入混排，合计 ≤ 3 个时水平居中（{@code 12 + (3 - n) * 19 / 2}），
 *       {@code n > 9} 时整体上移 9（{@code 51 → 60}）；</li>
 *   <li>产物：{@code addResultSlots(builder, results, 0, size - 1, 51, size % 2 != 0)}
 *       —— 两列 {@code 132 / 151}，每行 −19，奇数个时第一条落在 {@code 142}（居中）；</li>
 *   <li>热量：{@code addHeatSlots}（烈焰燃烧器槽 {@code (134, 81)}，需要超级加热时再加
 *       烈焰蛋糕槽 {@code (153, 81)}）；</li>
 *   <li>{@code drawBackground(recipe, graphics, results.size())}：向下的箭头 + 加热条 +
 *       加热等级文字（{@code HEAT_BAR}(4,80) / {@code LIGHT}(81,88) / 文字 (9,86)）。</li>
 * </ul>
 *
 * <p><b>为什么这样归一化：</b>冲压锤压工作盆在游戏内走两条路
 * （{@code PressHammerItem.tryPressBasin}）：{@code AllRecipeTypes.COMPACTING}（L1）与
 * 本模组 L3 {@code HandMadeToolRecipe}（{@code tool = press_hammer_basin}），
 * {@code mergeInGlobalOrder} 之后按全序逐条 {@code matches}。两者都 implements
 * {@link BasinRecipe}，所以 JEI 这边直接并进一个列表、一个显示类型即可。</p>
 *
 * <p><b>装饰</b>：Create 在 {@code (91, -5)} 画机械压床 + 工作盆 3D 模型
 * （{@code PressBasinRenderState}）。这里换成冲压锤物品图标，x 沿用 91；
 * y 由 −5 改为 8 —— 16×16 图标放在 −5 会有 5 px 被面板上沿裁掉（与
 * {@code HandPressDepotCategory} 里 −16 → 8 的处理同理）。</p>
 *
 * <p><b>{@code HAND_PRESS_BASIN_AUTO_SQUARE}（#5）为什么不在本类里：</b>见
 * {@code CreateHandMadeJEI} 里 R5 那段注释。</p>
 */
public class HandPressBasinCategory extends BasinCategory<BasinRecipe> {

    private static final String TITLE_KEY = "jei.create_hand_made.hand_press_basin";

    /** 产物槽的基准 y（与挤压类一致：与输入网格同高）。 */
    private static final int RESULT_Y = 51;
    /** 装饰：x 抄 Create 的模型原点 91；y 由 −5 改为 8（原因见类注释）。 */
    private static final int DECOR_X = 91;
    private static final int DECOR_Y = 8;

    /**
     * 源：{@code AllRecipeTypes.COMPACTING} + L3 {@code HandMadeToolRecipe}
     * （{@code tool = press_hammer_basin} 那一支）。
     */
    public static List<RecipeHolder<BasinRecipe>> getRecipes() {
        List<RecipeHolder<BasinRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendBasin(list, AllRecipeTypes.COMPACTING, HandMadeTool.PRESS_HAMMER_BASIN,
                null);
        JeiRecipeSource.appendBasin(list, JeiRecipeSource.l3Tool(), HandMadeTool.PRESS_HAMMER_BASIN,
                recipe -> recipe instanceof HandMadeToolRecipe handMade
                        && handMade.tool() == HandMadeTool.PRESS_HAMMER_BASIN);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<BasinRecipe>> getRecipeType() {
        return ModJeiTypes.HAND_PRESS_BASIN;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (机械压床, 工作盆)；这里换成 (冲压锤, 炼药锅) —— 炼药锅是原版物品，
        // 语义上就是「盆」，不用为此依赖 Create 的 AllItems.BASIN。
        return new TwoIconRenderer(ModItems.PRESS_HAMMER, Items.CAULDRON);
    }

    // getHeight() 由 BasinCategory 提供（103），不覆写。

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BasinRecipe> holder,
                          IFocusGroup focuses) {
        BasinRecipe recipe = holder.value();

        // 输入（物品 + 流体混排，Create 的居中算法）
        addIngredientSlots(builder, recipe);

        // 产物：与 CompactingCategory 完全同一调用
        List<ProcessingOutput> results = BasinSlots.results(recipe);
        int size = results.size();
        addResultSlots(builder, results, 0, size - 1, RESULT_Y, size % 2 != 0);

        // 热量槽（NONE 时不显示燃烧器；需要超级加热时多一个烈焰蛋糕槽）
        addHeatSlots(builder, recipe);
    }

    @Override
    public void draw(RecipeHolder<BasinRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        BasinRecipe recipe = holder.value();
        int resultCount = BasinSlots.results(recipe).size();
        // 箭头 / 加热条 / 加热等级文字全在 BasinCategory.drawBackground 里。
        drawBackground(recipe, graphics, resultCount);
        // Create 在这里画机械压床 + 工作盆 3D 模型（PressBasinRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.PRESS_HAMMER), DECOR_X, DECOR_Y);
    }
}
