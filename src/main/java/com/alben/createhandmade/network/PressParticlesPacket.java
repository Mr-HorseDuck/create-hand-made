package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ Forge 1.20.1 版本：
 *   - 不再实现 CustomPacketPayload / Type / StreamCodec
 *   - 编解码基于 FriendlyByteBuf
 *   - List<ItemStack> 手动循环处理
 *   - ParticleStyle 枚举用 writeEnum / readEnum（1.20.1 已支持）
 *   - 保留便捷构造器：单个 ItemStack 版本
 */
public class PressParticlesPacket {

    public enum ParticleStyle {
        DEPLOY, PRESS, STIR
    }

    private final BlockPos pos;
    private final List<ItemStack> stacks;
    private final ParticleStyle style;

    public PressParticlesPacket(BlockPos pos, List<ItemStack> stacks, ParticleStyle style) {
        this.pos = pos;
        this.stacks = stacks;
        this.style = style;
    }

    /** 便捷构造：单个物品 */
    public PressParticlesPacket(BlockPos pos, ItemStack stack, ParticleStyle style) {
        this(pos, stack.isEmpty() ? List.of() : List.of(stack), style);
    }

    // ================= 解码构造器 =================

    public PressParticlesPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();

        // ★ List<ItemStack> 手动循环解码
        int stackCount = buf.readVarInt();
        List<ItemStack> decoded = new ArrayList<>(stackCount);
        for (int i = 0; i < stackCount; i++) {
            decoded.add(buf.readItem());
        }
        this.stacks = decoded;

        // ★ 枚举用 writeEnum / readEnum
        this.style = buf.readEnum(ParticleStyle.class);
    }

    // ================= 编码 =================

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);

        // ★ List<ItemStack> 手动循环编码
        buf.writeVarInt(stacks.size());
        for (ItemStack s : stacks) {
            buf.writeItem(s);
        }

        // ★ 枚举用 writeEnum
        buf.writeEnum(style);
    }

    // ================= 访问器 =================

    public BlockPos pos() {
        return pos;
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    public ParticleStyle style() {
        return style;
    }
}