package com.alben.createhandmade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 「把任意 {@link ItemStack} 当成静态物品画出来」的 {@link SpecialModelRenderer}。
 *
 * <p>手锯的副手原料预览、风箱的副手介质预览都用它：{@code submit} 时现搭一个
 * {@link ItemStackRenderState}（{@code FIXED} 变换），再交给同一个 {@code SubmitNodeCollector} 提交。
 * 这样任意物品（含附魔光效、方块物品）都能正确渲染，且与物品栏里的外观完全一致。</p>
 *
 * <h3>坐标注意</h3>
 * <p>{@code FIXED} 变换自带收尾的 {@code T(-0.5)}，所以调用方的 {@code localTransform}
 * <b>不要</b>再补尾部 {@code T(-0.5)}（详见 {@link HandSawItemModel} / {@link BellowsItemModel}
 * 类注释里的共轭换算）。</p>
 */
final class FixedItemRenderer implements SpecialModelRenderer<ItemStack> {

    static final FixedItemRenderer INSTANCE = new FixedItemRenderer();

    private FixedItemRenderer() {
    }

    @Override
    public void submit(@Nullable ItemStack argument, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        if (argument == null || argument.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, argument,
                ItemDisplayContext.FIXED, minecraft.level, null, 0);
        state.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // 预览只用于显示，不参与物品实体的包围盒
    }

    @Override
    public ItemStack extractArgument(ItemStack stack) {
        return ItemStack.EMPTY;
    }
}
