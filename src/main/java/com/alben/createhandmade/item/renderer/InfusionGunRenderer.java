package com.alben.createhandmade.item.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.contents.InfusionGunContents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import org.joml.Vector3f;

/**
 * 灌注枪渲染：枪本体模型 + 液体层。
 *
 * <p>液体是物品渲染的一部分（BEWLR）：画在物品自身坐标系里，库存槽、仓储管理员 3D 网格、
 * JEI 都自动跟随。手持 / 掉落物 / 展示框不画液体（见 {@link #isFluidHiddenContext}）。</p>
 *
 * <p>层序“枪压水”：液体先发顶点，z 落在物品两个面之间（±{@link #FLUID_Z}，小于平面 ±0.03125），
 * 枪身不透明像素挡住液体，只有贴图透明处能看见。</p>
 *
 * <p>顶点格式必须与物品本体一致：走 {@link Sheets#translucentItemSheet()}
 * （{@code DefaultVertexFormat.NEW_ENTITY}），补齐 color/uv/overlay/uv2/normal，
 * 否则在 {@code BufferBuilder#beginVertex} 抛 {@code Missing elements in vertex: UV1, Normal}。</p>
 */
public class InfusionGunRenderer extends CustomRenderedItemModelRenderer {

    /** 液体层的 z 偏移，必须落在物品平面厚度（±0.03125）之内。 */
    private static final float FLUID_Z = 0.01F;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        // 1) 液体层必须先画，让随后绘制的枪身覆盖它
        if (!isFluidHiddenContext(transformType))
            renderFluid(stack, ms, buffer, light, overlay);

        // 2) 枪本体
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());
    }

    /**
     * 不显示液体的上下文：第一/第三人称左右手、掉落物（GROUND）、展示框（FIXED）。
     * 其余（GUI 槽位、仓储管理员 3D 网格、JEI）正常显示。
     */
    private static boolean isFluidHiddenContext(ItemDisplayContext transformType) {
        return transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED;
    }

    private static void renderFluid(ItemStack stack, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        FluidStack fluid = InfusionGunItem.getContents(stack).fluid();
        if (fluid.isEmpty())
            return;

        IClientFluidTypeExtensions ext;
        try {
            // 1.20.1：of() 接受 Fluid / FluidType，不接受 FluidStack
            ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        } catch (Throwable t) {
            return;
        }

        ResourceLocation still = ext.getStillTexture(fluid);
        if (still == null)
            return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(still);

        float fill = Math.min(1.0F, fluid.getAmount() / (float) InfusionGunContents.CAPACITY);
        if (fill <= 0.0F)
            return;

        int argb = ext.getTintColor(fluid);
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0)
            alpha = 255;                        // 上游给 0 视为“无 tint”，不要画成透明
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;

        // 纹理坐标：v1 是下边（MC 的 v 轴向下），液体从底部往上长
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float vTop = v1 - (v1 - v0) * fill;

        float x0 = -0.5F;
        float x1 = 0.5F;
        float yBottom = -0.5F;
        float yTop = -0.5F + fill;

        int lightU = light & 0xFFFF;
        int lightV = (light >> 16) & 0xFFFF;
        int overlayU = overlay & 0xFFFF;
        int overlayV = (overlay >> 16) & 0xFFFF;

        VertexConsumer vc = buffer.getBuffer(Sheets.translucentItemSheet());
        PoseStack.Pose pose = ms.last();

        // 正面（+Z）
        quad(vc, pose, x0, yBottom, x1, yTop, FLUID_Z, u0, u1, v1, vTop,
                red, green, blue, alpha, overlayU, overlayV, lightU, lightV, 1.0F);
        // 背面（-Z），UV 水平镜像
        quad(vc, pose, x0, yBottom, x1, yTop, -FLUID_Z, u0, u1, v1, vTop,
                red, green, blue, alpha, overlayU, overlayV, lightU, lightV, -1.0F);
    }

    /**
     * 发一个 quad。1.20.1 的 VertexConsumer 没有 addVertex(Pose, x, y, z) 重载，
     * 这里手动用 pose 变换坐标和法线，再走 addVertex(x, y, z)。
     */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float x0, float yBottom, float x1, float yTop, float z,
                             float u0, float u1, float vBottom, float vTop,
                             int red, int green, int blue, int alpha,
                             int overlayU, int overlayV, int lightU, int lightV,
                             float normalZ) {
        // 法线：先变换到世界空间，再归一化
        Vector3f normal = new Vector3f(0.0F, 0.0F, normalZ);
        normal.mul(pose.normal());
        normal.normalize();

        if (normalZ >= 0) {
            putVertex(vc, pose, x0, yBottom, z, u0, vBottom, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x1, yBottom, z, u1, vBottom, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x1, yTop, z, u1, vTop, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x0, yTop, z, u0, vTop, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
        } else {
            putVertex(vc, pose, x0, yBottom, z, u0, vBottom, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x0, yTop, z, u0, vTop, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x1, yTop, z, u1, vTop, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
            putVertex(vc, pose, x1, yBottom, z, u1, vBottom, red, green, blue, alpha, overlayU, overlayV, lightU, lightV, normal);
        }
    }

    private static void putVertex(VertexConsumer vc, PoseStack.Pose pose,
                              float x, float y, float z,
                              float u, float v,
                              int red, int green, int blue, int alpha,
                              int overlayU, int overlayV, int lightU, int lightV,
                              Vector3f normal) {
    // 1.20.1：VertexConsumer 的顶点起点是 vertex(...)，不是 addVertex(...)
    // Matrix4f 变换 Vector3f 用 transformPosition(...)，不是 mul(...)
    Vector3f pos = new Vector3f(x, y, z);
    pose.pose().transformPosition(pos);

    vc.vertex(pos.x(), pos.y(), pos.z())
            .color(red, green, blue, alpha)
            .uv(u, v)
            .overlayCoords(overlayU, overlayV)
            .uv2(lightU, lightV)
            .normal(normal.x(), normal.y(), normal.z())
            .endVertex();
}
}