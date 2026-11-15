package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeCuttingRecipe;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
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
 * 手锯 · 切削（16 个 JEI 类别里的 #1）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code SawingCategory}</b>（用 {@code javap -p -c}
 * 反编译核对的常量）：输入槽 (44,5)、产物单条时 (118,48)、多条时按 2 列 (118 / 137) 向上
 * 每行 -19、{@code JEI_DOWN_ARROW}(70,6)、{@code JEI_SHADOW}(55,55)、装饰模型原点 (64,31)、
 * 高度 70。</p>
 *
 * <p><b>为什么这样归一化：</b>手锯能切削的两类配方是「Create 的 L1 {@code CuttingRecipe}」与
 * 「本模组的 L3 {@code HandMadeCuttingRecipe}」，两者都 implements
 * {@link CreateSingleStackRollableRecipe}（{@code ingredient()} + {@code results()}），
 * 所以不需要上游那套显示代理 —— 一个类别、一种显示类型（{@link RecipeHolder} 包接口）、
 * 两段数据源直接并进同一个列表。</p>
 *
 * <p><b>与 Create 的唯一差别：</b>26.1 的 {@code SawingCategory} 在 (64,31) 画的是机械锯的 3D
 * 模型（{@code SawRenderState}）；这里换成手锯物品图标 —— 本类是「手工」工具，画一台机械锯会
 * 误导玩家。<b>图标坐标沿用装饰模型原点</b>，没有另行发挥。</p>
 */
public class HandSawCategory extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    /** 标题键：与 {@code zh_cn.json} / {@code en_us.json} 里已有的键一致（上游同款）。 */
    private static final String TITLE_KEY = "jei.create_hand_made.hand_saw";

    /** 26.1 {@code SawingCategory.setRecipe} 的输入槽坐标。 */
    private static final int INPUT_X = 44;
    private static final int INPUT_Y = 5;
    /** 单条产物时的槽位坐标。 */
    private static final int SINGLE_OUTPUT_X = 118;
    private static final int SINGLE_OUTPUT_Y = 48;
    /** 多条产物：左列 / 右列，行间距 -19。 */
    private static final int OUTPUT_LEFT_X = 118;
    private static final int OUTPUT_RIGHT_X = 137;
    private static final int OUTPUT_ROW_STEP = 19;
    /** 装饰模型原点（机械锯 → 手锯图标）。 */
    private static final int DECOR_X = 64;
    private static final int DECOR_Y = 31;

    /**
     * 本类别要显示的全部配方：{@code AllRecipeTypes.CUTTING} 在前、本模组 L3 并入其后。
     *
     * <p>取数一律走 {@link JeiRecipeSource}（唯一配方来源），这里只声明「哪些归手锯」：
     * L3 的 {@code tool_recipe} 类型下混着 6 个家族，用手锯的那部分靠
     * {@code instanceof HandMadeCuttingRecipe} 分流。</p>
     */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.CUTTING, HandMadeTool.HAND_SAW, null);
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Tool(), HandMadeTool.HAND_SAW,
                recipe -> recipe instanceof HandMadeCuttingRecipe);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.HAND_SAW_CUTTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // 与 Create 的 (机械锯, 原木) 一一对应：本模组的手锯 + 同一份示例原料。
        return new TwoIconRenderer(ModItems.HAND_SAW, Items.OAK_LOG);
    }

    @Override
    public int getHeight() {
        return 70;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          RecipeHolder<CreateSingleStackRollableRecipe> holder, IFocusGroup focuses) {
        CreateSingleStackRollableRecipe recipe = holder.value();

        builder.addInputSlot(INPUT_X, INPUT_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.ingredient());

        List<ProcessingOutput> results = recipe.results();
        int size = results.size();
        if (size == 1) {
            addChanceSlot(builder, SINGLE_OUTPUT_X, SINGLE_OUTPUT_Y, results.getFirst());
            return;
        }
        for (int i = 0; i < size; i++) {
            int x = (i % 2 == 0) ? OUTPUT_LEFT_X : OUTPUT_RIGHT_X;
            int y = SINGLE_OUTPUT_Y - (i / 2) * OUTPUT_ROW_STEP;
            addChanceSlot(builder, x, y, results.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<CreateSingleStackRollableRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 70, 6);
        AllGuiTextures.JEI_SHADOW.render(graphics, 55, 55);
        // Create 在这里画机械锯 3D 模型（SawRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.HAND_SAW), DECOR_X, DECOR_Y);
    }
}
