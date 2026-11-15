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
 * 碾钵 · 粉碎 + 研磨（#8）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code CrushingCategory}</b>（{@code javap -p -c} 核对）：
 * 输入槽 (51,3)、产物从 {@code 3 + (179 - 19 * n) / 2} 起、同一行 y=83、每个 +19、
 * {@code JEI_DOWN_ARROW}(72,7)、装饰模型原点 (42,24)、高度 100。</p>
 *
 * <p><b>为什么这样归一化：</b>游戏内 {@code CrusherMortarItem.findRecipe} 是「先 CRUSHING、
 * 匹配不到再 MILLING」，所以这里的数据源顺序也是
 * {@code AllRecipeTypes.CRUSHING} → {@code AllRecipeTypes.MILLING} → L3
 * {@code HandMadeCrushingRecipe}，并且照抄 {@code CrushingCategory.getRecipes} 的去重规则：
 * 如果一个研磨配方的输入已经有粉碎配方，<b>不再</b>在 JEI 里重复显示（同一物品两行没意义）。</p>
 *
 * <p><b>为什么能绑共同父接口：</b>上游绑的是 {@code AbstractCrushingRecipe}（CRUSHING 与
 * MILLING 的共同父类），26.1 的 Create Fly 没有这个类，共同父类换成
 * {@link CreateSingleStackRollableRecipe}（B 阶段已核实）。</p>
 *
 * <p>装饰：Create 在 (42,24) 画粉碎轮 3D 模型（{@code CrushWheelRenderState}），这里换成碾钵图标。</p>
 */
public class CrusherMortarCrushingCategory
        extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.crusher_mortar_crushing";

    /** 26.1 {@code CrushingCategory.setRecipe} 的输入槽坐标。 */
    private static final int INPUT_X = 51;
    private static final int INPUT_Y = 3;
    /** 产物行的基准 y 与「一行总宽 / 单槽步长」，照抄 {@code 179 - 19 * n} / 19。 */
    private static final int OUTPUT_ROW_Y = 83;
    private static final int OUTPUT_SPACING = 19;
    private static final int OUTPUT_TOTAL_WIDTH = 179;
    private static final int OUTPUT_START_OFFSET = 3;
    private static final int DECOR_X = 42;
    private static final int DECOR_Y = 24;

    /**
     * 源：CRUSHING 在前、去重后的 MILLING 在后、最后并入手工 L3 粉碎配方。
     *
     * <p>L3 的分流条件与 {@code HandMadeRecipePool.acceptsExclusiveRecipe} 一致：碾钵既认
     * {@code tool=CRUSHER_MORTAR} 也认 {@code tool=MORTAR} 的独占配方（研钵能磨的，碾钵自然也能）。</p>
     */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> crushing =
                JeiRecipeSource.rollable(AllRecipeTypes.CRUSHING, HandMadeTool.CRUSHER_MORTAR);

        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>(crushing);
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.MILLING, HandMadeTool.CRUSHER_MORTAR,
                recipe -> !isCoveredByCrushing(crushing, recipe));
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Tool(), HandMadeTool.CRUSHER_MORTAR,
                recipe -> recipe instanceof HandMadeCrushingRecipe crushingRecipe
                        && (crushingRecipe.tool() == HandMadeTool.CRUSHER_MORTAR
                        || crushingRecipe.tool() == HandMadeTool.MORTAR));
        return list;
    }

    /**
     * 该研磨配方的输入端是否已经被某条粉碎配方覆盖。
     *
     * <p>取「输入的第一个候选物品」，再用它去试每条粉碎配方的 {@code ingredient()}
     * —— 与 26.1 {@code CrushingCategory.getRecipes} 的判定等价（它取的是
     * {@code CustomIngredient#display} 的首个解析结果，这里用 Create 官方的
     * {@link CreateCategory#getStacks} 取，效果相同且不用碰 Fabric 的 ingredient API）。</p>
     */
    private static boolean isCoveredByCrushing(
            List<RecipeHolder<CreateSingleStackRollableRecipe>> crushing,
            CreateSingleStackRollableRecipe milling) {
        List<ItemStack> stacks = getStacks(milling.ingredient(),
                CreateCategory::createIngredientContext);
        if (stacks.isEmpty()) {
            return false;
        }
        ItemStack first = stacks.getFirst();
        if (first.isEmpty()) {
            return false;
        }
        for (RecipeHolder<CreateSingleStackRollableRecipe> holder : crushing) {
            if (holder.value().ingredient().test(first)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.CRUSHER_MORTAR_CRUSHING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (粉碎轮, 粉碎金矿)；这里换成 (碾钵, 沙砾)。
        return new TwoIconRenderer(ModItems.CRUSHER_MORTAR, Items.GRAVEL);
    }

    @Override
    public int getHeight() {
        return 100;
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
        // 一行居中：Create 的算法是 x = 3 + (179 - 19 * n) / 2，第 i 个再 +19 * i。
        int startX = OUTPUT_START_OFFSET + (OUTPUT_TOTAL_WIDTH - OUTPUT_SPACING * size) / 2;
        for (int i = 0; i < size; i++) {
            addChanceSlot(builder, startX + i * OUTPUT_SPACING, OUTPUT_ROW_Y, results.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<CreateSingleStackRollableRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 72, 7);
        // Create 在这里画粉碎轮 3D 模型（CrushWheelRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.CRUSHER_MORTAR), DECOR_X, DECOR_Y);
    }
}
