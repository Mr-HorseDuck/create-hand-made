package com.alben.createhandmade.recipe;

import java.util.List;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * 序列组装（Create 的 {@code sequenced_assembly}）侧的两个小助手：工具 → 物品 / 语言键。
 *
 * <p>L3 独占配方通过 {@link HandMadeAssemblyRecipe} 实现 Create 的
 * {@code IAssemblyRecipe}，接口要求提供「这一步需要什么机器（{@code ItemLike}）」和
 * 「这一步要做什么（{@code Component}）」。本模组的「机器」是手工工具物品，
 * 归属关系记录在 {@link HandMadeTool} 上（一个物品可能对应多个枚举常量，
 * 例如冲压锤有 3 个、手锯有 2 个），因此这里做一次归并。</p>
 *
 * <p><b>为什么单独一个类而不是放到枚举里：</b>枚举常量的顺序参与
 * {@link HandMadeToolRecipeSerializer} 的 {@code writeEnum/readEnum} 网络同步，
 * 往枚举里加方法不改变序号，但为了不把「物品 / 语言键」这类表现层信息塞进枚举语义，
 * 仍然集中放在这里。</p>
 */
public final class HandMadeAssembly {

    /** 工具物品的语言键前缀（与 lang 文件里的 {@code item.create_hand_made.*} 一致）。 */
    private static final String ITEM_KEY_PREFIX = "item.create_hand_made.";

    private HandMadeAssembly() {
    }

    /**
     * 归属工具对应的物品；{@code tool} 为 {@code null}（旧数据 / 未标注）时返回 {@code null}。
     *
     * <p>调用时机都在注册完成之后（JEI 建索引、tooltip 构建），所以
     * {@code ModItems.*.get()} 取注册结果是安全的。</p>
     */
    @Nullable
    public static Item toolItem(@Nullable HandMadeTool tool) {
        if (tool == null) {
            return null;
        }
        return switch (tool) {
            case PRESS_HAMMER_BASIN, PRESS_HAMMER_AUTO_SQUARE, PRESS_HAMMER_DEPOT -> ModItems.PRESS_HAMMER.get();
            case MORTAR -> ModItems.MORTAR.get();
            case CRUSHER_MORTAR -> ModItems.CRUSHER_MORTAR.get();
            case HAND_SAW, HAND_SAW_STONECUTTING -> ModItems.HAND_SAW.get();
            case STIRRING_STAFF, STIRRING_STAFF_AUTO_SHAPELESS, STIRRING_STAFF_AUTO_BREWING ->
                    ModItems.STIRRING_STAFF.get();
            case POINTER -> ModItems.POINTER.get();
            case INFUSION_GUN -> ModItems.INFUSION_GUN.get();
        };
    }

    /**
     * {@link HandMadeRecipeTypes#TOOL_RECIPE} 的类型，带上泛型参数以便喂给
     * {@code SequencedAssemblyRecipe#getRecipe(Level, ItemStack, RecipeType&lt;R&gt;, Class&lt;R&gt;)}。
     *
     * <p>{@code IRecipeTypeInfo#getType()} 的返回类型是 {@code RecipeType<?}}，直接传给
     * Create 那个方法无法让 {@code R} 收敛到具体家族类；这里做一次<b>受控</b>的 unchecked 转换
     * （类型唯一，且调用方总是紧跟一个 {@code XxxRecipe.class}，所以转换方向由调用点约束）。</p>
     *
     * @param <R> 家族配方类型（如 {@code HandMadePressingRecipe}）
     */
    @SuppressWarnings("unchecked")
    public static <R extends Recipe<?>> RecipeType<R> toolRecipeType() {
        return (RecipeType<R>) (RecipeType<?>) HandMadeRecipeTypes.TOOL_RECIPE.getType();
    }

    /** 归属工具物品的语言键；{@code tool} 为 {@code null} 时返回通用兜底键。 */
    public static String toolItemKey(@Nullable HandMadeTool tool) {
        if (tool == null) {
            return "create_hand_made.recipe.assembly.unknown_tool";
        }
        return ITEM_KEY_PREFIX + switch (tool) {
            case PRESS_HAMMER_BASIN, PRESS_HAMMER_AUTO_SQUARE, PRESS_HAMMER_DEPOT -> "press_hammer";
            case MORTAR -> "mortar";
            case CRUSHER_MORTAR -> "crusher_mortar";
            case HAND_SAW, HAND_SAW_STONECUTTING -> "hand_saw";
            case STIRRING_STAFF, STIRRING_STAFF_AUTO_SHAPELESS, STIRRING_STAFF_AUTO_BREWING -> "stirring_staff";
            case POINTER -> "pointer";
            case INFUSION_GUN -> "infusion_gun";
        };
    }

    /**
     * 一步的「额外准备」：指杆这类步骤的手持物、灌注枪这类步骤的流体。
     *
     * <p>序列组装初始化时第 0 槽已被换成过渡物品，所以物品从 1 开始看。
     * 与 Create 的 {@code addAssemblyIngredients} / {@code addAssemblyFluidIngredients}
     * 是同一份数据，只是这里压成一行的文本，供 tooltip 与 JEI 使用。</p>
     *
     * <p>例：{@code 燧石}、{@code 250 mB 水}；没有额外需求时返回 {@code null}。</p>
     */
    @Nullable
    public static Component stepDetail(ProcessingRecipe<?, ?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 1; i < ingredients.size(); i++) {
            ItemStack[] stacks = ingredients.get(i)
                    .getItems();
            if (stacks.length > 0) {
                return stacks[0].getHoverName();
            }
        }

        if (!recipe.getFluidIngredients()
                .isEmpty()) {
            SizedFluidIngredient fluid = recipe.getFluidIngredients()
                    .get(0);
            FluidStack[] stacks = fluid.getFluids();
            Component fluidName = stacks.length > 0
                    ? stacks[0].getHoverName()
                    : Component.translatable("create_hand_made.recipe.assembly.some_fluid");
            return Component.literal(fluid.amount() + " mB ")
                    .append(fluidName);
        }

        return null;
    }

    /**
     * 序列组装 tooltip 里「这一步要做什么」的文本：{@code <工具>}，有额外需求时追加
     * {@code · <附加物/流体>}。
     *
     * <p>与 Create 的 {@code getDescriptionForAssembly()} 语义一致（Create 会写成
     * “deploying item: sandpaper” / “spout filling fluid: water”，这里用中点分隔以保持精简）。</p>
     */
    public static Component stepDescription(@Nullable HandMadeTool tool, @Nullable Component detail) {
        Component base = Component.translatable(toolItemKey(tool));
        if (detail == null) {
            return base;
        }
        return base.copy()
                .append(Component.literal(" · "))
                .append(detail);
    }
}
