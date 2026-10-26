package com.alben.createhandmade.mixin;

import com.alben.createhandmade.item.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给注液枪画"液体层"（覆盖在物品图标上，但压在数量角标之下）。
 *
 * <h3>层序</h3>
 * <pre>
 * 数量数字  &gt;  灌注枪本体  &gt;  液体层
 * </pre>
 * 液体层如果真画在图标<b>下面</b>，会被不透明的枪身纹理完全挡住（实测"哪里都不显示"），
 * 所以实际是"先物品、再液体（半透明可见）、数量最后"；并且绘制液体时开启深度测试，
 * 让已经画好的数量数字（z 更高）不会被液体覆盖。
 *
 * <h3>两个已踩过的坑</h3>
 * <ul>
 *   <li>注入 {@code renderItem} HEAD（物品之前）⇒ 液体被不透明图标挡住，全界面不可见；</li>
 *   <li>结束时 {@code disableBlend()} ⇒ 打断调用方后续文字绘制，仓储管理员里的数量数字消失。
 *       现在结束时恢复为<b>开启</b>混合。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    private static final int TEXTURE_SIZE = 16;

    /** 与物品图标一致的 z（原版 renderItem 内部用 100）。 */
    private static final float ITEM_Z = 100.0F;

    @Inject(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD")
    )
    private void createHandMade$fluidUnderlay(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (!(stack.getItem() instanceof InfusionGunItem)) return;

        FluidStack fluid = InfusionGunItem.getContents(stack).fluid();
        if (fluid.isEmpty()) return;

        IClientFluidTypeExtensions ext;
        try {
            ext = IClientFluidTypeExtensions.of(fluid.getFluidType());
        } catch (Throwable t) {
            return;
        }

        ResourceLocation still = ext.getStillTexture(fluid);
        if (still == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(still);

        int tintColor = ext.getTintColor(fluid);
        if (tintColor == -1) tintColor = 0xFFFFFFFF;

        int amount = fluid.getAmount();
        int capacity = InfusionGunContents.CAPACITY;
        int fluidHeight = (int) Math.ceil(16.0 * amount / capacity);
        if (fluidHeight <= 0) return;

        GuiGraphics self = (GuiGraphics) (Object) this;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();          // 保护已绘制的高 z 内容（数量数字）
        setGLColorFromInt(tintColor);

        Matrix4f matrix = self.pose().last().pose();
        drawTiledSprite(matrix, x, y, 16, fluidHeight, sprite);

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();              // 不要关混合，调用方还要画文字
    }

    /** 抄自 JEI FluidTankRenderer */
    private static void setGLColorFromInt(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255F;
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    /** 从底部向上画 fluidHeight 像素的液体；x, y 是物品图标左上角。 */
    private static void drawTiledSprite(Matrix4f matrix, int x, int y, int width, int fluidHeight,
                                        TextureAtlasSprite sprite) {
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        int xTileCount = width / TEXTURE_SIZE;
        int xRemainder = width - (xTileCount * TEXTURE_SIZE);
        int yTileCount = fluidHeight / TEXTURE_SIZE;
        int yRemainder = fluidHeight - (yTileCount * TEXTURE_SIZE);

        int yStart = y + 16;

        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int w = (xTile == xTileCount) ? xRemainder : TEXTURE_SIZE;
                int h = (yTile == yTileCount) ? yRemainder : TEXTURE_SIZE;
                int dx = x + (xTile * TEXTURE_SIZE);
                int dy = yStart - ((yTile + 1) * TEXTURE_SIZE);

                if (w > 0 && h > 0) {
                    int maskTop = TEXTURE_SIZE - h;
                    int maskRight = TEXTURE_SIZE - w;
                    drawTextureWithMasking(matrix, dx, dy, sprite, maskTop, maskRight, ITEM_Z);
                }
            }
        }
    }

    /** 抄自 JEI，用 quad 精确绘制纹理并裁剪。 */
    private static void drawTextureWithMasking(Matrix4f matrix, float x, float y,
                                               TextureAtlasSprite sprite,
                                               int maskTop, int maskRight, float zLevel) {
        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();

        uMax = uMax - (maskRight / 16F * (uMax - uMin));
        vMax = vMax - (maskTop / 16F * (vMax - vMin));

        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y + 16, zLevel).setUv(uMin, vMax);
        buffer.addVertex(matrix, x + 16 - maskRight, y + 16, zLevel).setUv(uMax, vMax);
        buffer.addVertex(matrix, x + 16 - maskRight, y + maskTop, zLevel).setUv(uMax, vMin);
        buffer.addVertex(matrix, x, y + maskTop, zLevel).setUv(uMin, vMin);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
