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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    private static final int TEXTURE_SIZE = 16;

    @Inject(
            method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD")
    )
    private void createHandMade$fluidUnderlay(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (!(stack.getItem() instanceof InfusionGunItem)) return;

        FluidStack fluid = InfusionGunItem.getContents(stack).fluid();
        if (fluid.isEmpty()) return;

        IClientFluidTypeExtensions ext;
        try {
            ext = IClientFluidTypeExtensions.of(fluid.getFluid().getFluidType());
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

        Matrix4f matrix = self.pose().last().pose();

        setGLColorFromInt(tintColor);

        drawTiledSprite(matrix, x, y, 16, fluidHeight, sprite);

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static void setGLColorFromInt(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255F;
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

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
                    drawTextureWithMasking(matrix, dx, dy, sprite, maskTop, maskRight, 100);
                }
            }
        }
    }

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

        // ★ 1.20.1：Tesselator.getInstance().getBuilder() + buffer.begin(...)
        //   替代 1.21 的 tesselator.begin(...)
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // ★ 1.20.1：.uv(...) + .endVertex()
        //   替代 1.20.5+ 的 .setUv(...)（无 endVertex）
        buffer.vertex(matrix, x, y + 16, zLevel).uv(uMin, vMax).endVertex();
        buffer.vertex(matrix, x + 16 - maskRight, y + 16, zLevel).uv(uMax, vMax).endVertex();
        buffer.vertex(matrix, x + 16 - maskRight, y + maskTop, zLevel).uv(uMax, vMin).endVertex();
        buffer.vertex(matrix, x, y + maskTop, zLevel).uv(uMin, vMin).endVertex();

        // ★ 1.20.1：buffer.end() 返回 RenderedBuffer
        //   替代 1.20.5+ 的 buffer.buildOrThrow()
        BufferUploader.drawWithShader(buffer.end());
    }
}