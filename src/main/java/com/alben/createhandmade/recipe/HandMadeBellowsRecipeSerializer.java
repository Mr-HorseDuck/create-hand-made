package com.alben.createhandmade.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.stream.Stream;

/**
 * {@code create_hand_made:bellows_recipe} 的序列化器：在 Create 的
 * {@link StandardProcessingRecipe.Serializer} 之上多读/写一个<b>必填</b>的 {@code fan_type} 字段。
 *
 * <p>序列化逻辑本身一行都没写 —— Create 的 {@code StandardProcessingRecipe.Serializer} 是
 * 完全泛型的（只吃一个 {@code ProcessingRecipeParams -> R} 工厂，内部不含任何 RecipeType 检查，
 * 见 {@code StandardProcessingRecipe.java:45-69}），所以直接拿它当 delegate
 * （与 {@link HandMadeRecipeTypes} 的类注释说明完全同源）。这里只做两件事：
 * 解析并校验 {@code fan_type}、把它写进/读出配方对象。</p>
 *
 * <p><b>为什么独立成一个 serializer，而不是塞进 {@link HandMadeToolRecipeSerializer} 的
 * {@code Family} 分派：</b></p>
 * <ul>
 *   <li>{@code HandMadeToolRecipeSerializer} 的分派入口是 {@code familyOf(HandMadeTool)}
 *       （{@code HandMadeToolRecipeSerializer.java:108-118}），而风箱<b>不属于</b>
 *       {@code HandMadeTool} 体系（{@code HandMadeTool.java:13-15} 明确写了风箱不在枚举里、
 *       也不为它建 Pool 条目）。要塞进去就得给枚举加常量或让某个家族永远不被 {@code familyOf}
 *       返回，两种都很别扭；</li>
 *   <li>两者的字段集不同：{@code tool_recipe} 读必填 {@code tool} + 可选
 *       {@code keep_held_item} + 流体原料归一化，{@code bellows_recipe} 读必填
 *       {@code fan_type} 且<b>禁止流体</b>。混在一起会让 {@code supportedToolIds()}、
 *       {@code Missing required field 'tool'} 一类错误信息与校验逻辑互相干扰；</li>
 *   <li>代价很低：本类只有"读一个必填枚举字段"这点逻辑，泛型又刚好与
 *       {@link HandMadeBellowsRecipe} 一致（都是 {@code StandardProcessingRecipe<RecipeInput>}），
 *       不需要像 {@code tool_recipe} 那样为 6 个家族写 encode/streamCodec 的 switch。</li>
 * </ul>
 */
public class HandMadeBellowsRecipeSerializer implements RecipeSerializer<HandMadeBellowsRecipe> {

    private static final String FAN_TYPE_FIELD = "fan_type";

    /** Create 现成的泛型 serializer：负责除 {@code fan_type} 之外的全部字段（含 validate）。 */
    private static final StandardProcessingRecipe.Serializer<HandMadeBellowsRecipe> DELEGATE =
            new StandardProcessingRecipe.Serializer<>(HandMadeBellowsRecipe::new);

    @Override
    public MapCodec<HandMadeBellowsRecipe> codec() {
        return new MapCodec<>() {

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                // 让数据包校验 / 导出工具知道本 schema 还有 fan_type 这个键
                return Stream.concat(DELEGATE.codec().keys(ops),
                        Stream.of(ops.createString(FAN_TYPE_FIELD)));
            }

            @Override
            public <T> DataResult<HandMadeBellowsRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                // 1) 先读 fan_type：它是必填字段，缺失/非法时直接失败，不再往下解析
                DataResult<FanType> fanTypeResult = readFanType(ops, input);
                if (fanTypeResult.error().isPresent()) {
                    return DataResult.error(fanTypeResult.error().get()::message);
                }
                FanType fanType = fanTypeResult.getOrThrow();

                // 2) 其余字段全部交给 Create 的 delegate（它会跑 validate：输入/输出/流体上限、时长、加热）
                return DELEGATE.codec().decode(ops, input).map(recipe -> {
                    // 3) validate 通过后再写入 fan_type（与 tool_recipe 里 setTool 的时机同理）
                    recipe.setFanType(fanType);
                    return recipe;
                });
            }

            @Override
            public <T> RecordBuilder<T> encode(HandMadeBellowsRecipe recipe, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                RecordBuilder<T> builder = DELEGATE.codec().encode(recipe, ops, prefix);
                FanType fanType = recipe.getFanType();
                // 正常路径下 fanType 一定非 null（decode 时写入）；为 null 说明对象不是本 codec
                // 造出来的，此时不写该键（读取侧会按"缺失必填字段"报错，不会静默变成默认值）。
                if (fanType != null) {
                    builder.add(FAN_TYPE_FIELD, ops.createString(fanType.id()));
                }
                return builder;
            }
        };
    }

    /**
     * 读必填的 {@code fan_type} 字段并解析成 {@link FanType}。
     *
     * <p>三种失败各有明确原因：缺失 / 不是字符串 / 不是合法的 4 个取值之一。
     * 与 {@code tool_recipe} 的 {@code readTool}
     * （{@code HandMadeToolRecipeSerializer.java:273-292}）同构 —— 只解析、不记日志，
     * 错误交给 {@code DataResult} 由数据包加载器报给作者。</p>
     */
    private static <T> DataResult<FanType> readFanType(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(FAN_TYPE_FIELD);
        if (value == null) {
            return DataResult.error(() -> "Missing required field '" + FAN_TYPE_FIELD
                    + "' for " + HandMadeRecipeTypes.BELLOWS_RECIPE.getId());
        }

        DataResult<String> idResult = Codec.STRING.parse(ops, value);
        if (idResult.error().isPresent()) {
            return DataResult.error(() -> "Invalid '" + FAN_TYPE_FIELD + "' field: expected a string");
        }

        String id = idResult.getOrThrow();
        FanType fanType = FanType.byId(id);
        if (fanType == null) {
            return DataResult.error(() -> "Unknown fan_type: '" + id + "'. Valid values: "
                    + FanType.validIds());
        }
        return DataResult.success(fanType);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, HandMadeBellowsRecipe> streamCodec() {
        return StreamCodec.of(
                (buf, recipe) -> {
                    // fan_type 先写：读取侧要先拿到它才能构造出完整对象
                    FanType fanType = recipe.getFanType();
                    buf.writeEnum(fanType == null ? FanType.BLASTING : fanType);
                    DELEGATE.streamCodec().encode(buf, recipe);
                },
                buf -> {
                    FanType fanType = buf.readEnum(FanType.class);
                    HandMadeBellowsRecipe recipe = DELEGATE.streamCodec().decode(buf);
                    recipe.setFanType(fanType);
                    return recipe;
                });
    }
}
