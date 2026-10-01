package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
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

@ParametersAreNonnullByDefault
public class HandInfusionGunCategory extends CreateRecipeCategory<FillingRecipe> {

    public HandInfusionGunCategory(Info<FillingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FillingRecipe recipe, IFocusGroup focuses) {
        // ★ 完全照抄 SpoutCategory.setRecipe
        builder
                .addSlot(RecipeIngredientRole.INPUT, 27, 51)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        addFluidSlot(builder, 27, 32, recipe.getRequiredFluid());

        builder
                .addSlot(RecipeIngredientRole.OUTPUT, 132, 51)
                .setBackground(getRenderedSlot(), -1, -1)
                .addItemStack(getResultItem(recipe));
    }

    @Override
    public void draw(FillingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);

        // ★ 灌注枪：轻轻上下摇晃
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float dy = (float) Math.sin((ticks + partialTicks) * 0.4f) * 3f;

        ItemStack gunStack = new ItemStack(ModItems.INFUSION_GUN.get());
        GuiGameElement.of(gunStack)
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f - 13,
                        22 + dy,
                        0)
                .scale(2)
                .render(graphics);
    }
}