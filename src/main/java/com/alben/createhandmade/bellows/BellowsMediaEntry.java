package com.alben.createhandmade.bellows;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 风箱介质数据文件（{@code data/<ns>/bellows_media/*.json}）。
 *
 * <p>{@code ingredients} 里每项是一个「选择器」字符串，与 26.1 的配方物品格式一致：</p>
 * <ul>
 *   <li>{@code "minecraft:lava_bucket"} —— 单个物品</li>
 *   <li>{@code "#c:buckets/lava"} —— 物品标签</li>
 * </ul>
 *
 * <p>刻意**不**直接用 {@code Ingredient.CODEC}：数据包重载走的是无注册表的
 * {@code JsonOps}，而 {@code Ingredient.CODEC} 内部的 {@code HolderSetCodec}
 * 需要 registry 才能把 id 解析成 {@code Holder}。这里先存原始字符串，
 * 真正匹配时再去 {@code BuiltInRegistries} 查，既避开该限制，也让标签在
 * 标签重载之后才被解析。</p>
 */
public record BellowsMediaEntry(Identifier processingTypeId, List<String> ingredients) {

    public static final Codec<BellowsMediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("type").forGetter(BellowsMediaEntry::processingTypeId),
            Codec.STRING.listOf().fieldOf("ingredients").forGetter(BellowsMediaEntry::ingredients)
    ).apply(i, BellowsMediaEntry::new));
}
