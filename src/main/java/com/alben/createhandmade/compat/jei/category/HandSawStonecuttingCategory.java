package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.zurrtum.create.client.compat.jei.category.BlockCuttingCategory;
import com.zurrtum.create.client.compat.jei.display.BlockCuttingDisplay;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 手锯 · 切石（#2）。
 *
 * <p><b>布局直接继承 26.1 Create Fly 的 {@link BlockCuttingCategory}</b>，一个坐标常量都不自己写：
 * 输入槽 (5,5)、产物 5 列网格从 (78,48) 起、每列 +19 / 每行 -19（最多 15 个槽）、
 * {@code JEI_DOWN_ARROW}(31,6)、{@code JEI_SHADOW}(16,50)、装饰模型原点 (25,26)、高度 70。
 * 所以本类只覆写「类型 / 标题 / 图标」和折叠器 —— 这是 PLAN §3 #2 与 §7.2 的既定做法。</p>
 *
 * <p><b>为什么要自写折叠器：</b>上游 Create 1.21 的
 * {@code CondensedBlockCuttingRecipe} / {@code condenseRecipes} 在 26.1 已不存在，
 * 26.1 的 {@code BlockCuttingCategory.getRecipes(RecipeMap)} 把折叠算法直接写在类别里（内部类
 * {@code BlockCuttingCategory$1} 是「按 {@link Ingredient} 取哈希」的策略）。本类
 * <b>照抄它的折叠规则</b>（同输入并组、超过 15 条时按 {@code i % 15} 并入已有槽），
 * 但数据源换成 {@link JeiRecipeSource#stonecutting()}，从而额外吃进本模组 L3 的
 * {@code create_hand_made:stonecutting_recipe}（Create 的现成收集器只扫原版
 * {@code minecraft:stonecutting}）。</p>
 *
 * <p><b>注意 {@link Ingredient} 没有覆写 {@code hashCode()}（26.1 实测）</b>，所以不能拿它当
 * HashMap 键（Create 因此用了自定义 {@code Hash.Strategy}）。这里改用线性查表 + {@code equals}
 * 分组：原版切石两三百条配方、上百个不同输入，等价比较的开销可以忽略。</p>
 *
 * <p><b>已知的外观遗留：</b>{@code draw} 也是从 {@link BlockCuttingCategory} 继承的，所以装饰仍是
 * Create 的机械锯 3D 模型（{@code SawRenderState}，原点 (25,26)）。本类按 PLAN 的约定只覆写
 * 「类型 / 标题 / 图标 + 折叠器」，没有动 {@code draw}；若后续要把装饰换成手锯图标，
 * 只需追加一个 {@code draw} 覆写（把 {@code SawRenderState} 换成
 * {@code graphics.item(new ItemStack(ModItems.HAND_SAW), 25, 26)}）。</p>
 */
public class HandSawStonecuttingCategory extends BlockCuttingCategory {

    /** 标题键：与已有 lang 文件一致。 */
    private static final String TITLE_KEY = "jei.create_hand_made.hand_saw_stonecutting";

    /** 26.1 {@code BlockCuttingCategory.getRecipes} 的槽位上限：5 列 × 3 行。 */
    private static final int MAX_SLOTS = 15;

    /**
     * 折叠后的显示列表（净 {@code getRecipes(RecipeMap)} 的等价物，只是数据源换成客户端快照
     * 且额外并入了本模组的 L3 切石配方）。
     */
    public static List<BlockCuttingDisplay> getRecipes() {
        List<Group> groups = new ArrayList<>();
        for (JeiRecipeSource.StonecuttingEntry entry : JeiRecipeSource.stonecutting()) {
            Group group = find(groups, entry.input());
            if (group == null) {
                group = new Group(entry.id(), entry.input());
                groups.add(group);
            }
            group.outputs.add(entry.result());
        }

        List<BlockCuttingDisplay> displays = new ArrayList<>(groups.size());
        for (Group group : groups) {
            displays.add(new BlockCuttingDisplay(group.id, group.input,
                    group.outputs.size() <= MAX_SLOTS ? spread(group.outputs) : condense(group.outputs)));
        }
        return displays;
    }

    /** 线性找一个输入相同的组（{@link Ingredient} 没有 hashCode，只能靠 equals）。 */
    private static Group find(List<Group> groups, Ingredient input) {
        for (Group group : groups) {
            if (group.input.equals(input)) {
                return group;
            }
        }
        return null;
    }

    /** ≤15 条：一条一个槽（每个槽只有一个产物）。 */
    private static List<List<ItemStackTemplate>> spread(List<ItemStackTemplate> outputs) {
        List<List<ItemStackTemplate>> slots = new ArrayList<>(outputs.size());
        for (ItemStackTemplate output : outputs) {
            slots.add(List.of(output));
        }
        return slots;
    }

    /**
     * >15 条：先给前 15 条各占一个槽，剩下的第 i 条并进第 {@code i % 15} 个槽
     * （照抄 26.1 {@code BlockCuttingCategory.getRecipes} 的 else 分支）。
     *
     * <p>效果：同一个输入的产物仍然只占 5×3 的面积，JEI 里翻页看同一槽内的多个候选，
     * 而不是把面板撑成几百格。</p>
     */
    private static List<List<ItemStackTemplate>> condense(List<ItemStackTemplate> outputs) {
        List<List<ItemStackTemplate>> slots = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++) {
            List<ItemStackTemplate> inner = new ArrayList<>(2);
            inner.add(outputs.get(i));
            slots.add(inner);
        }
        for (int i = MAX_SLOTS; i < outputs.size(); i++) {
            slots.get(i % MAX_SLOTS).add(outputs.get(i));
        }
        return slots;
    }

    @Override
    public IRecipeType<BlockCuttingDisplay> getRecipeType() {
        return ModJeiTypes.HAND_SAW_STONECUTTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 的原图标是 (机械锯, 石砖台阶)；手锯切石 → 手锯 + 同一个示例产物。
        return new TwoIconRenderer(ModItems.HAND_SAW, Items.STONE_BRICK_STAIRS);
    }

    /** 折叠中间状态：首条配方的 id（当显示 UID）+ 输入 + 该输入的全部产物。 */
    private static final class Group {

        private final Identifier id;
        private final Ingredient input;
        private final List<ItemStackTemplate> outputs = new ArrayList<>(2);

        private Group(Identifier id, Ingredient input) {
            this.id = id;
            this.input = input;
        }
    }
}
