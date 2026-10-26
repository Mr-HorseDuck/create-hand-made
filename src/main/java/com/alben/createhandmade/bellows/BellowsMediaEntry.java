package com.alben.createhandmade.bellows;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/**
 * 一条 {@code data/<ns>/bellows_media/*.json} 条目。
 *
 * <p>四个字段都可以单独使用，展开后合并生效：</p>
 * <ul>
 *   <li>{@code type}：鼓风类型 id，必填，必须是带命名空间的完整 id</li>
 *   <li>{@code ingredients}：可选，原料列表（物品/标签）</li>
 *   <li>{@code catalysts}：可选，单个标签字符串（{@code "#ns:path"}），展开时扫 block/item/fluid 三个注册表</li>
 *   <li>{@code fluids}：可选，流体 id 列表，用于无桶流体的显式声明</li>
 *   <li>{@code priority}：可选，覆盖该类型的优先级</li>
 * </ul>
 */
public record BellowsMediaEntry(ResourceLocation processingTypeId,
                                List<Ingredient> ingredients,
                                Optional<String> catalysts,
                                List<ResourceLocation> fluids,
                                Optional<Integer> priority) {

    /**
     * 1.20.1 的 Ingredient 没有内置 Codec，手动写一个基于 JSON 的桥接。
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
            INGREDIENT_CODEC.listOf()
                    .optionalFieldOf("ingredients", List.of())
                    .forGetter(BellowsMediaEntry::ingredients),
            Codec.STRING.optionalFieldOf("catalysts").forGetter(BellowsMediaEntry::catalysts),
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("fluids", List.of())
                    .forGetter(BellowsMediaEntry::fluids),
            Codec.INT.optionalFieldOf("priority").forGetter(BellowsMediaEntry::priority)
    ).apply(i, BellowsMediaEntry::new));
}