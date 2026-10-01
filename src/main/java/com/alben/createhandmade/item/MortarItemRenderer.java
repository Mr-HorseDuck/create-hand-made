package com.alben.createhandmade.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MortarItemRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean isGui = transformType == ItemDisplayContext.GUI;

        // ★ 1.20.1：用 NBT 读取，不再用 DataComponentType
        MortarContents contents = MortarContents.fromStack(stack);
        boolean hasContents = contents != null && !contents.stack().isEmpty();

        ms.pushPose();

        // ---- 第一人称：整体往前推，让研钵离玩家远一些 ----
        if (firstPerson) {
            ms.translate(0.0f, 0.0f, -0.25f);
        }

        if (isGui) {
            // ===== GUI：先研钵，后物品 → 物品显示在研钵之上 =====
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                    model.getOriginalModel());

            if (hasContents) {
                ms.pushPose();
                ms.translate(0.0f, 0.125f, 0.5f);
                ms.scale(0.5f, 0.5f, 0.5f);
                ms.translate(0.0f, -0.05f, 0.0f);

                if (player != null) {
                    itemRenderer.renderStatic(contents.stack(), ItemDisplayContext.FIXED,
                            light, overlay, ms, buffer, player.level(), 0);
                }
                ms.popPose();
            }
        } else {
            // ===== 世界 / 第一人称：先物品，后研钵 → 形成"在碗里"的效果 =====
            if (hasContents) {
                ms.pushPose();
                ms.translate(0.0f, 0.125f, 0.0625f);
                ms.scale(0.5f, 0.5f, 0.5f);
                ms.translate(0.0f, -0.05f, 0.0f);

                if (player != null) {
                    itemRenderer.renderStatic(contents.stack(), ItemDisplayContext.FIXED,
                            light, overlay, ms, buffer, player.level(), 0);
                }
                ms.popPose();
            }

            itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                    model.getOriginalModel());
        }

        ms.popPose();
    }
}