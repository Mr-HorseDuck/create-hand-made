package com.alben.createhandmade.bellows;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/**
 * 一条 {@code data/<ns>/bellows_media/*.json} 条目：把一批"介质"绑定到某个鼓风类型。
 *
 * <h3>三个字段都可以单独使用（T11 B3 / B4）</h3>
 * <ul>
 *   <li>{@code ingredients}（可选）：原料列表，写法与配方一致（{@code {"item": ...}} / {@code {"tag": ...}}）。</li>
 *   <li>{@code catalysts}（可选，<b>T11 B3 新增</b>）：<b>一个标签</b>，字符串形如
 *       {@code "#create:fan_processing_catalysts/blasting"} 或 {@code "#minecraft:sand"}。
 *       没有 {@code Ingredients} 的等价语法能覆盖"方块标签"（例如 DnD / CMC / Processing 的触媒都是
 *       <b>block tag</b>，不是 item tag），所以这里单独开一个字段，展开时同时扫
 *       block / item / fluid 三个注册表的同名标签（详见 {@code BellowsMediaRegistry#itemsInTag}）。</li>
 *   <li>{@code fluids}（可选，<b>T11 B8 新增</b>）：流体 id 列表。介质系统本质上只认"物品"，
 *       而有些流体<b>没有桶物品</b>（例如龙+ 的染料液体，{@code fluid.getBucket()} 返回 AIR），
 *       玩家手里拿不出来。这个字段让数据包<b>显式声明</b>某个流体算哪种鼓风的触媒：
 *       有桶就顺手把桶也绑成介质；没桶则只登记在案（用于 JEI 展示与诊断），不会被静默丢弃。</li>
 *   <li>{@code priority}（可选，<b>T11 B4 新增</b>）：显式覆盖该类型的优先级。
 *       不写时用类型自己的 {@code getPriority()}；写了就<b>完全无视</b>原版值，
 *       用于把优先级虚高的模组（例如 CMC 的 2000）压下去、或把被饿死的类型顶上来。</li>
 * </ul>
 * <p>四者可以同时出现，展开后<b>合并生效</b>；也允许只写其中任意一个。</p>
 *
 * <h3>典型用法</h3>
 * <pre>
 * // 第三方兼容条目：只有类型 + 触媒标签
 * {"type": "dndesires:sanding", "catalysts": "#dndesires:fan_processing_catalysts/sanding"}
 *
 * // 优先级覆盖条目（T11 B4）：不需要任何介质，只改优先级
 * {"type": "dndesires:freezing", "priority": 9999}
 *
 * // 无桶流体的显式声明（T11 B8）
 * {"type": "create_dragons_plus:coloring", "fluids": ["create_dragons_plus:red_dye"]}
 *
 * // 合并：既接管触媒，又把它顶到最高
 * {"type": "dndesires:freezing", "catalysts": "#dndesires:fan_processing_catalysts/freezing", "priority": 9999}
 * </pre>
 */
public record BellowsMediaEntry(ResourceLocation processingTypeId,
                                List<Ingredient> ingredients,
                                Optional<String> catalysts,
                                List<ResourceLocation> fluids,
                                Optional<Integer> priority) {

    public static final Codec<BellowsMediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(BellowsMediaEntry::processingTypeId),
            Ingredient.CODEC.listOf()
                    .optionalFieldOf("ingredients", List.of())
                    .forGetter(BellowsMediaEntry::ingredients),
            Codec.STRING.optionalFieldOf("catalysts").forGetter(BellowsMediaEntry::catalysts),
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("fluids", List.of())
                    .forGetter(BellowsMediaEntry::fluids),
            Codec.INT.optionalFieldOf("priority").forGetter(BellowsMediaEntry::priority)
    ).apply(i, BellowsMediaEntry::new));
}
