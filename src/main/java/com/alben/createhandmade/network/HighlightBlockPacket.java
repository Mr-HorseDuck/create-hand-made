package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * ★ 兼容两种用法：
 *   - new HighlightBlockPacket(pos)              // 原始版本，默认金色
 *   - new HighlightBlockPacket(pos, color)       // 标记系统，三色
 */
public class HighlightBlockPacket {

    /** 默认高亮色（原始版本用的金色） */
    public static final int COLOR_DEFAULT = 0xFFD966;

    public static final int COLOR_WORK   = 0xFFFF00;
    public static final int COLOR_INPUT  = 0x00FF00;
    public static final int COLOR_OUTPUT = 0x0080FF;

    private final BlockPos pos;
    private final int color;

    /** 原始单参构造器（默认金色） */
    public HighlightBlockPacket(BlockPos pos) {
        this(pos, COLOR_DEFAULT);
    }

    /** 带颜色构造器 */
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