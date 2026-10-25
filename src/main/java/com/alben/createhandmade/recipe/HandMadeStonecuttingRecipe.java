package com.alben.createhandmade.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;

/**
 * 「手锯 · 切石」的 L3 独占配方类。
 *
 * <p>挂在独立的自定义 type {@code create_hand_made:stonecutting_recipe} 下：原版切石机与
 * Create 的机械锯查的是 {@code minecraft:stonecutting}，因此永远看不到这些配方。</p>
 */
public class HandMadeStonecuttingRecipe extends SingleItemRecipe {

    public static final com.mojang.serialization.MapCodec<HandMadeStonecuttingRecipe> MAP_CODEC =
            SingleItemRecipe.simpleMapCodec(HandMadeStonecuttingRecipe::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeStonecuttingRecipe> STREAM_CODEC =
            SingleItemRecipe.simpleStreamCodec(HandMadeStonecuttingRecipe::new);

    public HandMadeStonecuttingRecipe(Recipe.CommonInfo commonInfo, Ingredient input, ItemStackTemplate result) {
        super(commonInfo, input, result);
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends SingleItemRecipe> getType() {
        return (RecipeType) HandMadeRecipeTypes.STONECUTTING_RECIPE.getType();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends SingleItemRecipe> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.STONECUTTING_RECIPE.getSerializer();
    }
}
