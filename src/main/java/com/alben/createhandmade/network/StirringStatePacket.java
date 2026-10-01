package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ★ Forge 1.20.1 版本：
 *   - 不再实现 CustomPacketPayload / Type / StreamCodec
 *   - 改用 SimpleChannel 的 encode / decode / handle 三件套
 *   - 编解码基于 FriendlyByteBuf（不是 RegistryFriendlyByteBuf）
 */
public class StirringStatePacket {

    private final BlockPos pos;

    public StirringStatePacket(BlockPos pos) {
        this.pos = pos;
    }

    // ================= 编解码 =================

    public StirringStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    // ================= 访问器 =================

    public BlockPos pos() {
        return pos;
    }
}