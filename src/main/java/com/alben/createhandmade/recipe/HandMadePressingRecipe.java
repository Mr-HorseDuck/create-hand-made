package com.alben.createhandmade.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** 冲压家族的 L3 独占配方（冲压锤 · 置物台 / 传送带）。 */
public record HandMadePressingRecipe(HandMadeTool tool, List<ProcessingOutput> results, Ingredient ingredient)
        implements CreateSingleStackRollableRecipe, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadePressingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HandMadeTool.CODEC.fieldOf("tool").forGetter(HandMadePressingRecipe::tool),
            ProcessingOutput.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(HandMadePressingRecipe::results),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HandMadePressingRecipe::ingredient)
    ).apply(instance, HandMadePressingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadePressingRecipe> STREAM_CODEC = StreamCodec.composite(
            HandMadeTool.PACKET_CODEC, HandMadePressingRecipe::tool,
            ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), HandMadePressingRecipe::results,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadePressingRecipe::ingredient,
            HandMadePressingRecipe::new);

    @Override
    public HandMadeTool getTool() {
        return tool;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.TOOL_RECIPE.getSerializer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>> getType() {
        return (RecipeType) HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }
}
