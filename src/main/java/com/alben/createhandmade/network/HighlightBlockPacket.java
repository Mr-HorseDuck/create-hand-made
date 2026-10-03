package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * ★ Forge 1.20.1 版本：
 *   - 不再实现 CustomPacketPayload / Type / StreamCodec
 *   - 编解码基于 FriendlyByteBuf
 *   - BlockPos.STREAM_CODEC → buf.writeBlockPos / readBlockPos
 */
public class HighlightBlockPacket {

    private final BlockPos pos;

    public HighlightBlockPacket(BlockPos pos) {
        this.pos = pos;
    }

    // ================= 解码构造器 =================

    public HighlightBlockPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    // ================= 编码 =================

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    // ================= 访问器 =================

    public BlockPos pos() {
        return pos;
    }
}