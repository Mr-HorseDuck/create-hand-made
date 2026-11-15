package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeApplicationRecipe;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
 * 指杆 · 物品应用（#12）。
 *
 * <p><b>布局逐句照抄 26.1 的 {@code DeployingCategory}</b>（{@code javap -p -c} 核对）：</p>
 * <ul>
 *   <li>手持物品槽 {@code (51, 5)} = {@code ingredient()}；{@code keepHeldItem()} 为真时挂
 *       {@code KEEP_HELD} 富提示（「工具不消耗」）；</li>
 *   <li>被应用的物品槽 {@code (27, 51)} = {@code target()}；</li>
 *   <li>产物：单条 → {@code addChanceSlot(132, 51)}；多条 → 两列 {@code 132 / 151}，
 *       每行 −19（{@code 51 + (i / 2) * -19}）；</li>
 *   <li>{@code JEI_SHADOW}(62, 57)、{@code JEI_DOWN_ARROW} 的 x=126，
 *       y = {@code results.size() > 2 ? 10 : 29}；装饰模型原点 {@code (75, -10)}；高度 70。</li>
 * </ul>
 *
 * <p><b>为什么这样归一化：</b>指杆在游戏内（{@code PointerItem.findRecipe}）依次查
 * {@code AllRecipeTypes.DEPLOYING}、{@code AllRecipeTypes.ITEM_APPLICATION}，再追加 L3
 * {@code HandMadeApplicationRecipe}。26.1 的 {@link ItemApplicationRecipe} 是<b>接口</b>
 * （{@code DeployerApplicationRecipe} / {@code ManualApplicationRecipe} / L3 record 都
 * implements 它），四个 accessor（{@code ingredient / target / results / keepHeldItem}）
 * 就声明在接口上，所以<b>不再需要</b>上游的 {@code asDeployerProxy} 那一层包装。</p>
 *
 * <p><b>为什么显示类型写 {@code RecipeHolder<ItemApplicationRecipe>} 而不是
 * {@code RecipeHolder<? extends ItemApplicationRecipe>}：</b>Create 的
 * {@code DeployingCategory} 用的是通配符版本，但本模组的类型常量
 * {@link ModJeiTypes#POINTER_APPLICATION} 已经绑成非通配的
 * {@code RecipeHolder<ItemApplicationRecipe>}（切片 1 定的，UID 不许动）。JEI 的
 * {@code IRecipeType<T>} 不变型，所以这里必须与常量保持完全一致；erasure 之后与
 * Create 那份是同一份字节码，布局不受影响。</p>
 *
 * <p><b>不额外过 {@code CAN_BE_AUTOMATED}：</b>服务端 {@code PointerItem.findRecipe} 里那句
 * 「automation 过滤保留在调用方：配方池与 JEI 都不过滤这些『仅手动』配方」就是本层的依据 ——
 * JEI 里要把「仅手动」的配方也展示出来，否则玩家会以为某些应用配方不存在。</p>
 */
public class PointerApplicationCategory
        extends CreateCategory<RecipeHolder<ItemApplicationRecipe>> {

    private static final String TITLE_KEY = "jei.create_hand_made.pointer_application";

    private static final int HELD_ITEM_X = 51;
    private static final int HELD_ITEM_Y = 5;
    private static final int TARGET_X = 27;
    private static final int TARGET_Y = 51;
    /** 单条产物与两列布局的左列是同一个 x（132），所以不再单独留一个常量。 */
    private static final int OUTPUT_LEFT_X = 132;
    private static final int OUTPUT_RIGHT_X = 151;
    private static final int OUTPUT_Y = 51;
    private static final int OUTPUT_ROW_STEP = 19;
    /** 装饰：x 抄 Create 的 {@code DeployerRenderState} 原点 75；y 由 −10 改为 8。 */
    private static final int DECOR_X = 75;
    private static final int DECOR_Y = 8;

    /**
     * 源：{@code DEPLOYING} → {@code ITEM_APPLICATION} → L3 {@code HandMadeApplicationRecipe}
     * （{@code tool = pointer}）。顺序与游戏内优先级一致。
     */
    public static List<RecipeHolder<ItemApplicationRecipe>> getRecipes() {
        List<RecipeHolder<ItemApplicationRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendApplication(list, AllRecipeTypes.DEPLOYING, HandMadeTool.POINTER, null);
        JeiRecipeSource.appendApplication(list, AllRecipeTypes.ITEM_APPLICATION,
                HandMadeTool.POINTER, null);
        JeiRecipeSource.appendApplication(list, JeiRecipeSource.l3Tool(), HandMadeTool.POINTER,
                recipe -> recipe instanceof HandMadeApplicationRecipe handMade
                        && handMade.tool() == HandMadeTool.POINTER);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<ItemApplicationRecipe>> getRecipeType() {
        return ModJeiTypes.POINTER_APPLICATION;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是单图标 (机械手)；这里的「应用」= 指杆 + 被应用的物品，所以用双图标。
        return new TwoIconRenderer(ModItems.POINTER, Items.IRON_INGOT);
    }

    @Override
    public int getHeight() {
        return 70;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ItemApplicationRecipe> holder,
                          IFocusGroup focuses) {
        ItemApplicationRecipe recipe = holder.value();

        IRecipeSlotBuilder heldItemSlot = builder.addInputSlot(HELD_ITEM_X, HELD_ITEM_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.ingredient());
        if (recipe.keepHeldItem()) {
            // 与 DeployingCategory 同一个静态提示（CreateCategory.KEEP_HELD）。
            heldItemSlot.addRichTooltipCallback(KEEP_HELD);
        }

        builder.addInputSlot(TARGET_X, TARGET_Y)
                .setBackground(SLOT, -1, -1)
                .add(recipe.target());

        List<ProcessingOutput> results = recipe.results();
        int size = results.size();
        if (size == 1) {
            addChanceSlot(builder, OUTPUT_LEFT_X, OUTPUT_Y, results.getFirst());
            return;
        }
        for (int i = 0; i < size; i++) {
            int x = (i % 2 == 0) ? OUTPUT_LEFT_X : OUTPUT_RIGHT_X;
            int y = OUTPUT_Y - (i / 2) * OUTPUT_ROW_STEP;
            addChanceSlot(builder, x, y, results.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<ItemApplicationRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        // 产物超过两条时箭头要上移 19（给第二行产物腾位置）——Create 的写法是直接三元。
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126,
                holder.value().results().size() > 2 ? 10 : 29);
        // Create 在这里画机械手 3D 模型（DeployerRenderState，原点 (75,-10)）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.POINTER), DECOR_X, DECOR_Y);
    }
}
