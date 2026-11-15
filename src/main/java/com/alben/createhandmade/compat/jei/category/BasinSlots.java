package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.recipe.HandMadeToolRecipe;
import com.zurrtum.create.content.kinetics.mixer.CompactingRecipe;
import com.zurrtum.create.content.kinetics.mixer.MixingRecipe;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.infrastructure.fluids.FluidStack;

import java.util.List;

/**
 * 从 {@link BasinRecipe} 接口上取「产物列表」的小工具（切片 3 专用）。
 *
 * <p><b>为什么需要它：</b>26.1 的 {@code BasinRecipe} 接口<b>不</b>声明
 * {@code results()} / {@code fluidResults()}（{@code javap -p} 实测：接口只有
 * {@code getIngredientSize / ingredients / fluidIngredients / heat / apply / assemble}），
 * 这两个 accessor 是各具体 record 自己的分量。Create 的 {@code CompactingCategory} /
 * {@code MixingCategory} 能直接调，是因为它们的类型参数是<b>具体 record</b>：
 * {@code BasinCategory<CompactingRecipe>}。</p>
 *
 * <p>本模组做不到这一点 —— 工作盆家族的显示类型必须是接口 {@code BasinRecipe}，
 * 因为 L3 的 {@link HandMadeToolRecipe} 既不是 {@code CompactingRecipe} 也不是
 * {@code MixingRecipe}（JEI 的 {@code IRecipeType<T>} 不变型，绑具体 record 就塞不进 L3）。
 * 所以这里做一次「接口 → 具体 record」的分派，把产物列表交给
 * {@code BasinCategory.addResultSlots / addFluidResultSlots} 处理。</p>
 *
 * <p><b>分派表刻意只列本模组真正会碰到的 4 个 record：</b>
 * {@code MixingRecipe}（MIXING）、{@code CompactingRecipe}（COMPACTING）、
 * {@link HandMadeToolRecipe}（L3）。{@code PotionRecipe}（POTION）的产出是<b>流体</b>，
 * 且只有一个 {@code result()} 字段，酿造类别自己直接取（见
 * {@link StirringStaffMixingCategory.Brewing}），不经这里。未知实现返回空表
 * —— 表现为「只显示输入、没有产物」，比抛异常安全（JEI 注册期抛异常会让整个插件失效）。</p>
 */
final class BasinSlots {

    private BasinSlots() {
    }

    /** 物品产物（含概率）；工作盆家族的三类里只有药水没有物品产物。 */
    static List<ProcessingOutput> results(BasinRecipe recipe) {
        if (recipe instanceof MixingRecipe mixing) {
            return mixing.results();
        }
        if (recipe instanceof CompactingRecipe compacting) {
            return compacting.results();
        }
        if (recipe instanceof HandMadeToolRecipe handMade) {
            return handMade.results();
        }
        return List.of();
    }

    /** 流体产物；{@code CompactingRecipe} 没有流体产物字段，只可能在混合 / L3 里出现。 */
    static List<FluidStack> fluidResults(BasinRecipe recipe) {
        if (recipe instanceof MixingRecipe mixing) {
            return mixing.fluidResults();
        }
        if (recipe instanceof HandMadeToolRecipe handMade) {
            return handMade.fluidResults();
        }
        return List.of();
    }
}
