package com.alben.createhandmade.compat.jei.category.sequenced;

import com.alben.createhandmade.recipe.HandMadeAssembly;
import com.alben.createhandmade.recipe.HandMadeAssemblyRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * 序列组装 JEI 步骤的「本模组」子类别：一列 = 一步。
 *
 * <p>结构与 Create 的 {@code SequencedAssemblySubCategory.AssemblyDeploying} 完全一致，
 * 只是把「机器动画」换成「工具物品图标」——手搓没有机器可画：</p>
 * <pre>
 *  y=15   [附加物 / 流体槽]      ← setRecipe 里加，落在这一列的正上方
 *  y=50   [工具物品]
 * </pre>
 *
 * <p><b>为什么槽位放在子类里而不是类别里</b>：Create 的序列组装类别按子类别的
 * {@link #getWidth()} 累加宽度、居中排布，每列的槽位必须跟着列坐标走（{@code x + 4}）。
 * 放在类别里就得自己重算一遍列坐标，容易和 {@code draw()} 里的位移不同步。</p>
 *
 * <p><b>客户端专用</b>：只在 JEI 建索引与绘制时（客户端）被加载，
 * 由 {@link HandMadeAssemblyRecipe#getJEISubCategory()} 返回的 supplier 构造。</p>
 *
 * <p>★ 移植自 1.21.1 分支（0.7.0-beta）；1.20.1 的 {@code PoseStack} 用法与 1.21 一致。</p>
 */
public class HandMadeAssemblySubCategory extends SequencedAssemblySubCategory {

    /** 与 Create 内置子类同宽（25），这样本模组的步骤列和原版序列组装看起来是一套。 */
    private static final int WIDTH = 25;
    /** 槽位/图标相对列左上角的偏移，取自 Create 的 AssemblyDeploying / AssemblySpouting。 */
    private static final int SLOT_DX = 4;
    private static final int SLOT_Y = 15;
    private static final int TOOL_DX = 4;
    private static final int TOOL_Y = 50;

    public HandMadeAssemblySubCategory() {
        super(WIDTH);
    }

    /**
     * 这一步的「额外准备」：第 1 个及之后的物品输入（指杆的手持物等）或流体输入（灌注枪）。
     *
     * <p>序列组装初始化时第 0 个物品槽已被换成过渡物品，所以物品从下标 1 开始看，
     * 与 {@link HandMadeAssembly#stepDetail(ProcessingRecipe)} 的约定一致。</p>
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SequencedRecipe<?> recipe, IFocusGroup focuses, int x) {
        ProcessingRecipe<?> stepRecipe = recipe.getRecipe();
        List<Ingredient> ingredients = stepRecipe.getIngredients();
        for (int i = 1; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, x + SLOT_DX, SLOT_Y)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                    .addIngredients(ingredients.get(i));
            // 一步只有一个附加槽位（应用家族最多一个手持物）
            return;
        }

        List<FluidIngredient> fluids = stepRecipe.getFluidIngredients();
        if (!fluids.isEmpty()) {
            CreateRecipeCategory.addFluidSlot(builder, x + SLOT_DX, SLOT_Y, fluids.get(0));
        }
    }

    @Override
    public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        if (!(recipe.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe handmade)) {
            return;
        }
        ItemLike toolItem = HandMadeAssembly.toolItem(handmade.getTool());
        if (toolItem == null) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // 调用方已经把 pose 平移到本列左缘；这里只补列内偏移
        pose.translate(TOOL_DX, TOOL_Y, 0);
        graphics.renderItem(new ItemStack(toolItem), 0, 0);
        pose.popPose();
    }
}
