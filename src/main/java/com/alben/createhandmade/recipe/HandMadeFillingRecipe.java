package com.alben.createhandmade.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.content.fluids.transfer.FillingInput;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.foundation.recipe.CreateRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** 注液家族的 L3 独占配方（灌注枪）。 */
public record HandMadeFillingRecipe(HandMadeTool tool, ItemStackTemplate result, Ingredient ingredient,
                                    FluidIngredient fluidIngredient)
        implements CreateRecipe<FillingInput>, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadeFillingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HandMadeTool.CODEC.fieldOf("tool").forGetter(HandMadeFillingRecipe::tool),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(HandMadeFillingRecipe::result),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HandMadeFillingRecipe::ingredient),
            FluidIngredient.CODEC.fieldOf("fluid_ingredient").forGetter(HandMadeFillingRecipe::fluidIngredient)
    ).apply(instance, HandMadeFillingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeFillingRecipe> STREAM_CODEC = StreamCodec.composite(
            HandMadeTool.PACKET_CODEC, HandMadeFillingRecipe::tool,
            ItemStackTemplate.STREAM_CODEC, HandMadeFillingRecipe::result,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadeFillingRecipe::ingredient,
            FluidIngredient.PACKET_CODEC, HandMadeFillingRecipe::fluidIngredient,
            HandMadeFillingRecipe::new);

    @Override
    public boolean matches(FillingInput input, Level world) {
        return ingredient.test(input.item()) && fluidIngredient.test(input.fluid());
    }

    @Override
    public ItemStack assemble(FillingInput input) {
        ItemStack junk = CreateRecipe.getJunk(input.item());
        if (junk != null) {
            return junk;
        }
        return result.create();
    }

    @Override
    public HandMadeTool getTool() {
        return tool;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends net.minecraft.world.item.crafting.Recipe<FillingInput>> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.TOOL_RECIPE.getSerializer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends net.minecraft.world.item.crafting.Recipe<FillingInput>> getType() {
        return (RecipeType) HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }
}
