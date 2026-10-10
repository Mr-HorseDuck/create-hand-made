package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.item.HandSawItem;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 女仆切削辅助：只计算产物，不动 input、不生成掉落、不扣耐久。
 *
 * ★ 全部待在 tlmCompat，main 侧无任何改动。
 *   依赖 HandSawItem.getCuttingRecipes（public）与 NBT_RECIPE_INDEX（public）。
 */
public final class MaidCutHelper {

    private MaidCutHelper() {}

    /**
     * @return 产物列表；无配方或过滤器拒绝时返回空列表
     */
    public static List<ItemStack> computeCutResults(Level level, ItemStack saw, ItemStack input,
                                                     @Nullable FilterItemStack filter) {
        if (saw.isEmpty() || input.isEmpty()) return List.of();

        List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, input);
        if (recipes.isEmpty()) return List.of();

        if (filter != null) {
            for (Recipe<?> candidate : recipes) {
                List<ItemStack> candidateResults = new ArrayList<>();
                if (candidate instanceof CuttingRecipe cr) {
                    candidateResults = cr.rollResults();
                } else {
                    candidateResults.add(candidate.getResultItem(level.registryAccess()).copy());
                }
                if (resultsPassFilter(level, candidateResults, filter)) {
                    return candidateResults;
                }
            }
            return List.of();
        } else {
            int index = saw.getOrCreateTag().getInt(HandSawItem.NBT_RECIPE_INDEX);
            if (index < 0 || index >= recipes.size()) index = 0;
            Recipe<?> recipe = recipes.get(index);
            if (recipe instanceof CuttingRecipe cr) {
                return cr.rollResults();
            } else {
                List<ItemStack> results = new ArrayList<>();
                results.add(recipe.getResultItem(level.registryAccess()).copy());
                return results;
            }
        }
    }

    private static boolean resultsPassFilter(Level level, List<ItemStack> results,
                                              @Nullable FilterItemStack filter) {
        if (filter == null) return true;
        for (ItemStack s : results) {
            if (!s.isEmpty() && filter.test(level, s)) return true;
        }
        return false;
    }
}