package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public class BellowsCookingCategory extends CreateRecipeCategory<AbstractCookingRecipe> {

    private final Supplier<List<ItemStack>> mediaSupplier;

    public BellowsCookingCategory(Info<AbstractCookingRecipe> info,
                                  Supplier<List<ItemStack>> mediaSupplier) {
        super(info);
        this.mediaSupplier = mediaSupplier;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AbstractCookingRecipe recipe, IFocusGroup focuses) {
        // 输入槽
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // ★ 介质催化剂槽（输入槽上方）
        List<ItemStack> media = mediaSupplier.get();
        if (!media.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 27, 5)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addItemStacks(media);
        }

        // 输出槽
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 51)
                .setBackground(getRenderedSlot(), -1, -1)
                .addItemStack(recipe.getResultItem(level.registryAccess()));
    }

    @Override
    public void draw(AbstractCookingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);

        // 风箱图标 + 抖动
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
}