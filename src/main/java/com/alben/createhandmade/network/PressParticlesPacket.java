package com.alben.createhandmade.network;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 冲压锤 / 指杆 / 搅拌杖的碎屑粒子（服务端 → 客户端）。 */
public record PressParticlesPacket(BlockPos pos, List<ItemStack> stacks, ParticleStyle style)
        implements CustomPacketPayload {

    public enum ParticleStyle {
        DEPLOY, PRESS, STIR
    }

    public static final CustomPacketPayload.Type<PressParticlesPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(CreateHandMade.MODID, "press_particles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PressParticlesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PressParticlesPacket::pos,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), PressParticlesPacket::stacks,
                    ByteBufCodecs.VAR_INT, p -> p.style().ordinal(),
                    (pos, stacks, ordinal) -> new PressParticlesPacket(pos, stacks,
                            ParticleStyle.values()[ordinal])
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 便捷构造：单个物品 */
    public PressParticlesPacket(BlockPos pos, ItemStack stack, ParticleStyle style) {
        this(pos, stack.isEmpty() ? List.of() : List.of(stack), style);
    }
}
