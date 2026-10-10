package com.alben.createhandmade.compat.tlm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 液体模式标记包。
 * ★ 与 PointerMarkerPacket 结构相同，只传 BlockPos。
 *   服务端 handler 走 PointerLiquidMarker.doMark。
 */
public class PointerLiquidMarkerPacket {

    private final BlockPos pos;

    public PointerLiquidMarkerPacket(BlockPos pos) {
        this.pos = pos;
    }

    public PointerLiquidMarkerPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public BlockPos pos() {
        return pos;
    }
}