package com.alben.createhandmade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 「请打开配置界面」——空包。
 *
 * <p>为什么需要它：{@code /chm config} 是<b>服务端</b>命令，而界面在客户端。
 * 服务端只能通知「这个玩家该开界面了」，真正 {@code setScreen} 得客户端自己做。</p>
 *
 * <p>0.7.0 新增；因为老客户端不认这个包，{@code PROTOCOL_VERSION} 由 "2" 升到 "3"。</p>
 */
public class OpenConfigScreenPacket {

    public OpenConfigScreenPacket() {
    }

    public OpenConfigScreenPacket(FriendlyByteBuf buffer) {
        // 无字段
    }

    public void encode(FriendlyByteBuf buffer) {
        // 无字段
    }
}
