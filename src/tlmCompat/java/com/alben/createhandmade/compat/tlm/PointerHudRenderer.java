package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.client.PointerHighlightClient;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.network.HighlightBlockPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PointerHudRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;

        int mode = PointerModeHelper.getMode(player.getMainHandItem());
        if (mode != PointerModeHelper.MODE_MARK && mode != PointerModeHelper.MODE_LIQUID) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int lineHeight = 12;
        int startY = screenHeight - 60;

        if (mode == PointerModeHelper.MODE_MARK) {
            renderMarkMode(graphics, mc, screenWidth, startY, lineHeight);
        } else {
            renderLiquidMode(graphics, mc, screenWidth, startY, lineHeight);
        }
    }

    // ================= MODE_MARK（原逻辑不变） =================

    private static void renderMarkMode(GuiGraphics graphics, Minecraft mc,
                                        int screenWidth, int startY, int lineHeight) {
        renderLine(graphics, mc, screenWidth, startY,
                "工作方块", HighlightBlockPacket.COLOR_WORK, true);
        renderLine(graphics, mc, screenWidth, startY + lineHeight,
                "输入", HighlightBlockPacket.COLOR_INPUT, true);
        renderLine(graphics, mc, screenWidth, startY + lineHeight * 2,
                "输出", HighlightBlockPacket.COLOR_OUTPUT, true);
    }

    // ================= MODE_LIQUID（新增） =================

    private static void renderLiquidMode(GuiGraphics graphics, Minecraft mc,
                                          int screenWidth, int startY, int lineHeight) {
        renderPosLine(graphics, mc, screenWidth, startY,
                "液体输入", PointerModeHandler.getClientLiquidInput(), 0xFF4FC3F7, true);
        renderPosLine(graphics, mc, screenWidth, startY + lineHeight,
                "液体输出", PointerModeHandler.getClientLiquidOutput(), 0xFF66BB6A, true);
        renderPosLine(graphics, mc, screenWidth, startY + lineHeight * 2,
                "过剩输出", PointerModeHandler.getClientLiquidOverflow(), 0xFFFFA726, true);
    }

    // ================= 通用渲染 =================

    /**
     * 通过颜色键从 PointerHighlightClient 读坐标（MODE_MARK 用）。
     */
    private static void renderLine(GuiGraphics graphics, Minecraft mc,
                                    int screenWidth, int y,
                                    String label, int colorKey, boolean shadow) {
        BlockPos pos = PointerHighlightClient.getHighlight(colorKey);
        drawLine(graphics, mc, screenWidth, y, label, pos, 0xFF000000 | colorKey, shadow);
    }

    /**
     * 直接传坐标渲染（MODE_LIQUID 用）。
     */
    private static void renderPosLine(GuiGraphics graphics, Minecraft mc,
                                       int screenWidth, int y,
                                       String label, BlockPos pos,
                                       int color, boolean shadow) {
        drawLine(graphics, mc, screenWidth, y, label, pos, color, shadow);
    }

    private static void drawLine(GuiGraphics graphics, Minecraft mc,
                                  int screenWidth, int y,
                                  String label, BlockPos pos,
                                  int color, boolean shadow) {
        String text;
        if (pos == null) {
            text = label + "：未标记";
        } else {
            text = label + "：(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        }

        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;

        graphics.drawString(mc.font, text, x, y, color, shadow);
    }
}