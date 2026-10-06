package com.alben.createhandmade.compat.tlm;

import net.minecraft.network.FriendlyByteBuf;

public class PointerModePacket {

    private final int mode;

    public PointerModePacket(int mode) {
        this.mode = mode;
    }

    public PointerModePacket(FriendlyByteBuf buf) {
        this.mode = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(mode);
    }

    public int mode() {
        return mode;
    }
}