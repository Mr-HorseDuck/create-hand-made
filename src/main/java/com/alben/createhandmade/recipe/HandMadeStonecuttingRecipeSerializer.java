package com.alben.createhandmade.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * create_hand_made:stonecutting_recipe 的 serializer。
 *
 * 不继承 SingleItemRecipe.Serializer —— 它的 Factory 期望 3 参数
 * (String, Ingredient, ItemStack)，而 1.20.1 的 SingleItemRecipe 构造
 * 需要 ResourceLocation 作为 id。自己写实现直接传 id。
 *
 * JSON 结构与原版 minecraft:stonecutting 一致：
 * { "group": "...", "ingredient": {...}, "result": { "item": "...", "count": 1 } }
 */
public class HandMadeStonecuttingRecipeSerializer
        implements RecipeSerializer<HandMadeStonecuttingRecipe> {

    @Override
    public HandMadeStonecuttingRecipe fromJson(ResourceLocation id, JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        Ingredient ingredient = Ingredient.fromJson(
                GsonHelper.getAsJsonObject(json, "ingredient"));
        ItemStack result = ShapedRecipe.itemStackFromJson(
                GsonHelper.getAsJsonObject(json, "result"));
        return new HandMadeStonecuttingRecipe(id, group, ingredient, result);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, HandMadeStonecuttingRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        recipe.getIngredients().get(0).toNetwork(buf);
        buf.writeItem(recipe.getResultItem(null));
    }

    @Override
    public HandMadeStonecuttingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        ItemStack result = buf.readItem();
        return new HandMadeStonecuttingRecipe(id, group, ingredient, result);
    }
}