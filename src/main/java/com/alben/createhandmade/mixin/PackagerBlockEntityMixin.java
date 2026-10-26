package com.alben.createhandmade.mixin;

import com.alben.createhandmade.logistics.ToolStackMatcher;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让仓储发报机（仓储管理员）能打包"带易变组件"的本模组工具（注液枪里的液体、研钵的加工进度等）。
 *
 * <p>Create 的 {@code PackagerBlockEntity#attemptToSend} 用
 * {@link ItemStack#isSameItemSameComponents} 逐件比对库存；而请求是按"快照时刻的组件"发出的，
 * 一旦工具状态在快照之后变了（吸/倒液体、开始加工），比对就失败，表现为"待打包栏闪一下消失"。</p>
 *
 * <p>这里只对<b>本模组命名空间</b>的物品放宽：比较物品 + 耐久值，忽略其它组件；
 * 其它模组的物品仍然走原版精确比对，不受影响。</p>
 */
@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin {

    @Redirect(
            method = "attemptToSend",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private static boolean createHandMade$matchLenientForOwnTools(ItemStack requested, ItemStack stored) {
        return ToolStackMatcher.matches(requested, stored);
    }

}
