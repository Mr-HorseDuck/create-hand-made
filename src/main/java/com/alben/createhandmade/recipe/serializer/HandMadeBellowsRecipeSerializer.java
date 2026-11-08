package com.alben.createhandmade.recipe.serializer;

import com.alben.createhandmade.recipe.type.HandMadeRecipes;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * create_hand_made:bellows_recipe 的 serializer。
 *
 * 在 Create 的 ProcessingRecipeSerializer 之上多读/写一个必填的 fan_type 字段。
 * fan_type 用完整 ResourceLocation（如 create:blasting、dndesires:sanding），
 * 支持任意第三方鼓风类型。旧短名（如 blasting）不再接受。
 */
public class HandMadeBellowsRecipeSerializer
        implements RecipeSerializer<HandMadeRecipes.Bellows> {

    private static final String FAN_TYPE_FIELD = "fan_type";

    private static final ProcessingRecipeSerializer<HandMadeRecipes.Bellows> DELEGATE =
            new ProcessingRecipeSerializer<>(HandMadeRecipes.Bellows::new);

    @Override
    public HandMadeRecipes.Bellows fromJson(ResourceLocation id, JsonObject json) {
        // 1) 先读 fan_type：必填，必须是完整 ResourceLocation
        String fanTypeStr = GsonHelper.getAsString(json, FAN_TYPE_FIELD);
        ResourceLocation fanType;
        try {
            fanType = new ResourceLocation(fanTypeStr);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid fan_type '" + fanTypeStr + "' in " + id
                            + ". Must be a full resource location (e.g. create:blasting).", e);
        }

        // 2) 其余字段委托给 Create
        HandMadeRecipes.Bellows recipe = DELEGATE.fromJson(id, json);

        // 3) 写入 fan_type
        recipe.setFanType(fanType);
        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, HandMadeRecipes.Bellows recipe) {
        ResourceLocation fanType = recipe.getFanType();
        buf.writeBoolean(fanType != null);
        if (fanType != null) {
            buf.writeResourceLocation(fanType);
        }
        DELEGATE.toNetwork(buf, recipe);
    }

    @Override
    public HandMadeRecipes.Bellows fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        ResourceLocation fanType = buf.readBoolean() ? buf.readResourceLocation() : null;
        HandMadeRecipes.Bellows recipe = DELEGATE.fromNetwork(id, buf);
        recipe.setFanType(fanType);
        return recipe;
    }
}