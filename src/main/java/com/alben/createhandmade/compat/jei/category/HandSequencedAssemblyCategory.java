package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.category.sequenced.HandMadeAssemblySubCategory;
import com.alben.createhandmade.recipe.HandMadeAssembly;
import com.alben.createhandmade.recipe.HandMadeAssemblyRecipe;
import com.alben.createhandmade.recipe.HandMadeSequencedAssemblyRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

/**
 * JEI 类别：手工组装（{@code create_hand_made:hand_sequenced_assembly}）。
 *
 * <p><b>布局刻意与 Create 自己的序列组装类别一致</b>（背景 180×115，坐标系可以逐像素对照），
 * 这样玩家在两个类别之间切换时不需要重新学一遍读法：</p>
 * <pre>
 *  y=2    I     II    III   IV    V                  ← 罗马数字（第几步）
 *  y=15   [附加物/流体] …                            ← 每列上方的槽位
 *  y=50   [工具] …                                   ← 每列的工具图标
 *  y=91   [起始原料]            [产物]                ← 主横带
 *  y=94   ─────── 长箭头 ───────  x3  [ ? ]
 * </pre>
 *
 * <p><b>与 Create 唯一的差别：产物是权重池。</b>Create 的序列组装只有一个产物 + 一个概率，
 * 这里 {@code results} 可以有多条，游戏内是「先求和、再按权重抽一个」。
 * 所以：</p>
 * <ul>
 *   <li>主产物槽画<b>权重最大</b>的那一条，底纹按<b>归一化</b>后的概率；</li>
 *   <li>其余产物用 {@code addInvisibleIngredients} 注册成隐藏产物 —— 看不见，但 JEI 的
 *       「用途查询」照样能从它们反查到这条配方（这是可见性 vs 可发现性的取舍）；</li>
 *   <li>把池子里的全部条目连同归一化百分比塞进主产物槽的 tooltip，一眼能看全；</li>
 *   <li>权重总和 &lt; 1 时（有概率什么都不出）额外画一个 {@code ?} 底纹，语义同 Create 的
 *       "Random salvage" 槽。</li>
 * </ul>
 *
 * <p>步骤列由 {@link HandMadeAssemblySubCategory} 负责（一列一步，宽 25）。
 * 步数超过 {@link #MAX_STEPS} 时折叠成右上角的 {@code +N}，悬停列出被折叠的步骤 ——
 * 被折叠的步骤在游戏内<b>照常参与推进</b>，只是这一行放不下。</p>
 *
 * <p>★ 移植自 1.21.1 分支（0.7.0-beta），按 1.20.1 的 API 改写：
 * {@code ProcessingRecipe} 单泛型参数、{@code getTooltipStrings} 返回 {@code List<Component>}
 * （1.20.1 的 JEI 还没换成 {@code ITooltipBuilder} 重载）、流体用 Create 的 {@code FluidIngredient}。</p>
 */
@ParametersAreNonnullByDefault
public class HandSequencedAssemblyCategory extends CreateRecipeCategory<HandMadeSequencedAssemblyRecipe> {

    /** 一行最多画几步。5 列 × 25 + 4 × 3 = 137px，正好给右上角的 {@code +N} 留出位置。 */
    private static final int MAX_STEPS = 5;
    /** 列间距（Create 的 margin）。 */
    private static final int MARGIN = 3;

    /** 罗马数字：与 Create 一致，第 7 步起显示 {@code -}。 */
    private static final String[] ROMANS = {"I", "II", "III", "IV", "V", "VI", "-"};

    // ---- 主横带（数值全部照抄 Create 的 SequencedAssemblyCategory） ----
    private static final int MAIN_Y = 91;
    private static final int INPUT_X = 27;
    private static final int OUTPUT_X = 132;
    /** 产物是随机池时整体左移 7，给 {@code ?} 底纹让位。 */
    private static final int RANDOM_OFFSET = -7;

    /** 箭头与 ? 底纹所在横带（都在 translate(0,15) 之内，即实际 y = 常量 + 15）。 */
    private static final int BAND_Y = 75;
    private static final int ARROW_X = 52;
    private static final int ARROW_Y = 79;
    private static final int CHANCE_SLOT_X = 150;
    private static final int CHANCE_SLOT_Y = 75;
    private static final int LOOP_ICON_X = 50;
    private static final int LOOP_TEXT_X = 66;

    /** 步骤横带的鼠标热区（罗马数字上方到工具图标下方）。 */
    private static final int STEP_HIT_Y0 = 5;
    private static final int STEP_HIT_Y1 = 84;
    /** 圈数热区（箭头所在的横向区间）。 */
    private static final int REPEAT_HIT_X0 = 55;
    private static final int REPEAT_HIT_W = 65;
    private static final int REPEAT_HIT_Y0 = 92;
    private static final int REPEAT_HIT_Y1 = 116;
    /** {@code +N} 的位置与热区。 */
    private static final int MORE_X = 162;
    private static final int MORE_Y = 2;
    private static final int MORE_HIT_X0 = 158;
    private static final int MORE_HIT_X1 = 180;
    private static final int MORE_HIT_Y0 = 0;
    private static final int MORE_HIT_Y1 = 14;

