package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.HandSawItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 手锯切削时隐藏"副手的原版渲染"。
 *
 * <p>原因：手锯渲染器已经在主手的渲染 pass 里把副手原料作为"预览副本"画到锯子附近；
 * 如果副手再按原版画一份，就会看到两份原料（"分身"）。</p>
 *
 * <p>取消条件与 {@code HandSawItemRenderer} 画预览的条件严格对应：
 * 副手 + 主手手持手锯且正在使用它（= 切削中）+ 副手有原料。</p>
 */
@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class HandSawHandRenderHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // 只处理副手
        if (event.getHand() != InteractionHand.OFF_HAND) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 主手必须是手锯
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;

        // 必须正在使用主手的手锯（= 切削中）
        if (!player.isUsingItem()) return;
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
        if (player.getUseItem() != player.getMainHandItem()) return;

        // 副手必须有原料
        if (player.getOffhandItem().isEmpty()) return;

        event.setCanceled(true);
    }
}