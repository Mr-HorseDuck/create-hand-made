package com.alben.createhandmade.recipe;

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
 * 1.20.1 用 Gson（fromJson / fromNetwork / toNetwork）。
 */
public class HandMadeBellowsRecipeSerializer
        implements RecipeSerializer<HandMadeBellowsRecipe> {

    private static final String FAN_TYPE_FIELD = "fan_type";

    private static final ProcessingRecipeSerializer<HandMadeBellowsRecipe> DELEGATE =
            new ProcessingRecipeSerializer<>(HandMadeBellowsRecipe::new);

    @Override
    public HandMadeBellowsRecipe fromJson(ResourceLocation id, JsonObject json) {
        // 1) 先读 fan_type：必填
        String fanTypeId = GsonHelper.getAsString(json, FAN_TYPE_FIELD);
        FanType fanType = FanType.byId(fanTypeId);
        if (fanType == null) {
            throw new IllegalArgumentException(
                    "Unknown fan_type: '" + fanTypeId + "'. Valid values: " + FanType.validIds());
        }

        // 2) 其余字段委托给 Create
        HandMadeBellowsRecipe recipe = DELEGATE.fromJson(id, json);

        // 3) 写入 fan_type
        recipe.setFanType(fanType);
        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, HandMadeBellowsRecipe recipe) {
        FanType fanType = recipe.getFanType();
        buf.writeEnum(fanType != null ? fanType : FanType.BLASTING);
        DELEGATE.toNetwork(buf, recipe);
    }

    @Override
    public HandMadeBellowsRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        FanType fanType = buf.readEnum(FanType.class);
        HandMadeBellowsRecipe recipe = DELEGATE.fromNetwork(id, buf);
        recipe.setFanType(fanType);
        return recipe;
    }
}