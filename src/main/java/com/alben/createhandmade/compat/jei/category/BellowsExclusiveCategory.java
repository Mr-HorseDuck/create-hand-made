package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.registry.ModItems;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 风箱 · 第三方兼容（{@code create_hand_made:bellows_recipe} 里 {@code fan_type} <b>非 create 命名空间</b>的配方）。
 *
 * <p>原本 4 个风箱类别（熔炼 / 烟熏 / 缠魂 / 洗涤）各自只收 {@code fan_type} 等于对应 Create
 * 内置类型的独占配方，于是 {@code fan_type: dndesires:sanding} / {@code create_more_catalysts:snow_flow}
 * 这类第三方类型的配方在 JEI 里<b>根本看不到</b>。本类别把它们统一收进来：</p>
 * <ul>
 *   <li>直接以 {@link HandMadeBellowsRecipe} 作为类别泛型 —— 它本身就是
 *       {@code ProcessingRecipe} 子类，{@link CreateRecipeCategory} 要求的
 *       "有原料 / 有可滚动产物" 接口它全都有，<b>不需要</b>像 4 个原版类别那样包一层显示代理；</li>
 *   <li>介质图标不再固定写死，而是按<b>这条配方自己的</b> {@code fan_type} 去
 *       {@link BellowsMediaRegistry#getMediaFor} 查 —— 所以同一条配方换类型，图标跟着换；</li>
 *   <li>多产物与概率走 {@code getRollableResults()}，与缠魂 / 洗涤类别口径一致；</li>
 *   <li>排版与 4 个原版类别<b>逐坐标对齐</b>（输入 27,51 / 介质 27,5 / 产物 132|126,51 /
 *       阴影 62,57 / 箭头 126,29 / 风箱图标居中 y=22）；顶上多一行
 *       {@code 类型: <fan_type>}，让玩家看清这条配方来自哪个模组的鼓风。</li>
 * </ul>
 */
public class BellowsExclusiveCategory extends CreateRecipeCategory<HandMadeBellowsRecipe> {

    /** 类型标签文字的颜色（与 Create 类别里的灰字一致）。 */
    private static final int LABEL_COLOR = 0xFF404040;

    /** 类型标签的左上角坐标（顶部右侧，避开介质槽与风箱图标）。 */
    private static final int LABEL_X = 50;
    private static final int LABEL_Y = 6;

    public BellowsExclusiveCategory(Info<HandMadeBellowsRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HandMadeBellowsRecipe recipe, IFocusGroup focuses) {
        // 原料：与 4 个原版类别同样的位置（左侧）
        if (!recipe.getIngredients().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(recipe.getIngredients().get(0));
        }

        // 介质：按这条配方的 fan_type 动态查
        List<ItemStack> media = mediaFor(recipe);
        List<Fluid> fluids = fluidsFor(recipe);
        if (!media.isEmpty() || !fluids.isEmpty()) {
            var catalyst = builder.addSlot(RecipeIngredientRole.CATALYST, 27, 5)
                    .setBackground(getRenderedSlot(), -1, -1);
            if (!media.isEmpty()) {
                catalyst.addItemStacks(media);
            }
            for (Fluid fluid : fluids) {
                catalyst.addIngredient(ForgeTypes.FLUID_STACK,
                        new FluidStack(fluid, FluidType.BUCKET_VOLUME));
            }
        }

        // 产物：最多 12 个，按 2 列堆叠
        List<ProcessingOutput> results = recipe.getRollableResults();
        boolean single = results.size() == 1;
        int i = 0;
        for (ProcessingOutput output : results) {
            int xOffset = i % 2 == 0 ? 0 : 19;
            int yOffset = (i / 2) * -19;
            builder.addSlot(RecipeIngredientRole.OUTPUT,
                            single ? 132 : 126 + xOffset, 51 + yOffset)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
            i++;
        }
    }

    /** 查这条配方的介质图标。fan_type 为 null 时返回空列表，不抛异常。 */
    private static List<ItemStack> mediaFor(HandMadeBellowsRecipe recipe) {
        @Nullable ResourceLocation fanTypeId = recipe.getFanType();
        return fanTypeId == null ? List.of() : BellowsMediaRegistry.getMediaFor(fanTypeId);
    }

    /** 这条配方的类型绑定的流体介质。 */
    private static List<Fluid> fluidsFor(HandMadeBellowsRecipe recipe) {
        @Nullable ResourceLocation fanTypeId = recipe.getFanType();
        return fanTypeId == null ? List.of() : BellowsMediaRegistry.getFluidsFor(fanTypeId);
    }

    @Override
    public void draw(HandMadeBellowsRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);

        // 标出这条配方用的是哪个模组的鼓风类型
        Font font = Minecraft.getInstance().font;
        String shownType = fitToWidth(font, fanTypeLabel(recipe),
                getBackground().getWidth() - LABEL_X - 4);
        graphics.drawString(font,
                Component.translatable("jei.create_hand_made.bellows_exclusive.type", shownType),
                LABEL_X, LABEL_Y, LABEL_COLOR, false);

        // 风箱图标带轻微抖动
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float shake = (float) Math.sin((ticks + partialTicks) * 0.4f) * 2f;

        GuiGameElement.of(new ItemStack(ModItems.BELLOWS.get()))
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f - 13,
                        22 + shake,
                        0)
                .scale(2)
                .render(graphics);
    }

    private static String fanTypeLabel(HandMadeBellowsRecipe recipe) {
        @Nullable ResourceLocation fanTypeId = recipe.getFanType();
        return fanTypeId == null ? "?" : fanTypeId.toString();
    }

    private static String fitToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int budget = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(text, budget) + ellipsis;
    }
}
