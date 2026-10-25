package com.alben.createhandmade.item;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 手锯「副手物品能切出什么」的客户端缓存（服务端推送 → 客户端读取）。
 *
 * <h3>为什么需要</h3>
 * <p>1.21.1 的客户端是带配方表的，所以旧版 {@code HandSawHudRenderer} 直接
 * {@code HandSawItem.getCuttingRecipes(level, off)} 就能画出 HUD。</p>
 * <p>26.1 起配方表<b>只在服务端</b>（{@code RecipeManager} 挂在 {@code MinecraftServer} 上），
 * 客户端的 {@link com.alben.createhandmade.recipe.HandMadeRecipePool#getBaseRecipes} 恒为空表。
 * 于是 HUD 没有数据可画，{@code use()} 在客户端也永远返回 PASS（连"使用中"状态都进不去，
 * 动画自然也不会播）。</p>
 *
 * <h3>做法</h3>
 * <p>服务端每 tick 检查一次「主手是手锯 + 副手有物品」的组合，<b>只在组合变化时</b>把产物列表
 * 推给该玩家（{@link com.alben.createhandmade.network.HandSawRecipesPacket}）。客户端把它存在这里：
 * <ul>
 *   <li>HUD 直接读 {@link #outputs()}；</li>
 *   <li>{@code use()} 用它判断"要不要进入切削状态"，避免客户端与服务端行为分叉。</li>
 * </ul>
 *
 * <h3>为什么 key 是「物品 + 组件」而不是整条栈</h3>
 * <p>配方只取决于输入物品的种类与组件（{@code getCuttingRecipes} 内部就是
 * {@code copyWithCount(1)} 去匹配），数量从 64 掉到 63 不该触发重推。</p>
 *
 * <p>本类<b>不引用任何客户端类</b>，可以安全地在服务端/客户端两侧共用。</p>
 */
public final class HandSawRecipeCache {

    /** 这份数据对应的副手物品（数量恒为 1）；空 = 还没有数据 / 已清空。 */
    private static ItemStack input = ItemStack.EMPTY;

    /** 这份数据对应的产物（按配方顺序，已过滤空产物）。 */
    private static List<ItemStack> outputs = List.of();

    private HandSawRecipeCache() {
    }

    /** 客户端收包：整份替换。 */
    public static void accept(ItemStack newInput, List<ItemStack> newOutputs) {
        input = newInput.isEmpty() ? ItemStack.EMPTY : newInput.copyWithCount(1);
        outputs = newOutputs.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    /** 清空（换手 / 下线）。 */
    public static void clear() {
        input = ItemStack.EMPTY;
        outputs = List.of();
    }

    /** 这份数据是不是就是 {@code stack} 的？ */
    public static boolean matches(ItemStack stack) {
        if (input.isEmpty() || stack.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(input, stack);
    }

    /** 是否已经收到过任何数据（用来区分"没有配方"与"还没同步到"）。 */
    public static boolean isKnown() {
        return !input.isEmpty();
    }

    /** 产物列表（只读，按配方顺序）。 */
    public static List<ItemStack> outputs() {
        return outputs;
    }
}
