package com.alben.createhandmade.bellows;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public record BellowsMediaEntry(ResourceLocation processingTypeId, List<Ingredient> ingredients) {

    /**
     * ★ 1.20.1 的 Ingredient 没有内置 Codec，手动写一个基于 JSON 的桥接。
     *   1.20.1 的 Ingredient 提供 fromJson(JsonElement) / toJson() 方法。
     */
    public static final Codec<Ingredient> INGREDIENT_CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Ingredient, T>> decode(DynamicOps<T> ops, T input) {
            JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
            try {
                Ingredient ing = Ingredient.fromJson(json);
                return DataResult.success(Pair.of(ing, input));
            } catch (Exception e) {
                return DataResult.error(() -> "Failed to parse ingredient: " + e.getMessage());
            }
        }

        @Override
        public <T> DataResult<T> encode(Ingredient input, DynamicOps<T> ops, T prefix) {
            JsonElement json = input.toJson();
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
        }
    };

    public static final Codec<BellowsMediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(BellowsMediaEntry::processingTypeId),
            INGREDIENT_CODEC.listOf().fieldOf("ingredients").forGetter(BellowsMediaEntry::ingredients)
    ).apply(i, BellowsMediaEntry::new));
}