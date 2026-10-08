package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 本模组自己的 {@link RecipeType} / {@link RecipeSerializer} 注册表（L3 独占层的地基）。
 *
 * <p>独占配方 = 只有本模组的手搓工具能读到、Create 的机器读不到的配方。
 * 做法是给它一个<b>独立的 RecipeType</b>：Create 的机器查询
 * {@code AllRecipeTypes.XXX.getType()}，永远查不到
 * {@code create_hand_made:tool_recipe} 这个 type 下的配方。</p>
 *
 * <p><b>为什么能复用 Create 的 serializer：</b>
 * {@link StandardProcessingRecipe.Serializer} 是完全泛型的，只接受一个
 * {@code Factory<R>}（{@code ProcessingRecipeParams -> R}），内部
 * 用 {@code ProcessingRecipe.codec(factory, ProcessingRecipeParams.CODEC)} 组装，
 * <b>没有任何 RecipeType 检查</b>（见 Create sources:
 * {@code StandardProcessingRecipe.java:45-69} 与 {@code ProcessingRecipe.java:215-226}）。
 * 所以这里直接 {@code new StandardProcessingRecipe.Serializer<>(HandMadeToolRecipe::new)}，
 * 零新增序列化逻辑。</p>
 *
 * <p><b>为什么必须自己实现 {@link IRecipeTypeInfo}：</b>
 * {@code ProcessingRecipe} 的 {@code getType()} / {@code getSerializer()} 来自构造时传入的
 * {@code IRecipeTypeInfo}（{@code ProcessingRecipe.java:56-57,202-209}），
 * 而 Create 各具体配方的公共构造把类型写死成了自己的枚举
 * （例如 {@code BasinRecipe(params)} → {@code this(AllRecipeTypes.BASIN, params)}）。
 * 所以独占配方必须由本项目自己的配方类承载，并把本枚举传进去 —— 这就是
 * {@link HandMadeToolRecipe} 存在的唯一理由。</p>
 *
 * <p><b>为什么注册器放在嵌套类里：</b>枚举常量在类初始化时最先构造，若把
 * {@code DeferredRegister} 声明成枚举自己的静态字段，构造时它还是 null。
 * 放进嵌套类 {@link Registers} 后由「首次访问时初始化」解决这个顺序问题 ——
 * Create 自己也是这么做的（{@code AllRecipeTypes.java:172-175}）。</p>
 */
public enum HandMadeRecipeTypes implements IRecipeTypeInfo {

    /**
     * 手工工具独占配方。
     *
     * <p>目前只有一个 type；将来若需要按家族拆（basin / milling / pressing…），
     * 在这里加常量即可，每个常量自动获得自己的 type + serializer。</p>
     */
    TOOL_RECIPE(HandMadeToolRecipeSerializer::new),

    /**
     * 手锯 · 切石独占配方（{@code create_hand_made:stonecutting_recipe}，T7 批次 2）。
     *
     * <p><b>为什么不并进 {@link #TOOL_RECIPE}：</b>后者的 serializer
     * （{@link HandMadeToolRecipeSerializer}）整体建立在 Create 的
     * {@code StandardProcessingRecipe} 上（codec 返回的就是它、按 {@code tool} 字段分派家族），
     * 而切石配方属于原版 {@code SingleItemRecipe} 家族
     * （见 {@link HandMadeStonecuttingRecipe}），两者无法共用一条解析路径。
     * 因此照 {@link #BELLOWS_RECIPE} 的先例，给它一个独立 type + 独立 serializer。</p>
     *
     * <p><b>独占性：</b>原版切石机与 Create 机械锯查的都是 {@code minecraft:stonecutting}，
     * 永远看不到本 type 下的配方 —— 只有手锯切石段
     * （{@link HandMadeTool#HAND_SAW_STONECUTTING} 的候选集，在
     * {@code HandMadeRecipePool.collectHandSawStonecutting} 里以 L1 之后的位置并入）会读到。</p>
     */
    STONECUTTING_RECIPE(HandMadeStonecuttingRecipeSerializer::new),

    /**
     * 风箱独占配方（{@code fan_type} + 单品输入）。
     *
     * <p><b>为什么不并进 {@link #TOOL_RECIPE}：</b>风箱不属于 {@code HandMadeTool} 体系
     * （{@code HandMadeTool.java:13-15} 明确写了它不在枚举里、也不为它建 Pool 条目），
     * 字段集也不同（{@code fan_type} 必填、禁止流体）。语义上它是<b>另一个</b> L3 类型，
     * 所以拿一个独立的常量 + 独立的 serializer（见 {@link HandMadeBellowsRecipeSerializer} 的类注释）。</p>
     */
    BELLOWS_RECIPE(HandMadeBellowsRecipeSerializer::new);

    /** 延迟注册器。必须是嵌套类，见类注释里的初始化顺序说明。 */
    private static final class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreateHandMade.MODID);

        private static final DeferredRegister<RecipeType<?>> TYPES =
                DeferredRegister.create(Registries.RECIPE_TYPE, CreateHandMade.MODID);
    }

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

    HandMadeRecipeTypes(Supplier<RecipeSerializer<?>> serializerFactory) {
        var name = name().toLowerCase(Locale.ROOT);
        this.id = ResourceLocation.fromNamespaceAndPath(CreateHandMade.MODID, name);

        // 复用 Create 泛型 serializer 的薄包装：每个常量自己决定用哪个 serializer
        // （TOOL_RECIPE → HandMadeToolRecipeSerializer，BELLOWS_RECIPE → HandMadeBellowsRecipeSerializer）
        this.serializer = Registers.SERIALIZERS.register(name, serializerFactory);

        // 与 Create 同款写法（AllRecipeTypes.java:116 用的也是 RecipeType.simple(id)）
        this.type = Registers.TYPES.register(name, () -> RecipeType.simple(id));
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    /**
     * 把全部 type + serializer 注册到 mod 事件总线。
     *
     * <p>调用本静态方法会先触发本枚举的类初始化，因此所有枚举常量
     * （以及它们登记到 {@link Registers} 里的 {@code DeferredHolder}）
     * 一定早于这里的 {@code register(bus)} 完成 —— 顺序是有保证的。</p>
     */
    public static void register(IEventBus modEventBus) {
        Registers.TYPES.register(modEventBus);
        Registers.SERIALIZERS.register(modEventBus);
    }
}
