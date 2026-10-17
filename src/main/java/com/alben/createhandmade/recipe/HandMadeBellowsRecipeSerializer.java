package com.alben.createhandmade.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.stream.Stream;

/**
 * {@code create_hand_made:bellows_recipe} 的序列化器：在 Create 的
 * {@link StandardProcessingRecipe.Serializer} 之上多读/写一个<b>必填</b>的 {@code fan_type} 字段。
 *
 * <p>序列化逻辑本身一行都没写 —— Create 的 {@code StandardProcessingRecipe.Serializer} 是
 * 完全泛型的（只吃一个 {@code ProcessingRecipeParams -> R} 工厂），所以直接拿它当 delegate。
 * 这里只做两件事：解析并校验 {@code fan_type}、把它写进/读出配方对象。</p>
 *
 * <h3>T11 批次 1：{@code fan_type} 从 4 值枚举改成类型 id</h3>
 * <ul>
 *   <li><b>格式</b>：必须是带命名空间的完整 id，例如 {@code "create:blasting"}；
 *       旧的裸名 {@code "blasting"} 现在会被判为非法（{@link ResourceLocation#tryParse} 失败）。</li>
 *   <li><b>校验</b>：{@code id} 必须能在 {@code CreateBuiltInRegistries.FAN_PROCESSING_TYPE}
 *       里找到 —— 也就是"任何注册进 Create 注册表的鼓风类型"（含第三方模组新增的类型）都合法，
 *       不再局限于本模组枚举里的 4 个。</li>
 *   <li><b>网络同步</b>：由 {@code writeEnum/readEnum} 改为
 *       {@code writeResourceLocation/readResourceLocation}。</li>
 * </ul>
 */
public class HandMadeBellowsRecipeSerializer implements RecipeSerializer<HandMadeBellowsRecipe> {

    private static final String FAN_TYPE_FIELD = "fan_type";

    /** 网络同步遇到 null 时的兜底 id（正常路径不会用到：decode 成功即非 null）。 */
    private static final ResourceLocation FALLBACK_ID = ResourceLocation.fromNamespaceAndPath("create", "blasting");

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
                DataResult<ResourceLocation> fanTypeResult = readFanTypeId(ops, input);
                if (fanTypeResult.error().isPresent()) {
                    return DataResult.error(fanTypeResult.error().get()::message);
                }
                ResourceLocation fanTypeId = fanTypeResult.getOrThrow();

                // 2) 其余字段全部交给 Create 的 delegate（它会跑 validate：输入/输出/流体上限、时长、加热）
                return DELEGATE.codec().decode(ops, input).map(recipe -> {
                    // 3) validate 通过后再写入 fan_type
                    recipe.setFanTypeId(fanTypeId);
                    return recipe;
                });
            }

            @Override
            public <T> RecordBuilder<T> encode(HandMadeBellowsRecipe recipe, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                RecordBuilder<T> builder = DELEGATE.codec().encode(recipe, ops, prefix);
                ResourceLocation fanTypeId = recipe.getFanTypeId();
                // 正常路径下 fanTypeId 一定非 null（decode 时写入）；为 null 说明对象不是本 codec
                // 造出来的，此时不写该键（读取侧会按"缺失必填字段"报错，不会静默变成默认值）。
                if (fanTypeId != null) {
                    builder.add(FAN_TYPE_FIELD, ops.createString(fanTypeId.toString()));
                }
                return builder;
            }
        };
    }

    /**
     * 读必填的 {@code fan_type} 字段，解析成 {@link ResourceLocation} 并校验它在
     * {@code FAN_PROCESSING_TYPE} 注册表里存在。
     *
     * <p>四种失败各有明确原因：缺失 / 不是字符串 / 不是合法 id（缺命名空间等）/
     * 注册表里没有该类型。与 {@code tool_recipe} 的 {@code readTool} 同构 ——
     * 只解析、不记日志，错误交给 {@code DataResult} 由数据包加载器报给作者。</p>
     */
    private static <T> DataResult<ResourceLocation> readFanTypeId(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(FAN_TYPE_FIELD);
        if (value == null) {
            return DataResult.error(() -> "Missing required field '" + FAN_TYPE_FIELD
                    + "' for " + HandMadeRecipeTypes.BELLOWS_RECIPE.getId());
        }

        DataResult<String> idResult = Codec.STRING.parse(ops, value);
        if (idResult.error().isPresent()) {
            return DataResult.error(() -> "Invalid '" + FAN_TYPE_FIELD
                    + "' field: expected a string like 'create:blasting'");
        }

        String raw = idResult.getOrThrow();
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            return DataResult.error(() -> "Invalid '" + FAN_TYPE_FIELD + "': '" + raw
                    + "' is not a valid resource location (use 'namespace:path', e.g. 'create:blasting')");
        }

        FanProcessingType type = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id);
        if (type == null) {
            // 注意：ResourceLocation.tryParse 会把裸名补成 minecraft:<name>（例如 "blasting" → "minecraft:blasting"），
            // 所以这里把原始写法也带上，方便作者看出"忘了命名空间"这种错。
            return DataResult.error(() -> "Unknown fan processing type: '" + id + "' (from '" + raw
                    + "'). Use a full id like 'create:blasting'; it must be registered in Create's"
                    + " fan processing type registry");
        }
        return DataResult.success(id);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, HandMadeBellowsRecipe> streamCodec() {
        return StreamCodec.of(
                (buf, recipe) -> {
                    // fan_type 先写：读取侧要先拿到它才能构造出完整对象
                    ResourceLocation fanTypeId = recipe.getFanTypeId();
                    buf.writeResourceLocation(fanTypeId == null ? FALLBACK_ID : fanTypeId);
                    DELEGATE.streamCodec().encode(buf, recipe);
                },
                buf -> {
                    ResourceLocation fanTypeId = buf.readResourceLocation();
                    HandMadeBellowsRecipe recipe = DELEGATE.streamCodec().decode(buf);
                    recipe.setFanTypeId(fanTypeId);
                    return recipe;
                });
    }
}
