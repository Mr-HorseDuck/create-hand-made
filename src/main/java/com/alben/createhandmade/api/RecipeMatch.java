package com.alben.createhandmade.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import javax.annotation.Nullable;

/**
 * {@link CreateHandMadeApi#findRecipeMatchFor} 的返回值：配方本体 + 它的数据包 ID。
 *
 * <p>1.20.1 的 {@link Recipe} 没有 {@code RecipeHolder} 包一层，拿不到 {@code id()}，
 * 所以 ID 需要单独反查一次。动态配方（{@code SequencedAssemblyRecipe} 之类）没有稳定
 * ID，这种情况 {@link #id()} 为 {@code null}，用 {@link #hasId()} 判断。</p>
 *
 * <p>0.7.1.1 新增，向后兼容：{@link CreateHandMadeApi#findRecipeFor} 保持原样。</p>
 *
 * @since 0.7.1.1
 */
public record RecipeMatch(Recipe<?> recipe, @Nullable ResourceLocation id) {

    /** 有稳定 ID（可以被数据包 / KubeJS 过滤器命中）；动态配方为 false。 */
    public boolean hasId() {
        return id != null;
    }
}
