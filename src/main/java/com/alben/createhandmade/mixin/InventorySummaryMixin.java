package com.alben.createhandmade.mixin;

import com.alben.createhandmade.logistics.ToolStackMatcher;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让物流库存在"计数 / 擦除 / 查询"时也按放宽规则匹配本模组工具。
 *
 * <p>光改 {@code PackagerBlockEntity#attemptToSend} 不够：请求流程会先在
 * {@link InventorySummary} 里按确切栈（含易变组件）统计与抽取，
 * 那里对不上就直接没货可发，表现仍是"待打包栏闪一下消失"。</p>
 *
 * <p>这三处是 javap 实测出的全部比较点：
 * {@code add(ItemStack,int)}、{@code erase(ItemStack)}、{@code getCountOf(ItemStack)}。
 * 判定逻辑统一委托给 {@link ToolStackMatcher}，避免多处规则漂移。</p>
 */
@Mixin(InventorySummary.class)
public class InventorySummaryMixin {

    @Redirect(
            method = "add(Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean createHandMade$addLenient(ItemStack a, ItemStack b) {
        return ToolStackMatcher.matches(a, b);
    }

    @Redirect(
            method = "erase(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean createHandMade$eraseLenient(ItemStack a, ItemStack b) {
        return ToolStackMatcher.matches(a, b);
    }

    @Redirect(
            method = "getCountOf(Lnet/minecraft/world/item/ItemStack;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean createHandMade$countLenient(ItemStack a, ItemStack b) {
        return ToolStackMatcher.matches(a, b);
    }
}
