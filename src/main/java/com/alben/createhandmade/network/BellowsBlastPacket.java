package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 风箱气流（射线 + 末端爆发）粒子（服务端 → 客户端）。 */
public record BellowsBlastPacket(
        @Nullable BlockPos pos,           // 爆发点；无爆发时为 null
        double originX, double originY, double originZ,
        double targetX, double targetY, double targetZ,
        List<ItemStack> stacks,
        String typeId,
        boolean spawnBlast
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BellowsBlastPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "bellows_blast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BellowsBlastPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BellowsBlastPacket decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = buf.readNullable(BlockPos.STREAM_CODEC);
                    double ox = buf.readDouble();
                    double oy = buf.readDouble();
                    double oz = buf.readDouble();
                    double tx = buf.readDouble();
                    double ty = buf.readDouble();
                    double tz = buf.readDouble();
                    List<ItemStack> stacks = ItemStack.STREAM_CODEC
                            .apply(ByteBufCodecs.list())
                            .decode(buf);
                    String typeId = buf.readUtf();
                    boolean spawnBlast = buf.readBoolean();
                    return new BellowsBlastPacket(pos, ox, oy, oz, tx, ty, tz,
                            stacks, typeId, spawnBlast);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, BellowsBlastPacket p) {
                    buf.writeNullable(p.pos(), BlockPos.STREAM_CODEC);
                    buf.writeDouble(p.originX());
                    buf.writeDouble(p.originY());
                    buf.writeDouble(p.originZ());
                    buf.writeDouble(p.targetX());
                    buf.writeDouble(p.targetY());
                    buf.writeDouble(p.targetZ());
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())
                            .encode(buf, p.stacks());
                    buf.writeUtf(p.typeId());
                    buf.writeBoolean(p.spawnBlast());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
