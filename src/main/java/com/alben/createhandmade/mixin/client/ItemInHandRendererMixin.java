package com.alben.createhandmade.mixin.client;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.HandSawItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏「已经由自定义物品模型画过预览」的那只手的原版渲染。
 *
 * <p>目前覆盖两个工具：</p>
 * <ul>
 *   <li><b>手锯</b>：{@link com.alben.createhandmade.client.HandSawItemModel} 在主手 pass 里把副手原料
 *       画到锯子附近；副手再按原版画一份就会「分身」。</li>
 *   <li><b>风箱</b>：{@link com.alben.createhandmade.client.BellowsItemModel} 在风箱那只手的 pass 里
 *       把副手介质画到风箱前方；同理要取消介质那只手的原版渲染。</li>
 * </ul>
 *
 * <p>取消条件与两个模型里画预览的条件严格对应，且都必须带「那只手确实有物品」这一条：
 * 原版 {@code ItemInHandRenderer.renderArmWithItem} 只在手上<b>没有物品</b>时才画手臂
 * （{@code if (itemStack.isEmpty()) renderPlayerArm(...)}）；副手持物时取消渲染只等于不画那件物品
 * （手臂本来就不画），但如果那只手是空的，取消就会把手臂也藏掉。</p>
 *
 * <p>旧版对应的是 NeoForge 的 {@code RenderHandEvent}（{@code event.setCanceled(true)}）；
 * Fabric 没有等价事件，只能 mixin 到 {@code renderArmWithItem} 的 HEAD（该方法是 private）。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
                    + "Lnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void createHandMade$hidePreviewedHand(
            AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
            float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (createHandMade$shouldHideSawOffhand(player, hand)) {
            ci.cancel();
            return;
        }
        if (createHandMade$shouldHideBellowsMedia(player, hand, itemStack)) {
            ci.cancel();
        }
    }

    /** 手锯：副手 + 主手手持手锯且正在使用它（= 切削中）+ 副手有原料。 */
    private static boolean createHandMade$shouldHideSawOffhand(AbstractClientPlayer player, InteractionHand hand) {
        // 只处理副手
        if (hand != InteractionHand.OFF_HAND) return false;

        // 主手必须是手锯
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return false;

        // 必须正在使用主手的手锯（= 切削中；静止时不取消，副手原料照常显示）
        if (!player.isUsingItem()) return false;
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return false;
        if (player.getUseItem() != player.getMainHandItem()) return false;

        // 副手必须有原料（与模型里绘制预览的条件一致；空手时不能取消，否则会把手臂也藏掉）
        return !player.getOffhandItem().isEmpty();
    }

    /** 风箱：正在使用某只手的风箱 + 本次渲染的是「另一只手」+ 那只手放着合法介质。 */
    private static boolean createHandMade$shouldHideBellowsMedia(AbstractClientPlayer player, InteractionHand hand,
                                                                ItemStack itemStack) {
        if (!player.isUsingItem()) return false;
        if (!(player.getUseItem().getItem() instanceof BellowsItem)) return false;

        // 风箱正在被哪只手使用？介质在另一只手
        InteractionHand bellowsHand = player.getUsedItemHand();
        InteractionHand mediaHand = (bellowsHand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand != mediaHand) return false;

        // 介质必须存在且是合法介质（与渲染器绘制预览的条件严格对应；空手时不能取消，否则会藏掉手臂）
        if (itemStack.isEmpty()) return false;
        return BellowsMediaRegistry.resolve(itemStack) != null;
    }
}
