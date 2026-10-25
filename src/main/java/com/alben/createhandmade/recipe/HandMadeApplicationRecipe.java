package com.alben.createhandmade.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** 应用家族的 L3 独占配方（指杆）。 */
public record HandMadeApplicationRecipe(HandMadeTool tool, List<ProcessingOutput> results, boolean keepHeldItem,
                                        Ingredient target, Ingredient ingredient)
        implements ItemApplicationRecipe, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadeApplicationRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HandMadeTool.CODEC.fieldOf("tool").forGetter(HandMadeApplicationRecipe::tool),
            ProcessingOutput.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(HandMadeApplicationRecipe::results),
            Codec.BOOL.optionalFieldOf("keep_held_item", false).forGetter(HandMadeApplicationRecipe::keepHeldItem),
            Ingredient.CODEC.fieldOf("target").forGetter(HandMadeApplicationRecipe::target),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HandMadeApplicationRecipe::ingredient)
    ).apply(instance, HandMadeApplicationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeApplicationRecipe> STREAM_CODEC = StreamCodec.composite(
            HandMadeTool.PACKET_CODEC, HandMadeApplicationRecipe::tool,
            ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), HandMadeApplicationRecipe::results,
            ByteBufCodecs.BOOL, HandMadeApplicationRecipe::keepHeldItem,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadeApplicationRecipe::target,
            Ingredient.CONTENTS_STREAM_CODEC, HandMadeApplicationRecipe::ingredient,
            HandMadeApplicationRecipe::new);

    @Override
    public HandMadeTool getTool() {
        return tool;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends net.minecraft.world.item.crafting.Recipe<com.zurrtum.create.content.kinetics.deployer.ItemApplicationInput>> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.TOOL_RECIPE.getSerializer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends net.minecraft.world.item.crafting.Recipe<com.zurrtum.create.content.kinetics.deployer.ItemApplicationInput>> getType() {
        return (RecipeType) HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }
}
