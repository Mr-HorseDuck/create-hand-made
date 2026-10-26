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
 * 给注液枪画"液体背景层"。
 *
 * <h3>为什么挂在 {@code renderItem} 的 HEAD（T12 修正）</h3>
 * <p>原来挂在 {@code renderItemDecorations} 的 HEAD，并在物品已经画完之后以
 * {@code zLevel = 100} 覆盖上去 —— 层序变成「物品 → 液体 → 数量」，于是仓储管理员等
 * 界面里液体压在灌注枪本体（甚至数量）之上。</p>
 *
 * <p>现在改为在 <b>物品本体绘制之前</b>把液体画在同一位置、同一深度，
 * 层序自然变成「液体背景 → 灌注枪 → 数量角标」，与物品栏/HUD/容器槽位一致，
 * 也不需要再依赖各界面自己绘制数量角标的先后顺序。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    private static final int TEXTURE_SIZE = 16;

    /** 物品图标的基础 z（原版 {@code renderItem} 内部用 100）。 */
    private static final float ITEM_Z = 100.0F;

    @Inject(
            method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD")
    )
    private void createHandMade$fluidUnderlay(ItemStack stack, int x, int y, CallbackInfo ci) {
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
        setGLColorFromInt(tintColor);

        Matrix4f matrix = self.pose().last().pose();
        drawTiledSprite(matrix, x, y, 16, fluidHeight, sprite);

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
