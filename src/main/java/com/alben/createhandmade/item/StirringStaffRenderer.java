package com.alben.createhandmade.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class StirringStaffRenderer extends CustomRenderedItemModelRenderer {

    /** 起手过渡时长（tick），和 StirringStaffItem.WINDUP_TICKS 保持一致 */
    private static final float WINDUP = 8f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        // 判断玩家是否正在使用这把搅拌杖
        boolean using = false;
        float progress = 0f;
        if (firstPerson && player != null && player.isUsingItem()) {
            ItemStack useItem = player.getUseItem();
            if (useItem == stack) {
                using = true;
                int usedTicks = player.getTicksUsingItem();
                // 起手 8 tick 内从 0 平滑过渡到 1
                progress = Math.min(1f, usedTicks / WINDUP);
            }
        }

        ms.pushPose();

        // 只有使用中才应用搅拌姿势，且随 progress 做插值
        if (using && progress > 0f) {
            int modifier = leftHand ? -1 : 1;

            // 位移：无位移 → 搅拌位置
            ms.translate(modifier * 0.1f * progress,
                    -0.15f * progress,
                    -1f * progress);

            // 旋转：0° → 90°/180°
            ms.mulPose(Axis.XP.rotationDegrees(90 * progress));
            ms.mulPose(Axis.ZP.rotationDegrees(180 * progress));
        }

        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());

        ms.popPose();
    }
}