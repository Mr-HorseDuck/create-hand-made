package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.client.PointerHighlightClient;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.network.HighlightBlockPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "create_hand_made", value = Dist.CLIENT)
public class PointerHudRenderer {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;

        // ★ 只在指定模式显示 HUD
        if (PointerModeHelper.getMode(player.getMainHandItem()) != PointerModeHelper.MODE_MARK) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int lineHeight = 12;
        int startY = screenHeight - 60;

        renderLine(graphics, mc, screenWidth, startY,
                "工作方块", HighlightBlockPacket.COLOR_WORK);
        renderLine(graphics, mc, screenWidth, startY + lineHeight,
                "输入", HighlightBlockPacket.COLOR_INPUT);
        renderLine(graphics, mc, screenWidth, startY + lineHeight * 2,
                "输出", HighlightBlockPacket.COLOR_OUTPUT);
    }

    private static void renderLine(GuiGraphics graphics, Minecraft mc,
                                    int screenWidth, int y,
                                    String label, int colorKey) {
        BlockPos pos = PointerHighlightClient.getHighlight(colorKey);

        String text;
        if (pos == null) {
            text = label + "：未标记";
        } else {
            text = label + "：(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        }

        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;

        graphics.drawString(mc.font, text, x, y, 0xFF000000 | colorKey, true);
    }
}