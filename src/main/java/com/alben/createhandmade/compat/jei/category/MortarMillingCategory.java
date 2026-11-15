package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
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
 * 研钵 · 研磨（#7）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code MillingCategory}</b>（{@code javap -p -c} 核对）：
 * 输入槽 (15,9)、单条产物 (139,27)、多条时 2 列 (133 / 152) 向上每行 -19、
 * {@code JEI_ARROW}(85,32)、{@code JEI_DOWN_ARROW}(43,4)、{@code JEI_SHADOW}(32,40)、
 * 装饰模型原点 (42,19)、高度 53。</p>
 *
 * <p><b>为什么这样归一化：</b>研钵吃的是 {@code AllRecipeTypes.MILLING}（磨盘的 L1）+
 * 本模组 L3 {@code HandMadeCrushingRecipe}（研钵/碾钵共用那个 record，靠 {@code tool} 字段
 * 区分家族）。两者都 implements {@link CreateSingleStackRollableRecipe}，所以直接并进一个列表，
 * L3 用 {@code tool == HandMadeTool.MORTAR} 分流。</p>
 *
 * <p>装饰：Create 在 (42,19) 画磨盘 3D 模型（{@code MillstoneRenderState}），这里换成研钵图标，
 * 坐标沿用模型原点。</p>
 */
public class MortarMillingCategory extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.mortar_milling";

    private static final int INPUT_X = 15;
    private static final int INPUT_Y = 9;
    private static final int SINGLE_OUTPUT_X = 139;
    private static final int SINGLE_OUTPUT_Y = 27;
    private static final int OUTPUT_LEFT_X = 133;
    private static final int OUTPUT_RIGHT_X = 152;
    private static final int OUTPUT_ROW_STEP = 19;
    private static final int DECOR_X = 42;
    private static final int DECOR_Y = 19;

    /** 源：{@code AllRecipeTypes.MILLING} + L3 {@code HandMadeCrushingRecipe}（研钵那一支）。 */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.MILLING, HandMadeTool.MORTAR, null);
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Tool(), HandMadeTool.MORTAR,
                recipe -> recipe instanceof HandMadeCrushingRecipe);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.MORTAR_MILLING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (磨石, 面粉)；这里换成 (研钵, 糖) —— 用原版物品，不额外依赖 Create 的物品常量。
        return new TwoIconRenderer(ModItems.MORTAR, Items.SUGAR);
    }

    @Override
    public int getHeight() {
        return 53;
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
        AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);
        AllGuiTextures.JEI_SHADOW.render(graphics, 32, 40);
        // Create 在这里画磨盘 3D 模型（MillstoneRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.MORTAR), DECOR_X, DECOR_Y);
    }
}
