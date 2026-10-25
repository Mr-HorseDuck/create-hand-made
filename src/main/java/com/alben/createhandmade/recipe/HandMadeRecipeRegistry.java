package com.alben.createhandmade.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * 「独占配方」追加钩子（当前为空壳）。
 */
public final class HandMadeRecipeRegistry {

    private HandMadeRecipeRegistry() {
    }

    public static List<RecipeHolder<?>> appendExclusive(HandMadeTool tool, List<RecipeHolder<?>> recipes) {
        return recipes;
    }
}
