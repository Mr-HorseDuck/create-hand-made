package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;
import com.alben.createhandmade.registry.ModItems;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BellowsExclusiveCategory extends CreateRecipeCategory<HandMadeBellowsRecipe> {

    private static final int LABEL_COLOR = 0xFF404040;
    private static final int LABEL_X = 50;
    private static final int LABEL_Y = 6;

    public BellowsExclusiveCategory(Info<HandMadeBellowsRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HandMadeBellowsRecipe recipe, IFocusGroup focuses) {
        if (!recipe.getIngredients().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(recipe.getIngredients().get(0));
        }

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

    private static List<ItemStack> mediaFor(HandMadeBellowsRecipe recipe) {
        @Nullable ResourceLocation fanTypeId = recipe.getFanType();
        return fanTypeId == null ? List.of() : BellowsMediaRegistry.getMediaFor(fanTypeId);
    }

    private static List<Fluid> fluidsFor(HandMadeBellowsRecipe recipe) {
        @Nullable ResourceLocation fanTypeId = recipe.getFanType();
        return fanTypeId == null ? List.of() : BellowsMediaRegistry.getFluidsFor(fanTypeId);
    }

    @Override
    public void draw(HandMadeBellowsRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);

        Font font = Minecraft.getInstance().font;
        String shownType = fitToWidth(font, fanTypeLabel(recipe),
                getBackground().getWidth() - LABEL_X - 4);
        graphics.drawString(font,
                Component.translatable("jei.create_hand_made.bellows_exclusive.type", shownType),
                LABEL_X, LABEL_Y, LABEL_COLOR, false);

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

    /** 收集所有 fan_type 属于第三方模组的 bellows_recipe。 */
    public static List<HandMadeBellowsRecipe> collectThirdPartyRecipes() {
        List<HandMadeBellowsRecipe> result = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (Recipe<?> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.get())) {
            if (!(r instanceof HandMadeBellowsRecipe recipe)) continue;
            ResourceLocation fanType = recipe.getFanType();
            if (fanType == null) continue;
            if ("create".equals(fanType.getNamespace())) continue;
            result.add(recipe);
        }
        return result;
    }
}