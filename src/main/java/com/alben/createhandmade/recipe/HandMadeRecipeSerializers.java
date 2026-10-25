package com.alben.createhandmade.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * L3 独占配方的 serializer 工厂。
 *
 * <p>{@code create_hand_made:tool_recipe} 只有一个 serializer，但按 {@code tool} 字段
 * 分派到不同家族的配方 record；{@code stonecutting_recipe} 与 {@code bellows_recipe}
 * 各自独立。</p>
 */
public final class HandMadeRecipeSerializers {

    private HandMadeRecipeSerializers() {
    }

    /** 手工工具独占配方：按 {@code tool} 字段分派六个家族。 */
    public static RecipeSerializer<Recipe<?>> toolRecipe() {
        MapCodec<Recipe<?>> codec = HandMadeTool.CODEC.dispatchMap(
                "tool",
                HandMadeRecipeSerializers::toolOf,
                tool -> toolCodec(tool));

        StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> streamCodec = HandMadeTool.PACKET_CODEC.dispatch(
                HandMadeRecipeSerializers::toolOf,
                HandMadeRecipeSerializers::toolStreamCodec);

        return new RecipeSerializer<>(codec, streamCodec);
    }

    /** 手锯 · 切石独占配方。 */
    public static RecipeSerializer<HandMadeStonecuttingRecipe> stonecutting() {
        return new RecipeSerializer<>(HandMadeStonecuttingRecipe.MAP_CODEC, HandMadeStonecuttingRecipe.STREAM_CODEC);
    }

    /** 风箱独占配方。 */
    public static RecipeSerializer<HandMadeBellowsRecipe> bellows() {
        return new RecipeSerializer<>(HandMadeBellowsRecipe.MAP_CODEC, HandMadeBellowsRecipe.STREAM_CODEC);
    }

    private static HandMadeTool toolOf(Recipe<?> recipe) {
        if (recipe instanceof HandMadeToolRecipeLike like && like.getTool() != null) {
            return like.getTool();
        }
        throw new IllegalStateException("Not a create_hand_made tool recipe: " + recipe.getClass().getName());
    }

    private static MapCodec<? extends Recipe<?>> toolCodec(HandMadeTool tool) {
        return switch (tool) {
            case PRESS_HAMMER_BASIN, STIRRING_STAFF -> HandMadeToolRecipe.MAP_CODEC;
            case MORTAR, CRUSHER_MORTAR -> HandMadeCrushingRecipe.MAP_CODEC;
            case PRESS_HAMMER_DEPOT -> HandMadePressingRecipe.MAP_CODEC;
            case HAND_SAW -> HandMadeCuttingRecipe.MAP_CODEC;
            case INFUSION_GUN -> HandMadeFillingRecipe.MAP_CODEC;
            case POINTER -> HandMadeApplicationRecipe.MAP_CODEC;
            default -> throw new IllegalArgumentException("Tool has no exclusive recipe family: " + tool);
        };
    }

    private static StreamCodec<RegistryFriendlyByteBuf, ? extends Recipe<?>> toolStreamCodec(HandMadeTool tool) {
        return switch (tool) {
            case PRESS_HAMMER_BASIN, STIRRING_STAFF -> HandMadeToolRecipe.STREAM_CODEC;
            case MORTAR, CRUSHER_MORTAR -> HandMadeCrushingRecipe.STREAM_CODEC;
            case PRESS_HAMMER_DEPOT -> HandMadePressingRecipe.STREAM_CODEC;
            case HAND_SAW -> HandMadeCuttingRecipe.STREAM_CODEC;
            case INFUSION_GUN -> HandMadeFillingRecipe.STREAM_CODEC;
            case POINTER -> HandMadeApplicationRecipe.STREAM_CODEC;
            default -> throw new IllegalArgumentException("Tool has no exclusive recipe family: " + tool);
        };
    }
}
