package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.FanType;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
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
 * 风箱 · 缠魂（#15）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code FanHauntingCategory}</b>（{@code javap -p -c} 核对）：
 * 单条产物时输入槽 (21,48)、产物 (141,48)；多条时先算
 * {@code shift = 1 - min(3, n)}，输入槽 {@code (21 + shift * 5, 48)}、产物起点
 * {@code (141 + shift * 9, n <= 9 ? 48 : 57)}、按 3 列 (每列 +19) 向上每行 -19；
 * {@code JEI_SHADOW}(46,27)、{@code JEI_LIGHT}(65,39)、{@code JEI_LONG_ARROW}(54 + 7 * shift, 51)、
 * 装饰模型原点 (56,4)、高度 72。</p>
 *
 * <p><b>为什么这样归一化：</b>风箱缠魂在游戏内吃 {@code AllRecipeTypes.HAUNTING}（Create 的
 * 鼓风缠魂）+ 本模组 L3 {@code HandMadeBellowsRecipe}，后者用 {@code fan_type} 字段区分四种鼓风。
 * 两个 record 都 implements {@link CreateSingleStackRollableRecipe}，所以
 * <b>不需要</b>上游的 {@code asBellowsProcessingProxy} 那套显示代理 —— L3 靠
 * {@code fanType() == FanType.HAUNTING} 直接筛进来即可，多产物与概率照常渲染。</p>
 *
 * <p><b>L2 过滤为什么是 null：</b>风箱不是 {@code HandMadeTool} 的枚举常量（见
 * {@code HandMadeTool} 的类注释：风箱走 Create 的 {@code FanProcessingType}，不进 Pool），
 * 所以它没有 {@code HandMadeRecipeFilters} 过滤表，这里不传工具 ——
 * 与服务端 {@code HandMadeRecipePool} 的行为一致（风箱没有 L2 入口）。</p>
 */
public class BellowsHauntingCategory
        extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.bellows_haunting";

    private static final int INPUT_X = 21;
    private static final int INPUT_Y = 48;
    private static final int SINGLE_OUTPUT_X = 141;
    private static final int SINGLE_OUTPUT_Y = 48;
    private static final int OUTPUT_STEP = 19;
    /** 产物多于 9 条时整体下移一行，给上方三行腾位置（Create 的原算法）。 */
    private static final int OUTPUT_WRAP_LIMIT = 9;
    private static final int OUTPUT_Y_TALL = 57;
    /** 装饰：Create 画的是鼓风机 + 灵魂火（FanRenderState 原点 (56,4)）。 */
    private static final int DECOR_X = 56;
    private static final int DECOR_Y = 4;

    /** 源：{@code AllRecipeTypes.HAUNTING} + L3 风箱配方里 {@code fan_type = haunting} 的那部分。 */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.HAUNTING, null, null);
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Bellows(), null,
                recipe -> recipe instanceof HandMadeBellowsRecipe bellows
                        && bellows.fanType() == FanType.HAUNTING);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.BELLOWS_HAUNTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (螺旋桨, 灵魂营火)；这里换成 (风箱, 灵魂火把)。
        return new TwoIconRenderer(ModItems.BELLOWS, Items.SOUL_TORCH);
    }

    @Override
    public int getHeight() {
        return 72;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          RecipeHolder<CreateSingleStackRollableRecipe> holder, IFocusGroup focuses) {
        CreateSingleStackRollableRecipe recipe = holder.value();

        List<ProcessingOutput> results = recipe.results();
        int size = results.size();
        if (size == 1) {
            builder.addInputSlot(INPUT_X, INPUT_Y)
                    .setBackground(SLOT, -1, -1)
                    .add(recipe.ingredient());
            addChanceSlot(builder, SINGLE_OUTPUT_X, SINGLE_OUTPUT_Y, results.getFirst());
            return;
        }

        int shift = 1 - Math.min(3, size);
        builder.addInputSlot(INPUT_X + shift * 5, INPUT_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.ingredient());

        int startX = SINGLE_OUTPUT_X + shift * 9;
        int startY = size <= OUTPUT_WRAP_LIMIT ? SINGLE_OUTPUT_Y : OUTPUT_Y_TALL;
        for (int i = 0; i < size; i++) {
            addChanceSlot(builder, startX + (i % 3) * OUTPUT_STEP,
                    startY - (i / 3) * OUTPUT_STEP, results.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<CreateSingleStackRollableRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        int shift = 1 - Math.min(3, holder.value().results().size());
        AllGuiTextures.JEI_SHADOW.render(graphics, 46, 27);
        AllGuiTextures.JEI_LIGHT.render(graphics, 65, 39);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 54 + 7 * shift, 51);
        // Create 在这里画鼓风机 3D 模型（FanRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.BELLOWS), DECOR_X, DECOR_Y);
    }
}
