package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.client.PointerHighlightClient;
import com.alben.createhandmade.item.PointerItem;
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

    // ================= MODE_MARK =================

    private static void renderMarkMode(GuiGraphics graphics, Minecraft mc,
                                        int screenWidth, int startY, int lineHeight) {
        renderLine(graphics, mc, screenWidth, startY,
                "工作方块", PointerColors.WORK, true);
        renderLine(graphics, mc, screenWidth, startY + lineHeight,
                "输入", PointerColors.INPUT, true);
        renderLine(graphics, mc, screenWidth, startY + lineHeight * 2,
                "输出", PointerColors.OUTPUT, true);
    }

    // ================= MODE_LIQUID =================

    private static void renderLiquidMode(GuiGraphics graphics, Minecraft mc,
                                          int screenWidth, int startY, int lineHeight) {
        renderPosLine(graphics, mc, screenWidth, startY,
                "液体输入",
                PointerModeHandler.getClientLiquidInput(),
                0xFF000000 | PointerColors.INPUT, true);

        renderPosLine(graphics, mc, screenWidth, startY + lineHeight,
                "液体输出",
                PointerModeHandler.getClientLiquidOutput(),
                0xFF000000 | PointerColors.OUTPUT, true);

        renderPosLine(graphics, mc, screenWidth, startY + lineHeight * 2,
                "过剩输出",
                PointerModeHandler.getClientLiquidOverflow(),
                0xFF000000 | PointerColors.OVERFLOW, true);
    }

    // ================= 通用渲染 =================

    private static void renderLine(GuiGraphics graphics, Minecraft mc,
                                    int screenWidth, int y,
                                    String label, int colorKey, boolean shadow) {
        BlockPos pos = PointerHighlightClient.getHighlight(colorKey);
        drawLine(graphics, mc, screenWidth, y, label, pos, 0xFF000000 | colorKey, shadow);
    }

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