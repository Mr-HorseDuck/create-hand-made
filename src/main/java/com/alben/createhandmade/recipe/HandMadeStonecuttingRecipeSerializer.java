package com.alben.createhandmade.recipe;

import net.minecraft.world.item.crafting.SingleItemRecipe;

/**
 * 手锯切石 L3 独占配方的 serializer（T7 批次 2）。
 *
 * <p><b>为什么只有 3 行：</b>原版 {@link SingleItemRecipe.Serializer} 是完全泛型的
 * ——它只接受一个 {@link SingleItemRecipe.Factory}，内部用
 * {@code RecordCodecBuilder.mapCodec} 组装 {@code group? + ingredient + result{id,count}}，
 * <b>完全不涉及 {@code RecipeType}</b>（type 由配方子类的构造参数决定）。
 * 所以直接继承它、把工厂指向 {@link HandMadeStonecuttingRecipe} 的构造即可，
 * codec / streamCodec 一行都不用写。</p>
 *
 * <p><b>关于 protected 构造：</b>{@code SingleItemRecipe.Serializer} 的构造是
 * {@code protected}，跨包不能直接 {@code new}，但<b>子类 {@code super(...)} 是合法的</b>
 * ——这正是本类存在的唯一理由。序列化格式因此与原版切石配方完全一致，数据包作者
 * 与 KubeJS 作者看到的是同一套字段形状：
 * <pre>
 * { "ingredient": { "item": "minecraft:stone" },
 *   "result": { "id": "minecraft:stone_bricks", "count": 1 } }
 * </pre>
 * （{@code group} 可选，缺省 {@code ""}。）</p>
 *
 * <p><b>注册点：</b>{@link HandMadeRecipeTypes#STONECUTTING_RECIPE}
 * ——该枚举会同时注册出 {@code create_hand_made:stonecutting_recipe} 的
 * {@code RecipeType} 与 {@code RecipeSerializer}，本类不需要自己注册任何东西。</p>
 */
public class HandMadeStonecuttingRecipeSerializer
        extends SingleItemRecipe.Serializer<HandMadeStonecuttingRecipe> {

    public HandMadeStonecuttingRecipeSerializer() {
        super(HandMadeStonecuttingRecipe::new);
    }
}
