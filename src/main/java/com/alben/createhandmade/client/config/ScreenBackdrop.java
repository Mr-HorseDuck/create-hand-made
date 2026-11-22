package com.alben.createhandmade.client.config;

import com.alben.createhandmade.CreateHandMade;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/**
 * 配置界面的背景处理：把已经画好的世界做一次模糊，并暂时隐藏 HUD。
 *
 * <h2>模糊</h2>
 * <p>1.20.1 没有 1.21 的「菜单背景模糊」选项，但原版<b>自带了 blur 后处理链</b>
 * （{@code minecraft:shaders/post/blur.json}，两趟可分离模糊、Radius 20）。这里自己建一条
 * {@link PostChain} 每帧处理一次主渲染目标 —— 等同于 1.21 的
 * {@code GameRenderer#processBlurEffect}，但不去动全局的 {@code GameRenderer.effect}，
 * 所以关掉界面后世界立刻恢复清晰。</p>
 *
 * <p>用法：在 {@code render()} 的<b>最前面</b>调用 {@link #blur(float)}。此时世界里的一切
 * （含上一帧）已经在主渲染目标里，模糊完再让 {@code Screen} 往上画半透明底色和控件，
 * 于是只有世界被模糊、界面本身保持清晰。</p>
 *
 * <p>只在 {@code minecraft.level != null}（在存档里）时模糊，和 1.21 的行为一致：
 * 主菜单打开时原版画的是不透明背景，模糊既看不见也白费性能；
 * 顺带避免把「上一页 Mods 列表」的残影糊在背景上。</p>
 *
 * <h2>失败兜底</h2>
 * <p>光影类模组（Iris 等）会接管渲染管线，后处理链有可能建不起来或跑不动。
 * 这里任何一步抛异常就<b>永久停用模糊</b>（只记一条日志），界面照常可用 ——
 * 模糊只是观感，不值得为它让配置界面打不开。</p>
 */
public final class ScreenBackdrop implements AutoCloseable {

    /** 原版自带的后处理链（不在我们 jar 里，直接引用 {@code minecraft} 命名空间）。 */
    private static final ResourceLocation BLUR =
            new ResourceLocation("shaders/post/blur.json");

    private PostChain blurChain;
    private boolean disabled;
    private int width = -1;
    private int height = -1;

    /**
     * 把当前主渲染目标糊一层。必须在 GUI 开始绘制之前调用。
     *
     * @param partialTick 当前帧的插值进度（后处理链用它推进内部时间）
     */
    public void blur(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (disabled || minecraft.level == null || minecraft.getMainRenderTarget() == null) {
            return;
        }

        try {
            if (blurChain == null) {
                blurChain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), BLUR);
                width = -1;
            }
            int w = minecraft.getWindow().getWidth();
            int h = minecraft.getWindow().getHeight();
            if (w != width || h != height) {
                blurChain.resize(w, h);
                width = w;
                height = h;
            }

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            blurChain.process(partialTick);
            minecraft.getMainRenderTarget().bindWrite(true);
        } catch (Throwable t) {
            disabled = true;
            close();
            CreateHandMade.LOGGER.warn(
                    "[HandMade] 配置界面背景模糊不可用，已停用（界面不受影响）", t);
        }
    }

    /** 释放后处理链。界面关掉时必须调用，否则离屏缓冲会一直占着显存。 */
    @Override
    public void close() {
        if (blurChain != null) {
            try {
                blurChain.close();
            } catch (Throwable t) {
                CreateHandMade.LOGGER.debug("[HandMade] 释放模糊后处理链失败", t);
            }
            blurChain = null;
        }
        width = -1;
        height = -1;
    }
}
