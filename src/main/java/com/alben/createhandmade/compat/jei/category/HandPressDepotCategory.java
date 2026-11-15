package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadePressingRecipe;
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
 * 冲压锤 · 置物台 / 传送带（#3）。
 *
 * <p><b>布局坐标照抄 26.1 Create Fly 的 {@code PressingCategory}</b>（{@code javap -p -c} 核对）：
 * 输入槽 (27,51)、产物 {@code 131 + 19 * i} 同一行 y=51、{@code JEI_SHADOW}(61,41)、
 * {@code JEI_LONG_ARROW}(52,54)、装饰模型原点 (73,-16)、高度 70。</p>
 *
 * <p><b>为什么这样归一化：</b>置物台压印在游戏内是 {@code PressHammerItem.findPressingRecipe}
 * （{@code AllRecipeTypes.PRESSING}）+ 本模组 L3 {@code HandMadePressingRecipe}
 * （{@code tool = press_hammer_depot}）。两者都是 {@link CreateSingleStackRollableRecipe}，
 * 一个显示类型搞定，无需上游那套 {@code CondensedRecipe} 代理。</p>
 *
 * <p><b>装饰的坐标处理（与其它类别不同，特此说明）：</b>Create 的机械压床模型原点 y 是
 * <b>-16</b>（模型从面板上方垂下来），换成 16×16 的物品图标后 y=-16 会整块画到面板外，
 * 所以图标落到模型实际可见的区域顶端 y=8，x 仍是 73。</p>
 */
public class HandPressDepotCategory
        extends CreateCategory<RecipeHolder<CreateSingleStackRollableRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.hand_press_depot";

    private static final int INPUT_X = 27;
    private static final int INPUT_Y = 51;
    private static final int OUTPUT_START_X = 131;
    private static final int OUTPUT_Y = 51;
    private static final int OUTPUT_STEP = 19;
    /** 装饰：x 抄模型原点 73；y 由 -16 改为 8（原因见类注释）。 */
    private static final int DECOR_X = 73;
    private static final int DECOR_Y = 8;

    /** 源：{@code AllRecipeTypes.PRESSING} + L3 {@code HandMadePressingRecipe}（置物台那一支）。 */
    public static List<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipes() {
        List<RecipeHolder<CreateSingleStackRollableRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendRollable(list, AllRecipeTypes.PRESSING,
                HandMadeTool.PRESS_HAMMER_DEPOT, null);
        JeiRecipeSource.appendRollable(list, JeiRecipeSource.l3Tool(), HandMadeTool.PRESS_HAMMER_DEPOT,
                recipe -> recipe instanceof HandMadePressingRecipe);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateSingleStackRollableRecipe>> getRecipeType() {
        return ModJeiTypes.HAND_PRESS_DEPOT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (机械压床, 铁板)；这里换成 (冲压锤, 铁锭)。
        return new TwoIconRenderer(ModItems.PRESS_HAMMER, Items.IRON_INGOT);
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
        for (int i = 0; i < results.size(); i++) {
            addChanceSlot(builder, OUTPUT_START_X + i * OUTPUT_STEP, OUTPUT_Y, results.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<CreateSingleStackRollableRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 41);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 54);
        // Create 在这里画机械压床 3D 模型（PressRenderState，原点 y=-16）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.PRESS_HAMMER), DECOR_X, DECOR_Y);
    }
}
