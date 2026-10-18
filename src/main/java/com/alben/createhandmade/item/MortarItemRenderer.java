package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.contents.MortarContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MortarItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float TILT_DEGREES = 25f;
    private static final float SLAM_PHASE = 0.60f;
    private static final float LIFT_END = SLAM_PHASE - 0.15f;
    private static final float LEFT_PESTLE_X_CORRECTION = -0.0625f;
    private static final float PESTLE_Y_BASE = 0.0625f;
    private static final float USING_Z_PUSH = 0.1f;
    private static final float CONTENTS_Y_BASE = 0.0f;

    private static final PartialModel MORTAR_PESTLE = pestle("item/mortar_pestle");
    private static final PartialModel CRUSHER_MORTAR_PESTLE = pestle("item/crusher_mortar_pestle");

    private static PartialModel pestle(String path) {
        return PartialModel.of(new ResourceLocation(CreateHandMade.MODID, path));
    }

    private record MortarParams(
            float baseX, float baseY, float baseZ,
            float frontDistance,
            float poundAmplitude,
            float bowlDropFactor,
            float pestleXOffset,
            float pestleSpinDegrees,
            float contentsScale,
            float contentsSpinDegrees,
            float pestleDepthOffset,
            PartialModel pestleModel) {

        float contentsXOffset() {
            return pestleXOffset / 2f;
        }
    }

    /** 研钵（初始值照 1.21 定稿，1.20.1 实测后再调）。 */
    private static final MortarParams MORTAR_PARAMS = new MortarParams(
            -0.90f, 0.0f, 0.0f,
            0.4f,
            0.25f,
            0.5f,
            -0.05f,
            45f,
            0.25f,
            45f,
            0.07f,
            MORTAR_PESTLE);

    /** 碾钵（初始值抄研钵）。 */
    private static final MortarParams CRUSHER_PARAMS = new MortarParams(
            -0.90f, 0.0f, 0.0f,
            0.4f,
            0.25f,
            0.5f,
            -0.05f,
            45f,
            0.25f,
            45f,
            0.07f,
            CRUSHER_MORTAR_PESTLE);

    private static MortarParams paramsFor(ItemStack stack) {
        return isCrusherMortar(stack) ? CRUSHER_PARAMS : MORTAR_PARAMS;
    }

    private static boolean isCrusherMortar(ItemStack stack) {
        return stack.getItem() instanceof CrusherMortarItem;
    }

    private static int lastPoundCountInUse = 0;
    private static int totalPounds = 0;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        MortarParams params = paramsFor(stack);

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean isGui = transformType == ItemDisplayContext.GUI;

        MortarContents contents = MortarContents.fromStack(stack);
        boolean hasContents = contents != null && !contents.stack().isEmpty();

        InteractionHand useHand = leftHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean using = firstPerson && player != null && player.isUsingItem()
                && player.getUsedItemHand() == useHand
                && ItemStack.isSameItemSameTags(player.getUseItem(), stack);

        float windup = 0f;
        float pound = 0f;
        int poundCount = 0;

        if (using) {
            boolean crusher = isCrusherMortar(stack);
            int windupTicks = crusher
                    ? Config.INSTANCE.crusherMortarWindupTicks.get()
                    : Config.INSTANCE.mortarWindupTicks.get();
            int cycleTicks = Math.max(1, crusher
                    ? Config.INSTANCE.crusherMortarPoundCycleTicks.get()
                    : Config.INSTANCE.mortarPoundCycleTicks.get());

            int usedTicks = player.getTicksUsingItem();
            windup = windupTicks <= 0 ? 1f : Math.min(1f, usedTicks / (float) windupTicks);

            int poundTicks = Math.max(0, usedTicks - windupTicks);
            poundCount = poundTicks / cycleTicks;
            pound = poundCurve((poundTicks % cycleTicks) / (float) cycleTicks) * windup;

            totalPounds += poundCount >= lastPoundCountInUse
                    ? poundCount - lastPoundCountInUse
                    : poundCount;
            lastPoundCountInUse = poundCount;
        } else {
            lastPoundCountInUse = 0;
        }

        BakedModel pestle = params.pestleModel().get();

        int modifier = leftHand ? -1 : 1;

        float pestleX = modifier * params.pestleXOffset() * windup;
        if (leftHand) {
            pestleX += LEFT_PESTLE_X_CORRECTION * windup;
        }

        ms.pushPose();

        if (firstPerson) {
            float face = windup;

            // ★ 照 1.21：先 ZP 再 YP。pose 是右乘，后写的先应用到模型上，
            //   合成结果 R⁻¹ = Rz(-25°)·Ry(90°)（右手）
            ms.mulPose(Axis.ZP.rotationDegrees(modifier * -TILT_DEGREES * face));
            ms.mulPose(Axis.YP.rotationDegrees(modifier * 90f * face));

            // 位移写在 mulPose 之后，是转正后的视图空间
            ms.translate(modifier * params.baseX() * windup,
                    params.baseY() * windup,
                    params.baseZ() * windup);
            ms.translate(0f, 0f, -(params.frontDistance() + USING_Z_PUSH) * windup);
        }

        if (isGui) {
            renderBowl(itemRenderer, stack, model, ms, buffer, light, overlay);
            renderContents(itemRenderer, contents, hasContents, player, ms, buffer, light, overlay,
                    0.5f, 0, params, modifier * params.contentsXOffset() * windup);
            renderPestle(itemRenderer, stack, pestle, ms, buffer, light, overlay,
                    pestleX, PESTLE_Y_BASE, params.pestleDepthOffset(), 0f);
        } else {
            ms.pushPose();
            ms.translate(0f, -params.poundAmplitude() * params.bowlDropFactor() * pound, 0f);
            renderContents(itemRenderer, contents, hasContents, player, ms, buffer, light, overlay,
                    0.0625f, totalPounds, params, modifier * params.contentsXOffset() * windup);
            renderBowl(itemRenderer, stack, model, ms, buffer, light, overlay);
            ms.popPose();

            renderPestle(itemRenderer, stack, pestle, ms, buffer, light, overlay,
                    pestleX, PESTLE_Y_BASE + params.poundAmplitude() * pound,
                    params.pestleDepthOffset(), params.pestleSpinDegrees() * windup);
        }

        ms.popPose();
    }

    private static void renderBowl(ItemRenderer itemRenderer, ItemStack stack, CustomRenderedItemModel model,
                                  PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());
    }

    private static void renderPestle(ItemRenderer itemRenderer, ItemStack stack, BakedModel pestle,
                                     PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                     float dx, float dy, float dz, float spinDegrees) {
        if (pestle == null) return;

        ms.pushPose();
        ms.translate(dx, dy, dz);
        ms.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay, pestle);
        ms.popPose();
    }

    private static void renderContents(ItemRenderer itemRenderer, MortarContents contents, boolean hasContents,
                                       LocalPlayer player, PoseStack ms, MultiBufferSource buffer,
                                       int light, int overlay, float zOffset, int totalPounds,
                                       MortarParams params, float contentsX) {
        if (!hasContents || player == null) return;

        ms.pushPose();
        ms.translate(contentsX, CONTENTS_Y_BASE, zOffset);
        ms.scale(params.contentsScale(), params.contentsScale(), params.contentsScale());
        ms.translate(0.0f, -0.05f, 0.0f);
        ms.mulPose(Axis.ZP.rotationDegrees(totalPounds * params.contentsSpinDegrees()));
        itemRenderer.renderStatic(contents.stack(), ItemDisplayContext.FIXED,
                light, overlay, ms, buffer, player.level(), 0);
        ms.popPose();
    }

    private static float poundCurve(float phase) {
        if (phase < LIFT_END) {
            float t = phase / LIFT_END;
            return t * t * (3f - 2f * t);
        }
        if (phase < SLAM_PHASE) {
            float t = (phase - LIFT_END) / (SLAM_PHASE - LIFT_END);
            return 1f - t;
        }
        return 0f;
    }
}