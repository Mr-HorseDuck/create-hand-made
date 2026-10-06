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

/**
 * 正在使用风箱时，隐藏"另一只手（持有介质的那只）"的原版渲染。
 *
 * <p>风箱渲染器会在**风箱所在那只手**的渲染 pass 里，把介质作为一份"预览副本"
 * 画到风箱附近；如果介质那只手再按原版画一份，玩家就会看到两份介质（"分身"）。</p>
 *
 * <p>关键点：风箱可以拿在主手，也可以拿在副手，所以不能写死要取消哪一只手——
 * 必须根据 {@code getUsedItemHand()} 动态判断"风箱在哪只手、介质在另一只手"，
 * 然后取消**介质那只手**的渲染。</p>
 */
@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class BellowsHandRenderHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 必须正在使用风箱
        if (!player.isUsingItem()) return;
        ItemStack used = player.getUseItem();
        if (!(used.getItem() instanceof BellowsItem)) return;

        // 风箱正在被哪只手使用？介质在另一只手。
        InteractionHand bellowsHand = player.getUsedItemHand();
        InteractionHand mediaHand = (bellowsHand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;

        // 只取消"介质那只手"的原版渲染
        if (event.getHand() != mediaHand) return;

        // 介质必须存在且是合法介质
        ItemStack media = player.getItemInHand(mediaHand);
        if (media.isEmpty()) return;
        if (BellowsMediaRegistry.resolve(media) == null) return;

        event.setCanceled(true);
    }
}