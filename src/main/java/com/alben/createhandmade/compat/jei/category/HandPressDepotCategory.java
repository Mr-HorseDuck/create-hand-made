package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
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
public class HandPressDepotCategory extends CreateRecipeCategory<PressingRecipe> {

    private static final float SHAKE_AMPLITUDE = 2f;
    private static final float SHAKE_SPEED = 0.15f;

    public HandPressDepotCategory(Info<PressingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PressingRecipe recipe, IFocusGroup focuses) {
        // ★ 照抄 HandSawCategory 的坐标
        builder
                .addSlot(RecipeIngredientRole.INPUT, 27, 29)
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
                            single ? 132 : 126 + xOffset, 29 + yOffset)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack());
            i++;
        }
    }

    @Override
    public void draw(PressingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        // ★ 阴影 + 长箭头，坐标照抄 HandSawCategory
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 21);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 32);

        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float impact = (float) Math.abs(Math.sin((ticks + partialTicks) * SHAKE_SPEED));
        float dy = impact * SHAKE_AMPLITUDE;

        ItemStack hammerStack = new ItemStack(ModItems.PRESS_HAMMER.get());
        GuiGameElement.of(hammerStack)
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f - 16,
                        dy,
                        0)
                .scale(2)
                .render(graphics);
    }

}