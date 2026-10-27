package com.alben.createhandmade.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 灌注枪渲染：枪本体模型 + <b>液体层</b>。
 *
 * <h3>为什么液体画在这里，而不是 GUI 上</h3>
 * <p>早期实现是用 {@code GuiGraphics} 混入在物品图标上叠一层 2D 液体，靠屏幕坐标对齐。
 * 这条路必然失败：只要宿主界面用 3D 方式画物品（Create 仓储管理员用 {@code GuiGameElement}，
 * 物品是带旋转/透视的 3D 渲染），2D quad 的坐标就跟物品对不上——要么盖住物品和数量数字，
 * 要么只能"不画"。</p>
 *
 * <p>现在液体是<b>物品渲染的一部分</b>（BEWLR，经 {@code initializeClient} 注册）：画在物品自身
 * 坐标系里，于是库存槽、掉落物、JEI、以及仓储管理员里的 3D 网格全都会自动跟着物品走，
 * 一个界面都不用特判。数量角标由调用方在物品之后绘制，天然压在液体之上。</p>
 *
 * <p><b>例外：手持与"平铺/悬浮"展示不画液体。</b>第一/第三人称、掉落物（{@code GROUND}）、
 * 展示框（{@code FIXED}）里枪是斜着贴向镜头的，"水在枪背后"会变成一块浮着的半透明方块，很出戏；
 * 这些情况只显示枪本体（见 {@code isFluidHiddenContext}）。</p>
 *
 * <h3>坐标系</h3>
 * <p>Create 的 {@link CustomRenderedItemModelRenderer} 在调用 {@link #render} 前已
 * {@code translate(0.5, 0.5, 0.5)}，而 {@code ItemRenderer#render} 内部又会
 * {@code translate(-0.5, -0.5, -0.5)}，两者相消 ⇒ 本体贴图落在以原点为中心的
 * {@code [-0.5, 0.5]²} 平面上（平面本身位于 z≈±0.03，即 16 像素图里 7.5/8.5 像素处）。
 * 液体层就画在这个平面上、略向两侧偏移的位置。</p>
 *
 * <p><b>层序是"枪压水"</b>：液体先发顶点、且 z 落在物品两个面之间（{@code ±FLUID_Z}，
 * 小于平面本身的 ±0.03125），于是枪身的不透明像素天然把液体挡住，只有贴图透明的地方
 * （枪形轮廓之外的背景、以及握把缺口）能看见液体。反过来把液体放在最前面（z 更大）
 * 就会变成"水盖住枪"。</p>
 *
 * <p><b>顶点格式必须与物品本体一致</b>：物品在 GUI 里走 {@code Sheets.translucentItemSheet()}
 * （{@code DefaultVertexFormat.NEW_ENTITY}，含 uv1 overlay 与法线）。液体 quad 走同一个 RenderType
 * 并补齐 color/uv/uv1/uv2/normal 全部元素，否则会在 {@code BufferBuilder#beginVertex} 处抛
 * {@code IllegalStateException: Missing elements in vertex: UV1, Normal}（打开仓储管理员即崩）。</p>
 */
public class InfusionGunRenderer extends CustomRenderedItemModelRenderer {

    /**
     * 液体层的 z 偏移，必须是"物品平面厚度之内"的值：
     * 物品两个面在 ±0.03125（16 像素贴图里的 7.5 / 8.5 像素），这里取 ±0.01，
     * 既不会和枪面 z-fighting，也保证枪面在前、液体在后（枪压水）。
     */
    private static final float FLUID_Z = 0.01F;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();

        // 1) 液体层 —— 必须先画，让随后绘制的枪身覆盖它（同批次内按顶点顺序混合）
        //    手持与"平铺/悬浮"展示（掉落物、展示框）不画：那些上下文里枪是斜着贴向镜头的，
        //    "水在枪背后"会变成一块浮在手上的半透明方块，很出戏；这些情况只显示枪本体。
        if (!isFluidHiddenContext(transformType))
            renderFluid(stack, ms, buffer, light, overlay);

        // 2) 枪本体（与研钵同样的调用方式，走同一条烘焙/渲染路径）
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());
    }

    /**
     * 不显示液体层的上下文。
     *
     * <ul>
     *   <li>第一/第三人称左右手：手持时枪斜贴镜头，液体背景像"浮在手上的水方块"；</li>
     *   <li>{@code GROUND}：掉落在地上；</li>
     *   <li>{@code FIXED}：物品展示框里。</li>
     * </ul>
     *
     * <p>其余上下文（GUI 槽位、仓储管理员的 3D 网格、JEI 等）正常显示液体。</p>
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
            ext = IClientFluidTypeExtensions.of(fluid.getFluidType());
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
            alpha = 255;                        // 上游给 0 时视为"无 tint"，不要画成透明
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;

        // 纹理坐标：v1 是下边（MC 的 v 轴向下），液体从底部往上长
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float vTop = v1 - (v1 - v0) * fill;

        // 物品平面：x, y ∈ [-0.5, 0.5]，液体从底边往上占 fill 比例
        float x0 = -0.5F;
        float x1 = 0.5F;
        float yBottom = -0.5F;
        float yTop = -0.5F + fill;

        int lightU = light & 0xFFFF;
        int lightV = (light >> 16) & 0xFFFF;
        int overlayU = overlay & 0xFFFF;
        int overlayV = (overlay >> 16) & 0xFFFF;

        // 必须与物品本体用同一个 RenderType：它走的是 DefaultVertexFormat.NEW_ENTITY
        // （position/color/uv/**uv1 overlay**/uv2 lightmap/**normal**）。
        // 之前这里用了 Sheets.translucentCullBlockSheet()（块格式，无 overlay/法线），
        // 而 GUI 里物品的顶点是 NEW_ENTITY 格式，混在一起会让顶点元素不全 →
        // IllegalStateException: Missing elements in vertex: UV1, Normal（打开仓储管理员即崩溃）。
        VertexConsumer vc = buffer.getBuffer(Sheets.translucentItemSheet());
        PoseStack.Pose pose = ms.last();

        // 正面（+Z）：从 +Z 看逆时针
        createHandMade$quad(vc, pose, x0, yBottom, x1, yTop, FLUID_Z, u0, u1, v1, vTop, red, green, blue, alpha,
                overlayU, overlayV, lightU, lightV, 1.0F);
        // 背面（-Z）：物品被转过来时也能看到，UV 水平镜像
        createHandMade$quad(vc, pose, x0, yBottom, x1, yTop, -FLUID_Z, u0, u1, v1, vTop, red, green, blue, alpha,
                overlayU, overlayV, lightU, lightV, -1.0F);
    }

    /**
     * 发一个 quad。{@code normalZ} 为 +1 时朝 +Z（顶点按从 +Z 看的逆时针顺序），
     * 为 -1 时朝 -Z（顺序反转，看到的是同一张贴图的镜像）。
     */
    private static void createHandMade$quad(VertexConsumer vc, PoseStack.Pose pose,
                                            float x0, float yBottom, float x1, float yTop, float z,
                                            float u0, float u1, float vBottom, float vTop,
                                            int red, int green, int blue, int alpha,
                                            int overlayU, int overlayV, int lightU, int lightV,
                                            float normalZ) {
        if (normalZ >= 0) {
            vc.addVertex(pose, x0, yBottom, z).setColor(red, green, blue, alpha).setUv(u0, vBottom)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, x1, yBottom, z).setColor(red, green, blue, alpha).setUv(u1, vBottom)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, x1, yTop, z).setColor(red, green, blue, alpha).setUv(u1, vTop)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, 1.0F);
            vc.addVertex(pose, x0, yTop, z).setColor(red, green, blue, alpha).setUv(u0, vTop)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, 1.0F);
        } else {
            vc.addVertex(pose, x0, yBottom, z).setColor(red, green, blue, alpha).setUv(u0, vBottom)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, -1.0F);
            vc.addVertex(pose, x0, yTop, z).setColor(red, green, blue, alpha).setUv(u0, vTop)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, -1.0F);
            vc.addVertex(pose, x1, yTop, z).setColor(red, green, blue, alpha).setUv(u1, vTop)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, -1.0F);
            vc.addVertex(pose, x1, yBottom, z).setColor(red, green, blue, alpha).setUv(u1, vBottom)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(pose, 0.0F, 0.0F, -1.0F);
        }
    }
}
