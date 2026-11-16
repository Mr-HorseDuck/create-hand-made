package com.alben.createhandmade.compat.jei.category;

import java.util.List;

import com.alben.createhandmade.recipe.HandMadeAssembly;
import com.alben.createhandmade.recipe.HandMadeAssemblyRecipe;
import com.alben.createhandmade.recipe.HandMadeSequencedAssemblyRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI 类别：手搓流水线（{@code create_hand_made:hand_sequenced_assembly}）。
 *
 * <p>布局（177×70，单行）：</p>
 * <pre>
 *   [起始原料] [过渡物品]   [工具1] [工具2] [工具3]        [产物]
 *                          [额外原料] …
 * </pre>
 *
 * <p><b>为什么不用 Create 的 {@code SequencedAssemblyCategory}：</b>那个类别绑死了
 * {@code create:sequenced_assembly} 的 RecipeType 与 Create 的动画子类别体系
 * （压床 / 注液器 / 机械锯 / 部署器的模型），我们的步骤是手搓工具，没有对应机器模型；
 * 而且 JEI 的 {@code RecipeType} 是注册期固定的，无法一个类别收两种 type。
 * 所以这里做一个最简排版：工具用物品槽显示（带 tooltip），其余用箭头串起来。</p>
 *
 * <p><b>步骤上限 {#MAX_STEPS}</b>：单行放不下更多。超过的部分不会丢失 —— 玩家把过渡物品
 * 拿在手上时，Create 注册的进度 tooltip 会列出「当前这一步 + 后续几步」。</p>
 */
@ParametersAreNonnullByDefault
public class HandSequencedAssemblyCategory extends CreateRecipeCategory<HandMadeSequencedAssemblyRecipe> {

    private static final int MAX_STEPS = 3;
    private static final int STEP_X0 = 56;
    private static final int STEP_DX = 24;
    private static final int STEP_Y = 45;
    private static final int TOOL_Y = 22;
    private static final int OUTPUT_X = 150;

    public HandSequencedAssemblyCategory(Info<HandMadeSequencedAssemblyRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, HandMadeSequencedAssemblyRecipe recipe,
                             IFocusGroup focuses) {
        // 起始原料
        builder.addSlot(RecipeIngredientRole.INPUT, 9, STEP_Y)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredient());

        // 该流水线使用的过渡物品（只展示：它是流程状态，不是玩家要准备的原料）
        ItemStack transitional = recipe.getTransitionalItem();
        if (!transitional.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 31, STEP_Y)
                    .addItemStack(transitional);
        }

        // 每一步：工具图标（RENDER_ONLY，带 tooltip）+ 该步除过渡物品外的额外原料
        int x = STEP_X0;
        int drawn = 0;
        for (SequencedRecipe<?> step : recipe.getSequence()) {
            if (drawn >= MAX_STEPS) {
                break;
            }
            addStep(builder, step, x);
            x += STEP_DX;
            drawn++;
        }

        // 产物（最多两个槽，多的靠 JEI 的输出列表/tooltip）
        int[] ys = {STEP_Y, STEP_Y - 20};
        int i = 0;
        for (ProcessingOutput output : recipe.resultPool) {
            if (i >= ys.length) {
                break;
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, ys[i])
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addItemStack(output.getStack());
            i++;
        }
    }

    /** 一步：上方工具物品，下方该步的额外原料（第 0 槽已被换成过渡物品，故从 1 开始）。 */
    private void addStep(IRecipeLayoutBuilder builder, SequencedRecipe<?> step, int x) {
        fromAssemblyRecipe(step).ifPresent(assembly -> {
            Item tool = HandMadeAssembly.toolItem(assembly.getTool());
            if (tool != null) {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, x, TOOL_Y)
                        .addItemStack(new ItemStack(tool));
            }
        });

        ProcessingRecipe<?, ?> stepRecipe = step.getRecipe();
        List<Ingredient> ingredients = stepRecipe.getIngredients();
        for (int i = 1; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, STEP_Y)
                    .addIngredients(ingredients.get(i));
            break; // 单行布局只画第一个额外原料；更多原料见该步自身配方
        }
    }

    private static java.util.Optional<HandMadeAssemblyRecipe> fromAssemblyRecipe(SequencedRecipe<?> step) {
        return step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe assembly
                ? java.util.Optional.of(assembly)
                : java.util.Optional.empty();
    }

    @Override
    protected void draw(HandMadeSequencedAssemblyRecipe recipe, IRecipeSlotsView slotsView,
                        GuiGraphics graphics, double mouseX, double mouseY) {
        // 「起始原料 → 产物」的长箭头（与 Create 各手搓类别保持同一视觉语言）
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 41);
    }
}
