package com.alben.createhandmade.compat.jei.category;

import java.util.ArrayList;
import java.util.List;

import com.alben.createhandmade.recipe.HandMadeAssembly;
import com.alben.createhandmade.recipe.HandMadeAssemblyRecipe;
import com.alben.createhandmade.recipe.HandMadeSequencedAssemblyRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI 类别：手工组装（{@code create_hand_made:hand_sequenced_assembly}）。
 *
 * <p>布局（177×92，两条横带 + 箭头）：</p>
 * <pre>
 *  y=14  [附加物/流体] [附加物/流体] …                          [+N]
 *  y=40  [起始原料] [工具1] [工具2] [工具3] …            [产物1]
 *                                                          [产物2]
 *  y=64            ─────────── 长箭头 ───────────
 *  y=75                     ↻ x3（圈数，箭头下方居中）
 * </pre>
 *
 * <p><b>步骤块整体居中</b>：把「起始原料槽右缘 ~ 产物槽左缘」当作可用区间，
 * 按步数算出整块宽度再居中，所以 1 步和 5 步都不会贴着左边。</p>
 *
 * <p><b>概率</b>：序列组装的 {@code results} 与 {@code create:sequenced_assembly} 一样是
 * <b>权重池</b>（Create 的 {@code rollResult} 会先求和再按权重抽一个，不是每个产物独立判定）。
 * 所以这里显示的是<b>归一化后</b>的百分比（{@code chance / Σchance}），
 * 底纹也用归一化值决定（{@code getRenderedSlot(float)}）—— 与 Create 的
 * {@code getOutputChance()} 口径一致。</p>
 */
@ParametersAreNonnullByDefault
public class HandSequencedAssemblyCategory extends CreateRecipeCategory<HandMadeSequencedAssemblyRecipe> {

    /** 一行最多画几步（再多的部分在右上角显示 {@code +N}，悬停可看剩余步骤的工具）。 */
    private static final int MAX_STEPS = 6;
    /** 列间距：18（槽宽）+ 1 间隙，6 列正好塞进可用区间。 */
    private static final int STEP_DX = 19;
    private static final int SLOT = 18;

    /** 步骤附加物 / 流体所在横带。 */
    private static final int EXTRA_Y = 14;
    /**
     * {@code +N} 文字的位置与鼠标热区。
     *
     * <p>必须躲开「第二个产物槽」（在 {@code MAIN_Y - OUTPUT_DY = 22}），否则悬停产物槽顶部
     * 会被我们的 tooltip 抢走。</p>
     */
    private static final int MORE_TEXT_Y = 4;
    private static final int MORE_HIT_Y0 = 0;
    private static final int MORE_HIT_Y1 = 20;
    /** 起始原料、工具、产物所在横带。 */
    private static final int MAIN_Y = 40;
    /** 长箭头。 */
    private static final int ARROW_X = 52;
    private static final int ARROW_Y = 64;
    /** 圈数图标 + 文字（箭头下方居中）。 */
    private static final int LOOP_Y = 74;
    /** {@code I_SEQ_REPEAT} 图标宽度（Create 的 16×16 图标）。 */
    private static final int LOOP_ICON = 16;

    private static final int INPUT_X = 9;
    private static final int OUTPUT_X = 150;
    private static final int OUTPUT_DY = 18;

    /** 步骤块可用的横向区间：[起始原料槽右缘 + 4, 产物槽左缘 - 4]。 */
    private static final int STEP_SPAN_START = INPUT_X + SLOT + 4;
    private static final int STEP_SPAN_END = OUTPUT_X - 4;

