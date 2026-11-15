package com.alben.createhandmade.recipe.serializer;

import com.alben.createhandmade.recipe.HandbookCraftRecipe;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * {@link HandbookCraftRecipe} 的序列化器。
 *
 * <p>JSON 解析直接委托原版 {@link ShapelessRecipe.Serializer}（材料表、分组、分类的解析与校验都白拿），
 * 只额外读一次 {@code result}——父类的 result 是包私有字段，取不到。</p>
 */
public class HandbookCraftRecipeSerializer implements RecipeSerializer<HandbookCraftRecipe> {

    private static final ShapelessRecipe.Serializer VANILLA = new ShapelessRecipe.Serializer();

    @Override
    public HandbookCraftRecipe fromJson(ResourceLocation id, JsonObject json) {
        ShapelessRecipe base = VANILLA.fromJson(id, json);
        ItemStack result = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
        return new HandbookCraftRecipe(id, base.getGroup(), base.category(), result, base.getIngredients());
    }

    @Override
    public HandbookCraftRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);

        int size = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
        for (int i = 0; i < size; i++) {
            ingredients.set(i, Ingredient.fromNetwork(buf));
        }

        ItemStack result = buf.readItem();
        return new HandbookCraftRecipe(id, group, category, result, ingredients);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, HandbookCraftRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());

        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredient.toNetwork(buf);
        }

        buf.writeItem(recipe.resultStack());
    }
}
