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

@OnlyIn(Dist.CLIENT)
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    private static final int TEXTURE_SIZE = 16;

    /**
     * 注入<b>5 参数</b>重载（不是 3 参数）。
     *
     * <p>原因（T10 排查结论）：`GuiGraphics` 有两个重载，而 3 参数那个只是把
     * {@code text = null} 转调 5 参数版本 ——</p>
     * <pre>
     * renderItemDecorations(Font, ItemStack, int, int)            // aconst_null → 转调下面那个
     * renderItemDecorations(Font, ItemStack, int, int, String)    // 真正画数量/耐久的地方
     * </pre>
     * <p>快捷栏 HUD 走 3 参数（所以一直能显示液体 ✓），而物品栏/容器槽位
     * （{@code AbstractContainerScreen#renderSlot}）会带数量字符串<b>直接</b>调 5 参数 ⇒
     * 原来只注入 3 参数时物品栏里根本不触发 ✗。改成注入 5 参数即可两头都覆盖
     * （3 参数的调用会转调进来），且不会重复绘制。</p>
     */
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

        // 开启混合，让半透明水叠加正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = self.pose().last().pose();

        // ★ 关键：用流体的 tint 给纹理染色
        setGLColorFromInt(tintColor);

        // 绘制 tiled sprite（这里只画一个 16x16 的 tile，按液面高度裁剪）
        drawTiledSprite(matrix, x, y, 16, fluidHeight, sprite);

        // 恢复
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** 抄自 JEI FluidTankRenderer */
    private static void setGLColorFromInt(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255F;
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    /**
     * 从底部向上画 fluidHeight 像素的液体。
     * x, y 是物品图标左上角。
     */
    private static void drawTiledSprite(Matrix4f matrix, int x, int y, int width, int fluidHeight,
                                        TextureAtlasSprite sprite) {
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        // 单个 16x16 的 tile，裁剪底部 fluidHeight
        int xTileCount = width / TEXTURE_SIZE;         // = 1
        int xRemainder = width - (xTileCount * TEXTURE_SIZE);  // = 0
        int yTileCount = fluidHeight / TEXTURE_SIZE;   // = 0
        int yRemainder = fluidHeight - (yTileCount * TEXTURE_SIZE); // = fluidHeight

        int yStart = y + 16;  // 物品图标底部

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

    /** 抄自 JEI，用 quad 精确绘制纹理并裁剪 */
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