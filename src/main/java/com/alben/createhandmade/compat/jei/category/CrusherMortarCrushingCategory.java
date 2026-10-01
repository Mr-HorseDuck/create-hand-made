package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class CrusherMortarCrushingCategory extends CreateRecipeCategory<CrushingRecipe> {

    /** 抖幅比研钵大一点（粉碎更重） */
    private static final float SHAKE_AMPLITUDE = 2f;
    private static final float SHAKE_SPEED = 0.4f;

    public CrusherMortarCrushingCategory(Info<CrushingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrushingRecipe recipe, IFocusGroup focuses) {
        builder
                .addSlot(RecipeIngredientRole.INPUT, 27, 37)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        List<ProcessingOutput> results = recipe.getRollableResults();
        boolean single = results.size() == 1;
        int i = 0;
        for (ProcessingOutput output : results) {
            int xOffset = i % 2 == 0 ? 0 : 19;
            int yOffset = (i / 2) * -19;
            builder
                    .addSlot(RecipeIngredientRole.OUTPUT,
                            single ? 132 : 126 + xOffset, 37 + yOffset)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack());
            i++;
        }
    }

    @Override
    public void draw(CrushingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 29);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 40);

        // ★ 纯 Y 轴上下抖动（幅度更大）
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float dy = (float) Math.sin((ticks + partialTicks) * SHAKE_SPEED) * SHAKE_AMPLITUDE;

        ItemStack crusherStack = new ItemStack(ModItems.CRUSHER_MORTAR.get());
        GuiGameElement.of(crusherStack)
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f - 16,
                        dy + 8,
                        0)
                .scale(2)
                .render(graphics);
    }
}