package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class HighlightBlockPacket {

    public static final int COLOR_WORK   = 0xFFFF00;
    public static final int COLOR_INPUT  = 0x00FF00;
    public static final int COLOR_OUTPUT = 0x0080FF;

    private final BlockPos pos;
    private final int color;

    public HighlightBlockPacket(BlockPos pos, int color) {
        this.pos = pos;
        this.color = color;
    }

    public HighlightBlockPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.color = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(color);
    }

    public BlockPos pos() {
        return pos;
    }

    public int color() {
        return color;
    }
}