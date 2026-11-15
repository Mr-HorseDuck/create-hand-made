package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeFillingRecipe;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.content.fluids.transfer.FillingInput;
import com.zurrtum.create.content.fluids.transfer.FillingRecipe;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.foundation.recipe.CreateRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 灌注枪 · 注液（#6）。
 *
 * <p><b>布局逐句照抄 26.1 的 {@code SpoutFillingCategory}</b>（{@code javap -p -c} 核对）：</p>
 * <ul>
 *   <li>输入物品槽 {@code (27, 51)} = {@code ingredient()}；</li>
 *   <li>输入流体槽 {@code (27, 32)} = {@code fluidIngredient()}，槽名固定 {@code "fluid"}
 *       （Create 的 {@code draw} 靠这个名字回找流体，用来给注液口 3D 模型上色）；</li>
 *   <li>输出槽 {@code (132, 51)} = {@code result()}（{@code ItemStackTemplate}）；</li>
 *   <li>{@code draw}：{@code JEI_SHADOW}(62, 57)、{@code JEI_DOWN_ARROW}(126, 29)、
 *       注液口模型原点 {@code (75, 1)}；高度 70。</li>
 * </ul>
 *
 * <p><b>为什么显示类型是接口 {@code CreateRecipe<FillingInput>}：</b>数据源两侧 ——
 * {@code AllRecipeTypes.FILLING}（{@code FillingRecipe}）与本模组 L3
 * {@code HandMadeFillingRecipe} —— 都 {@code implements CreateRecipe<FillingInput>}
 * （{@code javap -p} 实测），而 JEI 的 {@code IRecipeType<T>} 不变型，只有绑这个共同接口
 * 才能把两侧塞进同一个类别。{@code HAND_INFUSION_GUN} 的泛型因此从
 * {@code RecipeHolder<FillingRecipe>} 改成 {@code RecipeHolder<CreateRecipe<FillingInput>>}
 * （<b>UID 未动</b>）。三个 accessor（{@code result / ingredient / fluidIngredient}）
 * 是各自 record 的分量、不在接口上，所以由 {@link View} 按具体 record 分派
 * —— 与 {@code BasinSlots} 对工作盆家族做的是同一件事。</p>
 *
 * <p><b>上游的 {@code SpoutCategory.consumeRecipes}（灌注枪的「转换配方」扩展）本期不做：</b>
 * 26.1 的 Create Fly 把它换成了 {@code SpoutFillingCategory}，该类<b>没有</b>任何
 * {@code consumeRecipes} 之类的静态方法（{@code javap -p} 实测：只有
 * {@code getRecipes / getRecipeType / getTitle / getIcon / getHeight / setRecipe / draw} 与
 * {@code MAX} / {@code idGenerator} 字段）。上游那段逻辑是「空的注液口 + 手上物品 =
 * 换装物品」这类合成式提示，没有 26.1 的对应入口，凭推测补会造出假配方，
 * 所以本类别<b>只显示真正的 {@code FILLING} 配方与 L3 配方</b>。</p>
 *
 * <p><b>不额外过 {@code CAN_BE_AUTOMATED}：</b>与指杆类别同理 —— 服务端
 * {@code InfusionGunItem.findFillingRecipe} 是「手动手持触发」，JEI 里要把「仅手动」的配方
 * 也展示出来，否则玩家会以为某些注液配方不存在。L2 过滤照旧走
 * {@link HandMadeTool#INFUSION_GUN} 的过滤表。</p>
 */
public class HandInfusionGunCategory
        extends CreateCategory<RecipeHolder<CreateRecipe<FillingInput>>> {

    private static final String TITLE_KEY = "jei.create_hand_made.hand_infusion_gun";

    private static final int INPUT_ITEM_X = 27;
    private static final int INPUT_ITEM_Y = 51;
    private static final int INPUT_FLUID_X = 27;
    private static final int INPUT_FLUID_Y = 32;
    private static final int OUTPUT_X = 132;
    private static final int OUTPUT_Y = 51;
    /** 流体槽名，与 Create 一致（Create 的 draw 靠它回找输入流体）。 */
    private static final String FLUID_SLOT_NAME = "fluid";
    /** 装饰：x 抄 Create 注液口模型原点 75；y 由 1 改成 8，让 16×16 图标落在注液口中段。 */
    private static final int DECOR_X = 75;
    private static final int DECOR_Y = 8;
    private static final int HEIGHT = 70;

    /**
     * 源：{@code AllRecipeTypes.FILLING} 在前、L3 {@code HandMadeFillingRecipe}
     * （{@code tool = infusion_gun}）在后 —— 与 {@code InfusionGunItem.findFillingRecipe}
     * 的查询顺序一致。
     */
    public static List<RecipeHolder<CreateRecipe<FillingInput>>> getRecipes() {
        List<RecipeHolder<CreateRecipe<FillingInput>>> list = new ArrayList<>();
        JeiRecipeSource.appendFilling(list, AllRecipeTypes.FILLING, HandMadeTool.INFUSION_GUN, null);
        JeiRecipeSource.appendFilling(list, JeiRecipeSource.l3Tool(), HandMadeTool.INFUSION_GUN,
                recipe -> recipe instanceof HandMadeFillingRecipe handMade
                        && handMade.tool() == HandMadeTool.INFUSION_GUN);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<CreateRecipe<FillingInput>>> getRecipeType() {
        return ModJeiTypes.HAND_INFUSION_GUN;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (注液口, 水桶)；这里换成 (灌注枪, 水桶)。
        return new TwoIconRenderer(ModItems.INFUSION_GUN, Items.WATER_BUCKET);
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          RecipeHolder<CreateRecipe<FillingInput>> holder, IFocusGroup focuses) {
        View view = View.of(holder.value());
        if (view == null) {
            // FILLING 与 L3 里只会有那两个 record；走到这里说明有别的实现被塞进来了，
            // 宁可空布局也不抛异常（注册期抛异常会让整个 JEI 插件失效）。
            return;
        }

        builder.addInputSlot(INPUT_ITEM_X, INPUT_ITEM_Y)
                .setBackground(SLOT, -1, -1)
                .add(view.ingredient());

        // 流体槽：CreateCategory.addFluidSlot 有 FluidIngredient / FluidStack 两个重载，
        // 这里给的是配方里的 FluidIngredient（与 SpoutFillingCategory 同一调用）。
        addFluidSlot(builder, INPUT_FLUID_X, INPUT_FLUID_Y, view.fluidIngredient())
                .setBackground(SLOT, -1, -1)
                .setSlotName(FLUID_SLOT_NAME);

        // 产物：26.1 配方层是 ItemStackTemplate，槽位直接 add(ItemStackTemplate)。
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .setBackground(SLOT, -1, -1)
                .add(view.result());
    }

    @Override
    public void draw(RecipeHolder<CreateRecipe<FillingInput>> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);
        // Create 在这里按输入流体的颜色画注液口 3D 模型（SpoutRenderState，原点 (75,1)，
        // 还需要一个全局 idGenerator 轮转）；手工工具换成就地画灌注枪图标。
        graphics.item(new ItemStack(ModItems.INFUSION_GUN), DECOR_X, DECOR_Y);
    }

    /**
     * 把 {@code CreateRecipe<FillingInput>} 归一成布局要的三样东西。
     *
     * <p>接口上只有 default 方法（{@code javap -p} 实测：{@code placementInfo /
     * recipeBookCategory / group / showNotification / isSpecial} + 静态 {@code getJunk}），
     * 三个配方分量在各自的 record 上：{@code FillingRecipe(ItemStackTemplate result,
     * Ingredient ingredient, FluidIngredient fluidIngredient)} 与
     * {@code HandMadeFillingRecipe(HandMadeTool tool, ...同样三个字段)}。
     * 分派表刻意只列这两个（本模组会碰到的全部实现）。</p>
     */
    private record View(Ingredient ingredient, FluidIngredient fluidIngredient,
                        ItemStackTemplate result) {

        private static @Nullable View of(CreateRecipe<FillingInput> recipe) {
            if (recipe instanceof FillingRecipe filling) {
                return new View(filling.ingredient(), filling.fluidIngredient(), filling.result());
            }
            if (recipe instanceof HandMadeFillingRecipe handMade) {
                return new View(handMade.ingredient(), handMade.fluidIngredient(), handMade.result());
            }
            return null;
        }
    }
}
