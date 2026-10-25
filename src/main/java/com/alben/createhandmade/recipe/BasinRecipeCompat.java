package com.alben.createhandmade.recipe;

import com.zurrtum.create.content.processing.basin.BasinInput;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * 工作盆配方的「匹配 / 应用」兼容层。
 *
 * <h3>为什么需要</h3>
 * <p>NeoForge 版有静态入口 {@code BasinRecipe.match(basin, recipe)} / {@code BasinRecipe.apply(basin, recipe)}，
 * 它们在内部按配方种类分派：{@code BasinRecipe} 走自己的 {@code matches/apply}，
 * 原版 {@code ShapelessRecipe} / {@code ShapedRecipe} 走 {@code matchCraftingRecipe} /
 * {@code applyCraftingRecipe}（即「工作台配方也能在工作盆里手工合成」）。</p>
 *
 * <p>26.1 的 Create Fly 把这套静态入口删了，只剩实例方法 {@code BasinRecipe#matches(BasinInput, Level)}
 * 与两个静态 helper {@code matchCraftingRecipe} / {@code applyCraftingRecipe}。
 * 如果直接写 {@code recipe instanceof BasinRecipe}，就会把<b>原版合成配方</b>全部漏掉
 * （{@code ShapelessRecipe} 并不实现 {@code BasinRecipe}）—— 表现为「搅拌杖 / 冲压锤对
 * 无序合成配方完全没反应」。这里把旧版的分派逻辑原样补回来。</p>
 */
public final class BasinRecipeCompat {

    private BasinRecipeCompat() {
    }

    /** 等价于旧版 {@code BasinRecipe.match(basin, recipe)}。 */
    public static boolean matches(BasinInput input, Recipe<?> recipe, Level level) {
        if (recipe instanceof BasinRecipe basinRecipe) {
            return basinRecipe.matches(input, level);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return BasinRecipe.matchCraftingRecipe(input, shapeless, level);
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return BasinRecipe.matchCraftingRecipe(input, shaped, level);
        }
        return false;
    }

    /** 等价于旧版 {@code BasinRecipe.apply(basin, recipe)}。 */
    public static boolean apply(BasinInput input, Recipe<?> recipe, Level level) {
        if (recipe instanceof BasinRecipe basinRecipe) {
            return basinRecipe.apply(input);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return BasinRecipe.applyCraftingRecipe(input, shapeless, level);
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return BasinRecipe.applyCraftingRecipe(input, shaped, level);
        }
        return false;
    }
}
