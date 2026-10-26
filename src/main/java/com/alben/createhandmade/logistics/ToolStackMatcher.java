package com.alben.createhandmade.logistics;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 物流/打包用的"宽松栈匹配"：只对本模组工具忽略易变组件。
 *
 * <h3>为什么需要</h3>
 * <p>注液枪（液体量/液体种类）、研钵与碾钵（加工进度）的组件会随使用变化。
 * Create 的仓储请求在快照之后就按"确切组件"去库存里抽取，一旦状态变了就抽不到，
 * 表现为请求被接受、待打包栏闪一下消失。</p>
 *
 * <h3>放宽到什么程度</h3>
 * <ul>
 *   <li>本模组物品：比较<b>物品 + 耐久值</b>，忽略其余组件（液体、进度等）；</li>
 *   <li>其它模组物品：完全走原版 {@link ItemStack#isSameItemSameComponents}，行为不变。</li>
 * </ul>
 * 耐久仍参与比较，避免"快坏的"和"全新的"被当成同一件。
 */
public final class ToolStackMatcher {

    private ToolStackMatcher() {
    }

    /** 见类注释；这是所有 mixin 注入点共用的唯一实现。 */
    public static boolean matches(ItemStack requested, ItemStack stored) {
        if (isOwnTool(requested) || isOwnTool(stored)) {
            return ItemStack.isSameItem(requested, stored)
                    && requested.getDamageValue() == stored.getDamageValue();
        }
        return ItemStack.isSameItemSameComponents(requested, stored);
    }

    private static boolean isOwnTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(CreateHandMade.MODID);
    }
}
