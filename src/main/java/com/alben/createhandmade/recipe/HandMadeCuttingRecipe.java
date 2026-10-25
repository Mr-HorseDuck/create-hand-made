package com.alben.createhandmade.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import com.zurrtum.create.foundation.recipe.TimedRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** 切削家族的 L3 独占配方（手锯）。 */
public record HandMadeCuttingRecipe(HandMadeTool tool, int time, List<ProcessingOutput> results,
                                    Ingredient ingredient)
        implements CreateSingleStackRollableRecipe, TimedRecipe, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadeCuttingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HandMadeTool.CODEC.fieldOf("tool").forGetter(HandMadeCuttingRecipe::tool),
            Codec.INT.optionalFieldOf("processing_time", 100).forGetter(HandMadeCuttingRecipe::time),
            ProcessingOutput.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(HandMadeCuttingRecipe::results),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HandMadeCuttingRecipe::ingredient)
    ).apply(instance, HandMadeCuttingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeCuttingRecipe> STREAM_CODEC = StreamCodec.composite(
            HandMadeTool.PACKET_CODEC, HandMadeCuttingRecipe::tool,
            ByteBufCodecs.INT, HandMadeCuttingRecipe::time,
            ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), HandMadeCuttingRecipe::results,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadeCuttingRecipe::ingredient,
            HandMadeCuttingRecipe::new);

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
