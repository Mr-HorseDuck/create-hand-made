package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 手部渲染相关的三个客户端处理器：风箱 / 手锯的「副手原版渲染取消」，灌注枪的液体条装饰。
 */
public final class HandRenderHandlers {

    private HandRenderHandlers() {}

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
    public static class Bellows {

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
    public static class HandSaw {

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

    /** 灌注枪物品图标上的液体存量条。 */
    public static class InfusionGunDecorator implements IItemDecorator {

        @Override
        public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
            if (!(stack.getItem() instanceof InfusionGunItem)) return false;
            InfusionGunContents contents = InfusionGunItem.getContents(stack);
            if (contents.isEmpty()) return false;

            float ratio = (float) contents.fluid().getAmount() / InfusionGunContents.CAPACITY;

            int barHeight = 2;
            int barX = xOffset + 2;
            int barY = yOffset + 13;
            int barWidth = 12;

            // ★ 正确方式：通过 IClientFluidTypeExtensions 获取客户端渲染用的 tint color
            int liquidColor = IClientFluidTypeExtensions.of(contents.fluid().getFluid())
                    .getTintColor(contents.fluid());

            // 兜底：如果某些流体没有定义 tint，可能会返回 0xFFFFFF（白色）
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

            int filledWidth = (int) (barWidth * ratio);
            int argb = (0xCC << 24) | (liquidColor & 0xFFFFFF);
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, argb);

            return true;
        }
    }
}
