package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.HandSawItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * 手锯切削时隐藏"副手的原版渲染"。
 *
 * <p>原因：手锯渲染器（{@code HandSawItemRenderer}）已经在主手的渲染 pass 里，把副手原料
 * 作为一份"预览副本"画到锯子附近；如果副手再按原版画一份，玩家就会看到两份原料（"分身"）。</p>
 *
 * <p>取消条件与 {@code HandSawItemRenderer} 里画预览的条件严格对应：
 * 副手 + 主手手持手锯且正在使用它（= 切削中）+ 副手有原料。</p>
 *
 * <p>为什么必须带"副手有原料"这一条：原版 {@code ItemInHandRenderer.renderArmWithItem} 只在
 * 手上**没有物品**时才画手臂（{@code if (stack.isEmpty()) renderPlayerArm(...)}）；副手持物时
 * 取消渲染只等于不画那件物品（手臂本来就不画），但如果副手是空的，取消就会把手臂也藏掉。
 * 而我们的预览本来就只在副手非空时绘制，所以两个条件正好吻合。</p>
 *
 * <p>写法与既有的 {@link BellowsHandRenderHandler} 完全一致（同样的 {@code RenderHandEvent}
 * 取消机制）。原版调用点：{@code ItemInHandRenderer.renderHandsWithItems} 里对 OFF_HAND 调用
 * {@code ClientHooks.renderSpecificFirstPersonHand(...)}，事件被取消即跳过该手的
 * {@code renderArmWithItem}。</p>
 */
@EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class HandSawHandRenderHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // 只处理副手
        if (event.getHand() != InteractionHand.OFF_HAND) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 主手必须是手锯
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;

        // 必须正在使用主手的手锯（= 切削中；静止/蓄力前不取消，副手原料照常显示）
        if (!player.isUsingItem()) return;
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
        if (player.getUseItem() != player.getMainHandItem()) return;

        // 副手必须有原料（与渲染器绘制预览的条件一致；空手时不能取消，否则会把手臂也藏掉）
        if (player.getOffhandItem().isEmpty()) return;

        event.setCanceled(true);
    }
}
