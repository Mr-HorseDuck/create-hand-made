package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.BellowsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class BellowsHandRenderHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // 只处理副手
        if (event.getHand() != InteractionHand.OFF_HAND) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 主手必须是风箱
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof BellowsItem)) return;

        // 必须正在蓄力主手风箱
        if (!player.isUsingItem()) return;
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
        if (player.getUseItem() != main) return;

        // 副手必须是合法介质
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;
        if (BellowsMediaRegistry.resolve(off) == null) return;

        // ★ 取消原副手渲染，改由 BellowsItemRenderer 在主手事件里画
        event.setCanceled(true);
    }
}