package com.alben.createhandmade.compat.tlm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class PointerMarkerPacket {

    private final BlockPos pos;

    public PointerMarkerPacket(BlockPos pos) {
        this.pos = pos;
    }

    public PointerMarkerPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public BlockPos pos() {
        return pos;
    }
}