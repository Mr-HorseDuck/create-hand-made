package com.alben.createhandmade.network;

import com.alben.createhandmade.config.SyncedServerConfig;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 服务端 → 客户端：把 SERVER 配置里的工具时间参数推给客户端。
 *
 * <p>槽位含义与打包顺序见 {@link SyncedServerConfig#capture()} / {@link SyncedServerConfig#apply(int[])}，
 * 本类只负责搬运 int 数组，不解释内容。</p>
 */
public class ConfigSyncPacket {

    private final int[] values;

    public ConfigSyncPacket(int[] values) {
        this.values = values;
    }

    public ConfigSyncPacket(FriendlyByteBuf buf) {
        this.values = buf.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarIntArray(this.values);
    }

    public int[] values() {
        return values;
    }
}
