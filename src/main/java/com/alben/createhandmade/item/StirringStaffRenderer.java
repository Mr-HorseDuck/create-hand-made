package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
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

/**
 * 搅拌杖第一人称渲染：起手 + 环绕画圈。
 * 照 1.21 思路：不做逆旋转，直接在 item/handheld 的局部坐标系里调。
 *
 * <p>局部轴换算成视图方向（右手）：
 * <ul>
 *   <li>局部 −X = 往前伸（略往下）</li>
 *   <li>局部 +Y = 往上前</li>
 *   <li>局部 +Z = 往左，右手要乘 modifier</li>
 * </ul>
 */
public class StirringStaffRenderer extends CustomRenderedItemModelRenderer {

    // ================= 加工位（起手终点）——1.21 实机定稿 =================

    /** 前伸量（局部 −X = 往前下）。 */
    private static final float POS_X = -0.8f;

    /** 高度（局部 +Y = 往上前）。 */
    private static final float POS_Y = 0.80f;

    /** 左右居中量（乘 modifier）。 */
    private static final float POS_Z = 0.8f;

    // ================= 搅拌圆 =================

    private static final float STIR_CIRCLE_RADIUS = 0.1f;
    private static final float STIR_CIRCLE_TARGET_TICKS = 20f;
    private static final float STIR_CIRCLE_MIN_PERIOD_TICKS = 5f;
    private static final float STIR_CIRCLE_FADE_IN_TICKS = 3f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        boolean using = false;
        float progress = 0f;
        int stirTicks = -1;
        float stirProgress = 0f;
        int processingTicks = 1;

        if (firstPerson && player != null && player.isUsingItem()
                && ItemStack.isSameItemSameTags(player.getUseItem(), stack)) {
            using = true;
            int windupTicks = Math.max(1, Config.INSTANCE.stirringStaffWindupTicks.get());
            processingTicks = Math.max(1, Config.INSTANCE.stirringStaffProcessingTicks.get());

            int usedTicks = player.getTicksUsingItem();
            progress = Math.min(1f, usedTicks / (float) windupTicks);
            stirTicks = usedTicks - windupTicks;
            stirProgress = stirTicks <= 0 ? 0f : Math.min(1f, stirTicks / STIR_CIRCLE_FADE_IN_TICKS);
        }

        ms.pushPose();

        if (using && progress > 0f) {
            int modifier = leftHand ? -1 : 1;

            // 搅拌圆：整数圈/件
            float revolutions = Math.max(1f, Math.round(processingTicks / STIR_CIRCLE_TARGET_TICKS));
            float circlePeriod = Math.max(STIR_CIRCLE_MIN_PERIOD_TICKS, processingTicks / revolutions);
            float circlePhase = (stirTicks % circlePeriod) / circlePeriod;
            float circleAngle = circlePhase * (float) (Math.PI * 2.0);
            float circleX = (float) Math.sin(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress;
            float circleZ = (float) Math.cos(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress * modifier;

            // 位移：1.21 定稿的镜像规则
            ms.translate(POS_X * progress + circleX * (-modifier),
                    POS_Y * progress,
                    modifier * POS_Z * progress + circleZ);

            // 6 次 mulPose：三明治自转 + XP/ZP/YP 收尾
            ms.mulPose(Axis.ZP.rotationDegrees(45f * progress));
            ms.mulPose(Axis.YP.rotationDegrees(-90f * modifier * progress));
            ms.mulPose(Axis.ZP.rotationDegrees(-45f * progress));

            ms.mulPose(Axis.XP.rotationDegrees(-90f * modifier * progress));
            ms.mulPose(Axis.ZP.rotationDegrees(135f * progress));
            ms.mulPose(Axis.YP.rotationDegrees(-90f * modifier * progress));
        }

        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());

        ms.popPose();
    }
}