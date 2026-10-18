package com.alben.createhandmade.client;

import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions; // ★ 新增 import

public class InfusionGunFluidDecorator implements IItemDecorator {

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (!(stack.getItem() instanceof InfusionGunItem)) return false;
        InfusionGunContents contents = InfusionGunItem.getContents(stack);
        if (contents.isEmpty()) return false;

        float ratio = (float) contents.fluid().getAmount() / InfusionGunContents.CAPACITY;

        int barHeight = 2;
        int barX = xOffset + 2;
        int barY = yOffset + 13;
        int barWidth = 12;

        // ★ 正确方式：通过 IClientFluidTypeExtensions 获取客户端渲染用的 tint color
        int liquidColor = IClientFluidTypeExtensions.of(contents.fluid().getFluid())
                .getTintColor(contents.fluid());

        // 兜底：如果某些流体没有定义 tint，可能会返回 0xFFFFFF（白色）
        // 如果需要更明显的水色，可以保留固定值：
        // int liquidColor = 0x3F76E4;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

        int filledWidth = (int) (barWidth * ratio);
        int argb = (0xCC << 24) | (liquidColor & 0xFFFFFF);
        guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, argb);

        return true;
    }
}