    public HandSequencedAssemblyCategory(Info<HandMadeSequencedAssemblyRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, HandMadeSequencedAssemblyRecipe recipe,
                             IFocusGroup focuses) {
        // 起始原料
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, MAIN_Y)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredient());

        // 步骤块居中
        int steps = Math.min(recipe.getSequence().size(), MAX_STEPS);
        int startX = stepStartX(steps);
        for (int i = 0; i < steps; i++) {
            addStep(builder, recipe.getSequence().get(i), startX + i * STEP_DX);
        }

        // 产物：概率按权重池归一化后再画底纹 / 显示百分比
        float totalWeight = totalWeight(recipe);
        int y = MAIN_Y;
        for (ProcessingOutput output : recipe.resultPool) {
            if (y < 0) {
                break;
            }
            float normalized = totalWeight <= 0f ? 1f : output.getChance() / totalWeight;

            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, y)
                    .setBackground(getRenderedSlot(normalized), -1, -1)
                    .addItemStack(output.getStack());
            if (normalized < 1f) {
                slot.addTooltipCallback((view, tooltip) -> tooltip.add(1,
                        Component.translatable("create_hand_made.recipe.assembly.chance",
                                        Math.round(normalized * 100f))
                                .withStyle(ChatFormatting.GOLD)));
            }
            y -= OUTPUT_DY;
        }
    }

    /** 步骤块居中：按步数算整块宽度，再在可用区间里居中。 */
    private static int stepStartX(int steps) {
        if (steps <= 0) {
            return STEP_SPAN_START;
        }
        int blockWidth = (steps - 1) * STEP_DX + SLOT;
        int center = (STEP_SPAN_START + STEP_SPAN_END) / 2;
        int startX = center - blockWidth / 2;
        return Math.max(STEP_SPAN_START, Math.min(startX, STEP_SPAN_END - blockWidth));
    }

    /** 一步：下带工具图标（{@code RENDER_ONLY}，带 tooltip），上带附加物或流体。 */
    private void addStep(IRecipeLayoutBuilder builder, SequencedRecipe<?> step, int x) {
        if (step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe assembly) {
            Item tool = HandMadeAssembly.toolItem(assembly.getTool());
            if (tool != null) {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, x, MAIN_Y)
                        .setBackground(getRenderedSlot(), -1, -1)
                        .addItemStack(new ItemStack(tool));
            }
        }

        ProcessingRecipe<?, ?> stepRecipe = step.getRecipe();
        List<Ingredient> ingredients = stepRecipe.getIngredients();
        for (int i = 1; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, EXTRA_Y)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(ingredients.get(i));
            return; // 一步只有一个附加槽位（应用家族最多一个手持物）
        }

        if (!stepRecipe.getFluidIngredients().isEmpty()) {
            SizedFluidIngredient fluid = stepRecipe.getFluidIngredients().get(0);
            addFluidSlot(builder, x, EXTRA_Y, fluid);
        }
    }

    private static float totalWeight(HandMadeSequencedAssemblyRecipe recipe) {
        float total = 0f;
        for (ProcessingOutput output : recipe.resultPool) {
            total += output.getChance();
        }
        return total;
    }

    @Override
    protected void draw(HandMadeSequencedAssemblyRecipe recipe, IRecipeSlotsView slotsView,
                        GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, ARROW_X, ARROW_Y);

        var font = Minecraft.getInstance().font;
        int seqSize = recipe.getSequence().size();

        // 圈数：Create 的「序列循环」图标 + xN，整组在箭头下方居中
        if (recipe.getLoops() > 1) {
            Component loops = Component.literal("x" + recipe.getLoops());
            int textW = font.width(loops);
            int groupW = LOOP_ICON + 2 + textW;
            int groupX = ARROW_X + 30 - groupW / 2;
            AllIcons.I_SEQ_REPEAT.render(graphics, groupX, LOOP_Y - 2);
            graphics.drawString(font, loops, groupX + LOOP_ICON + 2, LOOP_Y + 3, 0x888888, false);
        }

        // 步骤多于上限：右上角 +N（悬停看剩余步骤，见 getTooltipStrings）
        if (seqSize > MAX_STEPS) {
            graphics.drawString(font, Component.literal("+" + (seqSize - MAX_STEPS)),
                    OUTPUT_X + 6, MORE_TEXT_Y, 0x888888, false);
        }
    }

    /**
     * {@code +N} 的 tooltip：把被折叠掉的步骤逐条列出来，避免「看不到 = 不知道」。
     *
     * <p>这些步骤仍然参与游戏内的推进，只是 JEI 一行放不下。</p>
     */
    @Override
    protected List<Component> getTooltipStrings(HandMadeSequencedAssemblyRecipe recipe,
                                                IRecipeSlotsView slotsView, double mouseX, double mouseY) {
        int seqSize = recipe.getSequence().size();
        if (seqSize <= MAX_STEPS) {
            return List.of();
        }
        if (mouseX < OUTPUT_X - 2 || mouseX > OUTPUT_X + 20 || mouseY < MORE_HIT_Y0 || mouseY > MORE_HIT_Y1) {
            return List.of();
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("create_hand_made.recipe.assembly.more_steps",
                        seqSize - MAX_STEPS)
                .withStyle(ChatFormatting.GRAY));
        for (int i = MAX_STEPS; i < seqSize; i++) {
            SequencedRecipe<?> step = recipe.getSequence().get(i);
            Component line = Component.literal("#" + (i + 1) + " ").append(stepName(step));
            Component detail = HandMadeAssembly.stepDetail(step.getRecipe());
            if (detail != null) {
                line = line.copy()
                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(detail);
            }
            tooltip.add(line);
        }
        return tooltip;
    }

    /** 折叠步骤的工具名（机器步骤则退回配方类名）。 */
    private static Component stepName(SequencedRecipe<?> step) {
        if (step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe assembly) {
            return Component.translatable(HandMadeAssembly.toolItemKey(assembly.getTool()));
        }
        return Component.literal(step.getRecipe()
                .getClass()
                .getSimpleName());
    }


}
