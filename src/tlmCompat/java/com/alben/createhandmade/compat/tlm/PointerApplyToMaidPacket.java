package com.alben.createhandmade.compat.tlm;

import net.minecraft.network.FriendlyByteBuf;

public class PointerApplyToMaidPacket {

    private final int entityId;

    public PointerApplyToMaidPacket(int entityId) {
        this.entityId = entityId;
    }

    public PointerApplyToMaidPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public int entityId() {
        return entityId;
    }
}