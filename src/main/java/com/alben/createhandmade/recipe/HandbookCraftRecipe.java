package com.alben.createhandmade.recipe;

import com.alben.createhandmade.item.PressHammerItem;
import com.alben.createhandmade.recipe.serializer.HandbookCraftRecipeSerializer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * 「书 + 冲压锤 → 手册」这条合成用的无序配方：材料里的冲压锤 <b>不消耗</b>，原样退回。
 *
 * <p><b>为什么不用原版 {@code minecraft:crafting_shapeless}：</b>原版只有
 * {@code Item.Properties#craftingRemainingItem} 这一条"退回物品"的路，而它绑的是
 * <b>Item 实例</b>——必须在构造期给（拿不到自己的实例），而且退回来的是<b>全新默认态</b>
 * 物品，耐久/附魔/NBT 全丢。想要"同一把锤子原样回来"，只能在
 * {@link #getRemainingItems(CraftingContainer)} 里自己往格子里塞。</p>
 *
 * <p>所以这里继承 {@link ShapelessRecipe}（保持 instanceof 关系，JEI 与配方数据照旧认得它），
 * 只覆写"剩余物"和 serializer。合成台的结算走
 * {@code RecipeManager#getRemainingItemsFor} → {@code Recipe#getRemainingItems}，覆写即生效。</p>
 */
public class HandbookCraftRecipe extends ShapelessRecipe {

    /** 产物副本：父类的 result 是包私有字段，网络同步与剩余物计算都用这份。 */
    private final ItemStack result;

    public HandbookCraftRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                               ItemStack result, NonNullList<Ingredient> ingredients) {
        super(id, group, category, result, ingredients);
        this.result = result.copy();
    }

    /** 供序列化器读回产物（父类没有公开的取原始产物方法）。 */
    public ItemStack resultStack() {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HandMadeRecipeTypes.HANDBOOK_CRAFT_SERIALIZER.get();
    }

    /**
     * 把材料里的冲压锤原样退回（耐久、附魔、自定义 NBT 都保留）。
     *
     * <p>★ 必须 {@code copyWithCount(1)}：合成台每格只消耗 1 个，退回整栈会把物品复制出来。</p>
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(container);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack in = container.getItem(i);
            if (in.getItem() instanceof PressHammerItem) {
                remaining.set(i, in.copyWithCount(1));
            }
        }
        return remaining;
    }
}
