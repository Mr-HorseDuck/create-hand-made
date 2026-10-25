package com.alben.createhandmade.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zurrtum.create.content.processing.basin.BasinInput;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.HeatCondition;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.content.processing.recipe.SizedIngredient;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.foundation.recipe.TimedRecipe;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * basin 家族的 L3 独占配方（冲压锤 · 工作盆 / 搅拌杖）。
 *
 * <p>直接实现 Create 的 {@link BasinRecipe}，因此工作盆工具可以直接用
 * {@code BasinRecipe.tryCraft / prepareCraft / matchFluidIngredient} 那一套。</p>
 */
public record HandMadeToolRecipe(HandMadeTool tool, int time, List<ProcessingOutput> results,
                                 List<FluidStack> fluidResults, HeatCondition heat,
                                 List<FluidIngredient> fluidIngredients,
                                 List<SizedIngredient> ingredients)
        implements BasinRecipe, TimedRecipe, HandMadeToolRecipeLike {

    public static final MapCodec<HandMadeToolRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HandMadeTool.CODEC.fieldOf("tool").forGetter(HandMadeToolRecipe::tool),
            Codec.INT.optionalFieldOf("processing_time", 100).forGetter(HandMadeToolRecipe::time),
            ProcessingOutput.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(HandMadeToolRecipe::results),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_results", List.of()).forGetter(HandMadeToolRecipe::fluidResults),
            HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(HandMadeToolRecipe::heat),
            FluidIngredient.CODEC.listOf().optionalFieldOf("fluid_ingredients", List.of()).forGetter(HandMadeToolRecipe::fluidIngredients),
            SizedIngredient.LIST_CODEC.optionalFieldOf("ingredients", List.of()).forGetter(HandMadeToolRecipe::ingredients)
    ).apply(instance, HandMadeToolRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeToolRecipe> STREAM_CODEC = StreamCodec.composite(
            HandMadeTool.PACKET_CODEC, HandMadeToolRecipe::tool,
            ByteBufCodecs.INT, HandMadeToolRecipe::time,
            ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), HandMadeToolRecipe::results,
            FluidStack.PACKET_CODEC.apply(ByteBufCodecs.list()), HandMadeToolRecipe::fluidResults,
            HeatCondition.PACKET_CODEC, HandMadeToolRecipe::heat,
            FluidIngredient.PACKET_CODEC.apply(ByteBufCodecs.list()), HandMadeToolRecipe::fluidIngredients,
            SizedIngredient.PACKET_CODEC.apply(ByteBufCodecs.list()), HandMadeToolRecipe::ingredients,
            HandMadeToolRecipe::new);

    @Override
    public int getIngredientSize() {
        return fluidIngredients.size() + ingredients.size();
    }

    @Override
    public boolean matches(BasinInput input, Level world) {
        if (!heat.testBlazeBurner(input.heat())) {
            return false;
        }
        ServerFilteringBehaviour filter = input.filter();
        if (filter == null) {
            return false;
        }
        if (results.isEmpty()) {
            if (fluidResults.isEmpty() || !filter.test(fluidResults.getFirst())) {
                return false;
            }
        } else if (!filter.test(results.getFirst().create())) {
            return false;
        }
        List<ItemStack> outputs = BasinRecipe.tryCraft(input, ingredients);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.matchFluidIngredient(input, fluidIngredients)) {
            return false;
        }
        ProcessingOutput.rollOutput(input.random(), results, outputs::add);
        return input.acceptOutputs(outputs, fluidResults, true);
    }

    @Override
    public boolean apply(BasinInput input) {
        if (!heat.testBlazeBurner(input.heat())) {
            return false;
        }
        Deque<Runnable> changes = new ArrayDeque<>();
        List<ItemStack> outputs = BasinRecipe.prepareCraft(input, ingredients, changes);
        if (outputs == null) {
            return false;
        }
        if (!BasinRecipe.prepareFluidCraft(input, fluidIngredients, changes)) {
            return false;
        }
        ProcessingOutput.rollOutput(input.random(), results, outputs::add);
        if (!input.acceptOutputs(outputs, fluidResults, true)) {
            return false;
        }
        changes.forEach(Runnable::run);
        return input.acceptOutputs(outputs, fluidResults, false);
    }

    @Override
    public HandMadeTool getTool() {
        return tool;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeSerializer<? extends net.minecraft.world.item.crafting.Recipe<BasinInput>> getSerializer() {
        return (RecipeSerializer) HandMadeRecipeTypes.TOOL_RECIPE.getSerializer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public RecipeType<? extends net.minecraft.world.item.crafting.Recipe<BasinInput>> getType() {
        return (RecipeType) HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }
}
