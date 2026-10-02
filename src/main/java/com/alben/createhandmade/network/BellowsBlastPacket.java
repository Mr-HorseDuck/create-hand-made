package com.alben.createhandmade.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * ★ Forge 1.20.1 版本：
 *   - 不再实现 CustomPacketPayload / Type / StreamCodec
 *   - 编解码基于 FriendlyByteBuf
 *   - 可空 BlockPos 用 writeBoolean + writeBlockPos 组合表达
 *   - ItemStack 用 writeItem / readItem（Forge 扩展，自动处理 NBT 与空栈）
 *   - List 手动写入 size + 循环
 */
public class BellowsBlastPacket {

    @Nullable
    private final BlockPos pos;
    private final double originX, originY, originZ;
    private final double targetX, targetY, targetZ;
    private final List<ItemStack> stacks;
    private final String typeId;
    private final boolean spawnBlast;

    public BellowsBlastPacket(@Nullable BlockPos pos,
                              double originX, double originY, double originZ,
                              double targetX, double targetY, double targetZ,
                              List<ItemStack> stacks,
                              String typeId,
                              boolean spawnBlast) {
        this.pos = pos;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.stacks = stacks;
        this.typeId = typeId;
        this.spawnBlast = spawnBlast;
    }

    // ================= 解码构造器 =================

    public BellowsBlastPacket(FriendlyByteBuf buf) {
        // ★ 可空 BlockPos：先读 boolean 判断，再读坐标
        boolean hasPos = buf.readBoolean();
        this.pos = hasPos ? buf.readBlockPos() : null;

        this.originX = buf.readDouble();
        this.originY = buf.readDouble();
        this.originZ = buf.readDouble();
        this.targetX = buf.readDouble();
        this.targetY = buf.readDouble();
        this.targetZ = buf.readDouble();

        // ★ List<ItemStack> 手动循环解码
        int stackCount = buf.readVarInt();
        List<ItemStack> decoded = new ArrayList<>(stackCount);
        for (int i = 0; i < stackCount; i++) {
            decoded.add(buf.readItem());
        }
        this.stacks = decoded;

        this.typeId = buf.readUtf();
        this.spawnBlast = buf.readBoolean();
    }

    // ================= 编码 =================

    public void encode(FriendlyByteBuf buf) {
        // 可空 BlockPos
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }

        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);

        // List<ItemStack> 手动循环编码
        buf.writeVarInt(stacks.size());
        for (ItemStack s : stacks) {
            buf.writeItem(s);
        }

        buf.writeUtf(typeId);
        buf.writeBoolean(spawnBlast);
    }

    // ================= 访问器 =================

    @Nullable
    public BlockPos pos() { return pos; }
    public double originX() { return originX; }
    public double originY() { return originY; }
    public double originZ() { return originZ; }
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double targetZ() { return targetZ; }
    public List<ItemStack> stacks() { return stacks; }
    public String typeId() { return typeId; }
    public boolean spawnBlast() { return spawnBlast; }
}