    /** 一个子类别实例够用：它无状态（不像 Create 的动画子类要记 offset）。 */
    private final SequencedAssemblySubCategory stepColumn = new HandMadeAssemblySubCategory();

    public HandSequencedAssemblyCategory(Info<HandMadeSequencedAssemblyRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HandMadeSequencedAssemblyRecipe recipe,
                          IFocusGroup focuses) {
        int xOffset = isDeterministic(recipe) ? 0 : RANDOM_OFFSET;

        // 起始原料
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + xOffset, MAIN_Y)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredient());

        addOutputSlots(builder, recipe, xOffset);
        addStepColumns(builder, recipe, focuses);
    }

    /** 产物槽：主槽画权重最大的那条，其余注册成隐藏产物（保证 JEI 用途查询能找到）。 */
    private void addOutputSlots(IRecipeLayoutBuilder builder, HandMadeSequencedAssemblyRecipe recipe,
                                int xOffset) {
        float total = totalWeight(recipe);
        ProcessingOutput primary = primaryOutput(recipe);
        if (primary == null) {
            return;
        }

        float normalized = total <= 0f ? 1f : primary.getChance() / total;
        var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + xOffset, MAIN_Y)
                .setBackground(getRenderedSlot(normalized), -1, -1)
                .addItemStack(primary.getStack());

        slot.addRichTooltipCallback((view, tooltip) -> {
            for (ProcessingOutput output : recipe.resultPool) {
                tooltip.add(poolLine(output, total));
            }
            float nothing = 1f - total;
            if (nothing > 1e-4f) {
                tooltip.add(Component.translatable("create_hand_made.recipe.assembly.nothing",
                                Math.round(nothing * 100f))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        });

        for (ProcessingOutput output : recipe.resultPool) {
            if (output == primary) {
                continue;
            }
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .addItemStack(output.getStack());
        }
    }

    /** 步骤列：按 Create 的规则累加宽度后整体居中，每列自己往里摆槽位。 */
    private void addStepColumns(IRecipeLayoutBuilder builder, HandMadeSequencedAssemblyRecipe recipe,
                                IFocusGroup focuses) {
        List<SequencedRecipe<?>> sequence = recipe.getSequence();
        int visible = Math.min(sequence.size(), MAX_STEPS);
        int x = stepStartX(visible);
        for (int i = 0; i < visible; i++) {
            stepColumn.setRecipe(builder, sequence.get(i), focuses, x);
            x += stepColumn.getWidth() + MARGIN;
        }
    }

    /** 步骤块左缘：整块在背景里居中。 */
    private int stepStartX(int steps) {
        if (steps <= 0) {
            return getBackground().getWidth() / 2;
        }
        int blockWidth = steps * stepColumn.getWidth() + (steps - 1) * MARGIN;
        return (getBackground().getWidth() - blockWidth) / 2;
    }

    @Override
    public void draw(HandMadeSequencedAssemblyRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        boolean deterministic = isDeterministic(recipe);
        int xOffset = deterministic ? 0 : RANDOM_OFFSET;

        // ---- 下横带：长箭头 / 圈数 / ? 底纹（整条平移 15px，与 Create 一致） ----
        graphics.pose().pushPose();
        graphics.pose().translate(0, 15, 0);

        AllGuiTextures.JEI_LONG_ARROW.render(graphics, ARROW_X + xOffset, ARROW_Y);

        if (!deterministic) {
            AllGuiTextures.JEI_CHANCE_SLOT.render(graphics, CHANCE_SLOT_X + xOffset, CHANCE_SLOT_Y);
            Component mark = Component.literal("?").withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, mark,
                    CHANCE_SLOT_X + xOffset + 8 - font.width(mark) / 2,
                    CHANCE_SLOT_Y + 5, 0xefefef, false);
        }

        if (recipe.getLoops() > 1) {
            AllIcons.I_SEQ_REPEAT.render(graphics, LOOP_ICON_X + xOffset, BAND_Y);
            graphics.drawString(font, Component.literal("x" + recipe.getLoops()),
                    LOOP_TEXT_X + xOffset, BAND_Y + 5, 0x888888, false);
        }

        graphics.pose().popPose();

        // ---- 步骤列：罗马数字 + 工具图标 ----
        List<SequencedRecipe<?>> sequence = recipe.getSequence();
        int visible = Math.min(sequence.size(), MAX_STEPS);
        int x = stepStartX(visible);

        graphics.pose().pushPose();
        graphics.pose().translate(x, 0, 0);
        for (int i = 0; i < visible; i++) {
            Component numeral = Component.literal(ROMANS[Math.min(i, ROMANS.length - 1)]);
            graphics.drawString(font, numeral,
                    (stepColumn.getWidth() - font.width(numeral)) / 2, 2, 0x888888, false);
            stepColumn.draw(sequence.get(i), graphics, mouseX, mouseY, i);
            graphics.pose().translate(stepColumn.getWidth() + MARGIN, 0, 0);
        }
        graphics.pose().popPose();

        // ---- 折叠步数 ----
        if (sequence.size() > MAX_STEPS) {
            graphics.drawString(font, Component.literal("+" + (sequence.size() - MAX_STEPS)),
                    MORE_X, MORE_Y, 0x888888, false);
        }
    }

    /**
     * 悬停提示：产物池明细 / 圈数 / 单步说明 / 被折叠的步数。
     *
     * <p>这些步骤仍然参与游戏内的推进，只是这一行放不下。</p>
     */
    @Override
    @SuppressWarnings("removal")   // JEI 15.x 仍调用它；未来换成 ITooltipBuilder 重载即可
    public List<Component> getTooltipStrings(HandMadeSequencedAssemblyRecipe recipe,
                                             IRecipeSlotsView slotsView, double mouseX, double mouseY) {
        List<SequencedRecipe<?>> sequence = recipe.getSequence();
        List<Component> tooltip = new ArrayList<>();

        // 被折叠的步骤
        if (sequence.size() > MAX_STEPS
                && mouseX >= MORE_HIT_X0 && mouseX <= MORE_HIT_X1
                && mouseY >= MORE_HIT_Y0 && mouseY <= MORE_HIT_Y1) {
            tooltip.add(Component.translatable("create_hand_made.recipe.assembly.more_steps",
                            sequence.size() - MAX_STEPS)
                    .withStyle(ChatFormatting.GRAY));
            for (int i = MAX_STEPS; i < sequence.size(); i++) {
                tooltip.add(stepLine(i + 1, sequence.get(i)));
            }
            return tooltip;
        }

        // 圈数
        int xOffset = isDeterministic(recipe) ? 0 : RANDOM_OFFSET;
        if (recipe.getLoops() > 1
                && mouseX >= REPEAT_HIT_X0 + xOffset && mouseX < REPEAT_HIT_X0 + REPEAT_HIT_W + xOffset
                && mouseY >= REPEAT_HIT_Y0 && mouseY < REPEAT_HIT_Y1) {
            tooltip.add(Component.translatable("create.recipe.assembly.repeat", recipe.getLoops())
                    .withStyle(ChatFormatting.GRAY));
            return tooltip;
        }

        // 单步说明：按列宽反查鼠标落在哪一列
        if (mouseY > STEP_HIT_Y0 && mouseY < STEP_HIT_Y1) {
            int visible = Math.min(sequence.size(), MAX_STEPS);
            double relativeX = mouseX - stepStartX(visible);
            for (int i = 0; i < visible; i++) {
                if (relativeX >= 0 && relativeX < stepColumn.getWidth()) {
                    tooltip.add(Component.translatable("create.recipe.assembly.step", i + 1)
                            .withStyle(ChatFormatting.GRAY));
                    tooltip.add(stepDescription(sequence.get(i)));
                    return tooltip;
                }
                relativeX -= stepColumn.getWidth() + MARGIN;
            }
        }

        return tooltip;
    }

    // ==================== 小工具 ====================

    /**
     * 产物是否确定：只有一条且概率补满 1。
     *
     * <p>不确定时整体左移 {@code -7} 并画 {@code ?} 底纹，与 Create 的判定口径一致。</p>
     */
    private static boolean isDeterministic(HandMadeSequencedAssemblyRecipe recipe) {
        return recipe.resultPool.size() == 1 && recipe.resultPool.get(0).getChance() >= 1f;
    }

    private static float totalWeight(HandMadeSequencedAssemblyRecipe recipe) {
        float total = 0f;
        for (ProcessingOutput output : recipe.resultPool) {
            total += output.getChance();
        }
        return total;
    }

    /** 权重最大的产物（并列时取靠前的）；产物池为空时返回 {@code null}。 */
    private static ProcessingOutput primaryOutput(HandMadeSequencedAssemblyRecipe recipe) {
        ProcessingOutput best = null;
        for (ProcessingOutput output : recipe.resultPool) {
            if (best == null || output.getChance() > best.getChance()) {
                best = output;
            }
        }
        return best;
    }

    /** 产物池的一行：{@code 物品名 40%}。 */
    private static Component poolLine(ProcessingOutput output, float total) {
        int percent = Math.round((total <= 0f ? 1f : output.getChance() / total) * 100f);
        return Component.empty()
                .append(output.getStack().getHoverName())
                .append(Component.literal(" " + percent + "%").withStyle(ChatFormatting.GOLD));
    }

    /** 单步的一行：{@code #2 冲压锤 · 燧石}。 */
    private static Component stepLine(int index, SequencedRecipe<?> step) {
        return Component.literal("#" + index + " ")
                .append(stepDescription(step));
    }

    /** 一步的说明文本：{@code <工具>}，有附加物 / 流体时追加 {@code · <细节>}。 */
    private static Component stepDescription(SequencedRecipe<?> step) {
        if (step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe assembly) {
            return HandMadeAssembly.stepDescription(assembly.getTool(),
                    HandMadeAssembly.stepDetail(step.getRecipe()))
                    .copy()
                    .withStyle(ChatFormatting.DARK_GREEN);
        }
        return Component.literal(step.getRecipe().getClass().getSimpleName())
                .withStyle(ChatFormatting.DARK_GREEN);
    }
}
