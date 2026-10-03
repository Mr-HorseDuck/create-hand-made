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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HandSawItemRenderer extends CustomRenderedItemModelRenderer {

    /** 砍树姿态：从 0 到 1 的伸出过程所需 tick */
    private static final float FELL_WINDUP = 5f;

    /** ★ 切削起手阶段：位移 0.5 秒 */
    private static final float CUT_MOVE_TICKS = 10f;
    /** ★ 切削振动阶段：1 秒 */
    private static final float CUT_VIBRATE_TICKS = 20f;

    /** 每个玩家"开始砍树姿态"时的 tickCount，用于做渐进 */
    private static final Map<UUID, Integer> FELL_START_TICK = new HashMap<>();

    // ================= 副手物品：独立玩家空间坐标 =================
    private static final float OFFHAND_START_X = -0.5f;
    private static final float OFFHAND_START_Y = -0.55f;
    private static final float OFFHAND_START_Z = 0.15f;

    private static final float OFFHAND_TARGET_X = -0.4f;
    private static final float OFFHAND_TARGET_Y = 0.12f;
    private static final float OFFHAND_TARGET_Z = 0.6f;

    private static final float OFFHAND_SCALE = 0.4f;
    private static final float OFFHAND_YAW = 90f;
    /** 振动阶段副手物品的微颤幅度 */
    private static final float OFFHAND_SHAKE = 0.02f;

    // ================= 锯子目标位置 =================
    private static final float SAW_TARGET_X = -1f;
    private static final float SAW_TARGET_Y = 0.45f;
    private static final float SAW_TARGET_Z = 0.45f;
    /** 振动阶段锯子的摆动幅度 */
    private static final float SAW_SWING = 0.1f;
    /** 起手阶段完成后锯子沿 YP 轴旋转的角度 */
    private static final float SAW_ROTATE_Y = 90f;
    private static final float SAW_ROTATE_Z = 90f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        boolean cutting = false;
        boolean felling = false;
        float moveProgress = 0f;
        float vibrateProgress = 0f;
        float fellProgress = 0f;
        ItemStack offhand = ItemStack.EMPTY;

        if (firstPerson && player != null) {
            boolean rightUsing = player.isUsingItem()
                    && player.getUseItem() == stack
                    && player.getUsedItemHand() == InteractionHand.MAIN_HAND;

            if (rightUsing) {
                cutting = true;
                int usedTicks = player.getTicksUsingItem();

                // ★ 阶段 1：位移进度（0 → 1，持续 CUT_MOVE_TICKS）
                moveProgress = Math.min(1f, usedTicks / CUT_MOVE_TICKS);
                // ★ 阶段 2：振动渐入包络（3 tick 内进入满幅，之后保持满幅）
                float afterMove = Math.max(0f, usedTicks - CUT_MOVE_TICKS);
                vibrateProgress = Math.min(1f, afterMove / 3f);

                offhand = player.getOffhandItem();
            } else {
                boolean leftMouseDown = mc.options.keyAttack.isDown();
                boolean shouldFell = player.isShiftKeyDown()
                        && leftMouseDown
                        && player.getMainHandItem() == stack;

                if (shouldFell) {
                    FELL_START_TICK.putIfAbsent(player.getUUID(), player.tickCount);
                    int startTick = FELL_START_TICK.getOrDefault(player.getUUID(), player.tickCount);
                    fellProgress = Math.min(1f, (player.tickCount - startTick) / FELL_WINDUP);
                    felling = true;
                } else {
                    FELL_START_TICK.remove(player.getUUID());
                }
            }
        }

        ms.pushPose();

        // ================= 1. 副手物品：独立坐标系统 =================
        if (cutting && !offhand.isEmpty()) {
            int modifier = leftHand ? -1 : 1;

            float startX = OFFHAND_START_X * modifier;
            float targetX = OFFHAND_TARGET_X * modifier;

            // ★ 位移用 moveProgress，振动只叠加微颤
            float tx = startX + (targetX - startX) * moveProgress;
            float ty = OFFHAND_START_Y + (OFFHAND_TARGET_Y - OFFHAND_START_Y) * moveProgress;
            float tz = OFFHAND_START_Z + (OFFHAND_TARGET_Z - OFFHAND_START_Z) * moveProgress;

            // ★ 振动阶段才有的微颤
            float shake = (float) Math.sin(player.tickCount ) * OFFHAND_SHAKE * vibrateProgress;
            tx += shake;
            ty += shake;

            ms.pushPose();
            ms.translate(tx, ty, tz);
            ms.scale(OFFHAND_SCALE, OFFHAND_SCALE, OFFHAND_SCALE);
            ms.mulPose(Axis.YP.rotationDegrees(OFFHAND_YAW * modifier));

            itemRenderer.renderStatic(offhand, ItemDisplayContext.FIXED,
                    light, overlay, ms, buffer, player.level(), 0);

            ms.popPose();
        }

        // ================= 2. 锯子姿态动画 =================
        if (felling) {
            int modifier = leftHand ? -1 : 1;
            ms.translate(modifier * 0.1f * fellProgress,
                    -0.15f * fellProgress,
                    -1f * fellProgress);
        } else if (cutting) {
            // ★ 位移阶段：从原位滑到目标
            float sawX = SAW_TARGET_X * moveProgress;
            float sawY = SAW_TARGET_Y * moveProgress;
            float sawZ = SAW_TARGET_Z * moveProgress;

            // ★ 振动阶段：叠加摆动
            float swing = (float) Math.sin(player.tickCount ) * SAW_SWING;
            sawY += swing * vibrateProgress;
            sawZ += swing * vibrateProgress;

            ms.translate(sawX, sawY, sawZ);
            // ★ 起手完成后：沿 YP 轴旋转 90°（用 moveProgress 做平滑过渡）
            int modifier = leftHand ? -1 : 1;
            ms.mulPose(Axis.YP.rotationDegrees(SAW_ROTATE_Y * modifier * moveProgress));
            ms.mulPose(Axis.ZP.rotationDegrees(SAW_ROTATE_Z * modifier * moveProgress));
        }

        // ================= 3. 锯子本体 =================
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());

        ms.popPose();
    }
}