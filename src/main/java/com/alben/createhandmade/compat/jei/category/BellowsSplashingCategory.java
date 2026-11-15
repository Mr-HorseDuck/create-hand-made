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
 * 风箱 · 洗涤（#16）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code FanWashingCategory}</b>（{@code javap -p -c} 核对）：
 * 与缠魂类别<b>完全同一套</b>槽位算法（单条输入 (21,48) / 产物 (141,48)；多条按
 * {@code shift = 1 - min(3, n)} 平移，3 列 +19、向上每行 -19），差异只在
 * 装饰与第二块底纹：Create 在 (46,27) 与 (65,39) 各画一个 {@code JEI_SHADOW}
 * （水那侧没有 {@code JEI_LIGHT}）—— 这是它源码里的原样，照抄不「修正」。
 * 高度 72。</p>
 *
 * <p><b>为什么这样归一化：</b>{@code AllRecipeTypes.SPLASHING} + 本模组 L3
 * {@code HandMadeBellowsRecipe}（{@code fan_type = splashing}），共同父接口是
 * {@link CreateSingleStackRollableRecipe}，多产物与概率直接渲染，无需显示代理。</p>
 */
public class BellowsSplashingCategory
        extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.bellows_splashing";

    private static final int INPUT_X = 21;
    private static final int INPUT_Y = 48;
    private static final int SINGLE_OUTPUT_X = 141;
    private static final int SINGLE_OUTPUT_Y = 48;
    private static final int OUTPUT_STEP = 19;
    private static final int OUTPUT_WRAP_LIMIT = 9;
    private static final int OUTPUT_Y_TALL = 57;
    /** 装饰：Create 画的是鼓风机 + 水（FanRenderState 原点 (56,4)）。 */
    private static final int DECOR_X = 56;
    private static final int DECOR_Y = 4;

    /** 源：{@code AllRecipeTypes.SPLASHING} + L3 风箱配方里 {@code fan_type = splashing} 的那部分。 */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.SPLASHING, null, null);
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Bellows(), null,
                recipe -> recipe instanceof HandMadeBellowsRecipe bellows
                        && bellows.fanType() == FanType.SPLASHING);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.BELLOWS_SPLASHING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (螺旋桨, 水桶)；这里换成 (风箱, 水桶)。
        return new TwoIconRenderer(ModItems.BELLOWS, Items.WATER_BUCKET);
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
        // Create 的 FanWashingCategory 在这里画两块 JEI_SHADOW（(46,27) 与 (65,39)），照抄。
        AllGuiTextures.JEI_SHADOW.render(graphics, 46, 27);
        AllGuiTextures.JEI_SHADOW.render(graphics, 65, 39);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 54 + 7 * shift, 51);
        // Create 在这里画鼓风机 3D 模型（FanRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.BELLOWS), DECOR_X, DECOR_Y);
    }
}
