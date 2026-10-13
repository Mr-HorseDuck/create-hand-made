package com.alben.createhandmade.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.Level;

public class HandMadeStonecuttingRecipe extends SingleItemRecipe {

    /**
     * 1.20.1 的 SingleItemRecipe 构造需要 ResourceLocation 作为第三个参数。
     */
    public HandMadeStonecuttingRecipe(ResourceLocation id, String group,
                                       Ingredient ingredient, ItemStack result) {
        super(HandMadeRecipeTypes.STONECUTTING_RECIPE.get(),
                HandMadeRecipeTypes.STONECUTTING_SERIALIZER.get(),
                id, group, ingredient, result);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return !container.isEmpty() && this.ingredient.test(container.getItem(0));
    }
}