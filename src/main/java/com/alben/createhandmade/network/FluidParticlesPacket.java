package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;

/**
 * ★ Forge 1.20.1 版本：
 *   - 不再实现 CustomPacketPayload / Type / StreamCodec
 *   - 编解码基于 FriendlyByteBuf
 *   - FluidStack 用 Forge 扩展的 writeToPacket / readFromPacket
 *   - FluidStack 导入路径从 net.neoforged.neoforge.fluids 换到 net.minecraftforge.fluids
 */
public class FluidParticlesPacket {

    private final BlockPos pos;
    private final FluidStack fluid;

    public FluidParticlesPacket(BlockPos pos, FluidStack fluid) {
        this.pos = pos;
        this.fluid = fluid;
    }

    // ================= 解码构造器 =================

    public FluidParticlesPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        // ★ Forge 扩展：readFromPacket 自动处理空流体与 NBT
        this.fluid = FluidStack.readFromPacket(buf);
    }

    // ================= 编码 =================

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        // ★ Forge 扩展：writeToPacket 自动处理空流体与 NBT
        fluid.writeToPacket(buf);
    }

    // ================= 访问器 =================

    public BlockPos pos() {
        return pos;
    }

    public FluidStack fluid() {
        return fluid;
    }
}