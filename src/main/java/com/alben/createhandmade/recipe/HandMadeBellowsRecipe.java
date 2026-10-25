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

/** 风箱的 L3 独占配方（{@code fan_type} + 单品输入，瞬发）。 */
public record HandMadeBellowsRecipe(FanType fanType, List<ProcessingOutput> results, Ingredient ingredient)
        implements CreateSingleStackRollableRecipe, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadeBellowsRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FanType.CODEC.fieldOf("fan_type").forGetter(HandMadeBellowsRecipe::fanType),
            ProcessingOutput.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(HandMadeBellowsRecipe::results),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HandMadeBellowsRecipe::ingredient)
    ).apply(instance, HandMadeBellowsRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeBellowsRecipe> STREAM_CODEC = StreamCodec.composite(
            FanType.PACKET_CODEC, HandMadeBellowsRecipe::fanType,
            ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), HandMadeBellowsRecipe::results,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadeBellowsRecipe::ingredient,
            HandMadeBellowsRecipe::new);

    @Override
    public HandMadeTool getTool() {
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.BELLOWS_RECIPE.getSerializer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>> getType() {
        return (RecipeType) HandMadeRecipeTypes.BELLOWS_RECIPE.getType();
    }
}
