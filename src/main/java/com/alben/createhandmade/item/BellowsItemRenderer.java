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

        // ===== 判断是否"使用主手风箱 + 副手合法介质" =====
        boolean showOffhand = false;
        float progress = 0f;
        ItemStack off = ItemStack.EMPTY;

        if (firstPerson && player != null && player.isUsingItem()) {
            if (player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    && player.getMainHandItem().getItem() instanceof BellowsItem) {
                off = player.getOffhandItem();
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

            float startX = START_X * modifier;
            float targetX = TARGET_X * modifier;

            float tx = startX + (targetX - startX) * eased;
            float ty = START_Y + (TARGET_Y - START_Y) * eased;
            float tz = START_Z + (TARGET_Z - START_Z) * eased;
            float sc = START_SCALE + (TARGET_SCALE - START_SCALE) * eased;

            ms.pushPose();
            ms.translate(tx, ty, tz);
            ms.scale(sc, sc, sc);

            // ★ 让副手物品正对玩家：绕 Y 轴旋转
            ms.mulPose(Axis.YP.rotationDegrees(90));
            ms.mulPose(Axis.XP.rotationDegrees(45));

            itemRenderer.renderStatic(off, ItemDisplayContext.NONE,
                    light, overlay, ms, buffer, player.level(), 0);

            ms.popPose();
        }

        ms.popPose();
    }
}