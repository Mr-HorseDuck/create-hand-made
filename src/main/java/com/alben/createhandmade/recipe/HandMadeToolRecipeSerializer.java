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
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 独占配方的 serializer：按 {@code tool} 字段把解析<b>分派</b>到对应家族的配方类。
 *
 * <p>独占配方只有一个 RecipeType（{@code create_hand_made:tool_recipe}）和一个 serializer id，
 * 但按"家族"分成不同的配方类：</p>
 * <ul>
 *   <li><b>basin 家族</b>（{@code press_hammer_basin} / {@code stirring_staff}）→
 *       {@link HandMadeToolRecipe}，继承 Create 的 {@code BasinRecipe}，
 *       因为只有它提供了接受自定义 {@code IRecipeTypeInfo} 的 protected 构造；</li>
 *   <li><b>碾磨家族</b>（{@code mortar} / {@code crusher_mortar}）→
 *       {@link HandMadeCrushingRecipe}，继承 Create 的 {@code AbstractCrushingRecipe}
 *       （{@code MillingRecipe} / {@code CrushingRecipe} 的公共父类，同样有接受
 *       {@code IRecipeTypeInfo} 的 public 构造）。</li>
 * </ul>
 *
 * <p>两个家族的公共父类只有 Create 的
 * {@code StandardProcessingRecipe<RecipeInput>}（{@code BasinRecipe.java:37}、
 * {@code AbstractCrushingRecipe.java:9}），所以本 serializer 就以它为返回类型，
 * 并通过 {@link HandMadeToolRecipeLike} 读写 {@code tool} 归属。</p>
 *
 * <p><b>分派顺序（T5c 批次 1 的关键改动）：</b>{@code tool} 必须在委托 decode
 * <b>之前</b>读出来 —— 它决定用哪个家族的工厂构造对象。旧实现（T5b-1）是
 * "先委托 decode 再补读 tool"，那只适用于单一配方类。</p>
 *
 * <p><b>tool 的写入时机：</b>{@code ProcessingRecipe.codec} 内部带
 * {@code validate(recipe -> recipe.validate())}，而 validate 会调用
 * {@code getMaxOutputCount()} 等上限方法。本批次没有让上限依赖 tool
 * （见 {@link HandMadeCrushingRecipe#MAX_OUTPUT_COUNT}），所以 decode 之后再
 * {@code setTool} 是安全的。</p>
 */
public class HandMadeToolRecipeSerializer implements RecipeSerializer<StandardProcessingRecipe<RecipeInput>> {

    private static final String TOOL_FIELD = "tool";
    private static final String INGREDIENTS_FIELD = "ingredients";
    private static final String TYPE_FIELD = "type";
    private static final String FLUID_FIELD = "fluid";

    /** NeoForge 的「单一流体」原料类型 id，见 {@link #normalizeFluidIngredients}。 */
    private static final String SINGLE_FLUID_TYPE = "neoforge:single";

    /** 独占配方的家族：决定用哪个配方类 / 哪个 delegate。 */
    private enum Family {
        BASIN,
        CRUSHING
    }

    private static final StandardProcessingRecipe.Serializer<HandMadeToolRecipe> BASIN_DELEGATE =
            new StandardProcessingRecipe.Serializer<>(HandMadeToolRecipe::new);

    private static final StandardProcessingRecipe.Serializer<HandMadeCrushingRecipe> CRUSHING_DELEGATE =
            new StandardProcessingRecipe.Serializer<>(HandMadeCrushingRecipe::new);

    /**
     * {@code tool} → 家族；返回 null 表示该工具还不支持独占配方。
     *
     * <p>新增家族时只要在这里加一个分支（外加对应的配方类与 delegate），
     * 错误信息里的"已支持清单"会自动跟着变（见 {@link #supportedToolIds()}）。</p>
     */
    @Nullable
    private static Family familyOf(HandMadeTool tool) {
        return switch (tool) {
            case PRESS_HAMMER_BASIN, STIRRING_STAFF -> Family.BASIN;
            case MORTAR, CRUSHER_MORTAR -> Family.CRUSHING;
            default -> null;
        };
    }

    /** 错误信息里列出的「当前已支持」工具清单；从枚举推导，避免新增家族时漏改文案。 */
    private static String supportedToolIds() {
        StringBuilder sb = new StringBuilder();
        for (HandMadeTool tool : HandMadeTool.values()) {
            if (familyOf(tool) == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(tool.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    @Override
    public MapCodec<StandardProcessingRecipe<RecipeInput>> codec() {
        return new MapCodec<>() {

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                // 两个家族的参数集完全一样（都是 ProcessingRecipeParams），任取一个声明即可；
                // 额外声明 tool，让数据包校验 / 导出工具知道本 schema 还有这个键。
                return Stream.concat(BASIN_DELEGATE.codec().keys(ops), Stream.of(ops.createString(TOOL_FIELD)));
            }

            @Override
            public <T> DataResult<StandardProcessingRecipe<RecipeInput>> decode(DynamicOps<T> ops, MapLike<T> input) {
                // 0) 归一化流体原料：KubeJS 写出的形状缺 type 字段，Create 的 codec 不收
                //    （见 normalizeFluidIngredients）
                MapLike<T> normalized = normalizeFluidIngredients(ops, input);

                // 1) 先读 tool：它决定用哪个家族的配方类，所以必须在委托 decode 之前读
                DataResult<HandMadeTool> toolResult = readTool(ops, normalized);
                if (toolResult.error().isPresent()) {
                    return DataResult.error(toolResult.error().get()::message);
                }
                HandMadeTool tool = toolResult.getOrThrow();

                // 2) 校验归属工具是否有自己的配方类
                Family family = familyOf(tool);
                if (family == null) {
                    return DataResult.error(() -> "Tool '" + tool.name().toLowerCase(Locale.ROOT)
                            + "' does not support exclusive recipes yet. Supported: " + supportedToolIds());
                }

                // 3) 交给对应家族的 Create delegate 解析其余全部字段（它会忽略 tool）
                return switch (family) {
                    case BASIN -> decodeWith(BASIN_DELEGATE, ops, normalized, tool);
                    case CRUSHING -> decodeWith(CRUSHING_DELEGATE, ops, normalized, tool);
                };
            }

            @Override
            public <T> RecordBuilder<T> encode(StandardProcessingRecipe<RecipeInput> recipe, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                // 用配方对象的实际类型反查家族（它一定是某个 delegate 造出来的）
                RecordBuilder<T> builder = switch (recipe) {
                    case HandMadeCrushingRecipe crushing -> CRUSHING_DELEGATE.codec().encode(crushing, ops, prefix);
                    case HandMadeToolRecipe basin -> BASIN_DELEGATE.codec().encode(basin, ops, prefix);
                    default -> prefix;
                };

                if (recipe instanceof HandMadeToolRecipeLike like && like.getTool() != null) {
                    builder.add(TOOL_FIELD, ops.createString(like.getTool().name().toLowerCase(Locale.ROOT)));
                }
                return builder;
            }
        };
    }

    /**
     * 用某个家族的 delegate 解码，把结果统一成公共父类型，并写入 {@code tool}。
     *
     * <p>保持旧实现的行为：委托解码失败时原样返回它的 {@code DataResult}
     * （包括 validate 失败时携带的 partial value），此时不会写入 tool。</p>
     */
    private static <T, R extends StandardProcessingRecipe<RecipeInput>>
    DataResult<StandardProcessingRecipe<RecipeInput>> decodeWith(
            StandardProcessingRecipe.Serializer<R> delegate, DynamicOps<T> ops, MapLike<T> input, HandMadeTool tool) {
        return delegate.codec().decode(ops, input).map(recipe -> {
            ((HandMadeToolRecipeLike) recipe).setTool(tool);
            return (StandardProcessingRecipe<RecipeInput>) recipe;
        });
    }

    /**
     * 读 {@code tool} 字段并解析成枚举。
     *
     * <p>刻意不复用 {@link HandMadeRecipeFilterLoader#parseTool}：它的签名是
     * {@code (String, ResourceLocation fileId)}，且语义是「记 warning 后返回 null」，
     * 而 {@code MapCodec.decode} 拿不到配方 id（只能拿到 ops + MapLike），传 null 会打出
     * 误导性的 {@code "... in tool filter null"} 日志。所以这里只解析、不记日志，
     * 错误信息交给 {@code DataResult} 报给数据包加载器。</p>
     */
    private static <T> DataResult<HandMadeTool> readTool(DynamicOps<T> ops, MapLike<T> input) {
        T toolValue = input.get(TOOL_FIELD);
        if (toolValue == null) {
            return DataResult.error(() -> "Missing required field '" + TOOL_FIELD
                    + "' for " + HandMadeRecipeTypes.TOOL_RECIPE.getId());
        }

        DataResult<String> toolIdResult = Codec.STRING.parse(ops, toolValue);
        if (toolIdResult.error().isPresent()) {
            return DataResult.error(() -> "Invalid '" + TOOL_FIELD + "' field: expected a tool id string");
        }

        String toolId = toolIdResult.getOrThrow();
        HandMadeTool tool = parseToolOrNull(toolId);
        if (tool == null) {
            return DataResult.error(() -> "Unknown tool id: '" + toolId + "'. Valid ids: "
                    + HandMadeRecipeFilterLoader.validToolIds());
        }
        return DataResult.success(tool);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, StandardProcessingRecipe<RecipeInput>> streamCodec() {
        return StreamCodec.of(
                (buf, recipe) -> {
                    // 先写 tool：读的一侧要先知道家族，才能选对应的流 codec 构造对象
                    HandMadeTool tool = recipe instanceof HandMadeToolRecipeLike like ? like.getTool() : null;
                    buf.writeEnum(tool == null ? HandMadeTool.PRESS_HAMMER_BASIN : tool);

                    switch (recipe) {
                        case HandMadeCrushingRecipe crushing -> CRUSHING_DELEGATE.streamCodec().encode(buf, crushing);
                        case HandMadeToolRecipe basin -> BASIN_DELEGATE.streamCodec().encode(buf, basin);
                        default -> { }
                    }
                },
                buf -> {
                    HandMadeTool tool = buf.readEnum(HandMadeTool.class);
                    StandardProcessingRecipe<RecipeInput> recipe = familyOf(tool) == Family.CRUSHING
                            ? CRUSHING_DELEGATE.streamCodec().decode(buf)
                            : BASIN_DELEGATE.streamCodec().decode(buf);
                    ((HandMadeToolRecipeLike) recipe).setTool(tool);
                    return recipe;
                }
        );
    }

    /**
     * 把「扁平流体原料」补成 Create 要求的形状。
     *
     * <p><b>为什么需要：</b>KubeJS 的 {@code flat_sized_fluid_ingredient} 组件用 NeoForge 的
     * {@code SizedFluidIngredient.FLAT_CODEC} 序列化，写出来的是</p>
     * <pre>{"fluid": "minecraft:water", "amount": 100}</pre>
     * <p>而 Create 的原料 codec 是
     * {@code Codec.either(CreateCodecs.SIZED_FLUID_INGREDIENT, Ingredient.CODEC)}，其中
     * {@code SIZED_FLUID_INGREDIENT = withAlternative(FLAT_SIZED_FLUID_INGREDIENT_WITH_TYPE, ...)}，
     * 而 {@code FLAT_SIZED_FLUID_INGREDIENT_WITH_TYPE} <b>要求顶层 {@code type} 字段</b>：</p>
     * <pre>{"type": "neoforge:single", "amount": 100, "fluid": "minecraft:water"}</pre>
     * <p>只差这一个字段，整条配方就会解析失败。KubeJS 侧没有能产出该形状的组件
     * （{@code flat_*} 与 {@code nested_*} 都不带顶层 type），所以在本模组自己的
     * serializer 里补上 —— 只影响 {@code create_hand_made:tool_recipe} 这一个类型。</p>
     *
     * <p><b>改写规则（保守）：</b>只处理 {@code ingredients} 数组里「是对象、没有
     * {@code type}、且有 {@code fluid}」的元素；其余一律原样保留，包括物品原料、
     * 已经写了 {@code type} 的流体原料（例如数据包里手写的
     * {@code {"type":"neoforge:tag","amount":250,"tag":"c:milk"}}）以及
     * {@code fluid_tag} 旧格式。</p>
     *
     * <p><b>实现方式：</b>直接用 {@link DynamicOps} 的泛型 API 操作
     * （{@code getStream/getMap/createMap/createList}），不转成 {@code JsonElement} ——
     * 这样不会丢掉 ops 自身的上下文（配方加载时 ops 是 RegistryOps），
     * 也不需要引入 Gson。</p>
     *
     * @return 改写后的 MapLike；无需改写时原样返回 {@code input}
     */
    private static <T> MapLike<T> normalizeFluidIngredients(DynamicOps<T> ops, MapLike<T> input) {
        T rawIngredients = input.get(INGREDIENTS_FIELD);
        if (rawIngredients == null) {
            return input;
        }

        DataResult<Stream<T>> rawList = ops.getStream(rawIngredients);
        if (rawList.result().isEmpty()) {
            // 不是列表：交给 Create 的 codec 去报它自己的错
            return input;
        }

        List<T> rewritten = new ArrayList<>();
        boolean changed = false;

        for (T element : rawList.result().get().toList()) {
            DataResult<MapLike<T>> asMap = ops.getMap(element);
            if (asMap.result().isEmpty()
                    || asMap.result().get().get(TYPE_FIELD) != null
                    || asMap.result().get().get(FLUID_FIELD) == null) {
                rewritten.add(element);
                continue;
            }

            Map<T, T> patched = new LinkedHashMap<>();
            asMap.result().get().entries().forEach(entry -> patched.put(entry.getFirst(), entry.getSecond()));
            patched.put(ops.createString(TYPE_FIELD), ops.createString(SINGLE_FLUID_TYPE));

            rewritten.add(ops.createMap(patched));
            changed = true;
        }

        if (!changed) {
            return input;
        }

        // 重建外层 map：跳过旧的 ingredients，再把归一化后的列表追加进去
        RecordBuilder<T> builder = ops.mapBuilder();
        input.entries().forEach(entry -> {
            String key = ops.getStringValue(entry.getFirst()).result().orElse(null);
            if (!INGREDIENTS_FIELD.equals(key)) {
                builder.add(entry.getFirst(), entry.getSecond());
            }
        });
        builder.add(INGREDIENTS_FIELD, ops.createList(rewritten.stream()));

        // build(...) 收的是 ops 自己的值类型（不是 MapLike），所以用空容器作前缀，
        // 其余字段已经在上面的循环里逐条复制过去了
        return ops.getMap(builder.build(ops.empty()).getOrThrow()).result().orElse(input);
    }

    /**
     * 把 {@code tool_id} 解析成 {@link HandMadeTool}，失败返回 null（不记日志）。
     *
     * <p>与 {@link HandMadeRecipeFilterLoader#validToolIds()} 一样，枚举名与 tool id 的
     * 关系就是「全大写下划线 ↔ 全小写下划线」。</p>
     */
    @Nullable
    private static HandMadeTool parseToolOrNull(String toolId) {
        try {
            return HandMadeTool.valueOf(toolId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
