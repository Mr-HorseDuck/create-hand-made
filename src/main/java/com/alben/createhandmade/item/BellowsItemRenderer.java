package com.alben.createhandmade.item;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BellowsItemRenderer extends CustomRenderedItemModelRenderer {

    private static final int CHARGE_TICKS = 10;

    /** 副手最终位置（主手物品局部空间） */
    private static final float TARGET_X = -0.5f;
    private static final float TARGET_Y = 0.5f;
    private static final float TARGET_Z = 0.1f;

    /** 副手起始位置（近似玩家屏幕右下角） */
    private static final float START_X = 0f;
    private static final float START_Y = 0f;
    private static final float START_Z = 1f;

    private static final float TARGET_SCALE = 0.2f;
    private static final float START_SCALE = 1f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms,
                          MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        // ===== 判断是否"正在使用这一手的风箱 + 另一只手放着合法介质" =====
        boolean showOffhand = false;
        float progress = 0f;
        ItemStack off = ItemStack.EMPTY;

        if (firstPerson && player != null && player.isUsingItem()) {
            // ★ 本次渲染的是"哪一只手"：leftHand = 画在左臂上；主手是否为左臂由主手设置决定
            InteractionHand myHand = ((player.getMainArm() == HumanoidArm.LEFT) == leftHand)
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

            if (player.getUsedItemHand() == myHand
                    && player.getItemInHand(myHand).getItem() instanceof BellowsItem) {
                // 介质始终放在"另一只手"
                off = player.getItemInHand(myHand == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                FanProcessingType resolved = off.isEmpty()
                        ? null
                        : BellowsMediaRegistry.resolve(off);
                if (resolved != null) {
                    showOffhand = true;
                    progress = Math.min(1f,
                            player.getTicksUsingItem() / (float) CHARGE_TICKS);
                }
            }
        }

        ms.pushPose();

        // ===== 1. 先画主手风箱本体 =====
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());

        // ===== 2. 后画副手介质（覆盖在风箱上方） =====
        if (showOffhand) {
            int modifier = leftHand ? -1 : 1;

            // smoothstep 缓动
            float eased = progress * progress * (3f - 2f * progress);

            // ★ 介质位移镜像（实测定稿）：X 不镜像、Y 不镜像、Z 镜像
            float startX = START_X;
            float targetX = TARGET_X;
            float startY = START_Y;
            float targetY = TARGET_Y;
            float startZ = START_Z * modifier;
            float targetZ = TARGET_Z * modifier;

            float tx = startX + (targetX - startX) * eased;
            float ty = startY + (targetY - startY) * eased;
            float tz = startZ + (targetZ - startZ) * eased;
            float sc = START_SCALE + (TARGET_SCALE - START_SCALE) * eased;

            ms.pushPose();
            ms.translate(tx, ty, tz);
            ms.scale(sc, sc, sc);

            // ★ 介质朝向（实测定稿）：YP 90°、XP 45° 均乘 modifier；ZP 为 0°，无需旋转
            ms.mulPose(Axis.YP.rotationDegrees(90f * modifier));
            ms.mulPose(Axis.XP.rotationDegrees(45f * modifier));

            // 末端额外的 Y 轴旋转（实测定稿：仅左手追加 180°，右手不加）
            float endY = leftHand ? 180f : 0f;
            if (endY != 0f) {
                ms.mulPose(Axis.YP.rotationDegrees(endY));
            }

            itemRenderer.renderStatic(off, ItemDisplayContext.NONE,
                    light, overlay, ms, buffer, player.level(), 0);

            ms.popPose();
        }

        ms.popPose();
    }
}