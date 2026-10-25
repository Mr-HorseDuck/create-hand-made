package com.alben.createhandmade.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

/** 风箱气流粒子数据。 */
public class BellowsAirParticleData implements ParticleOptions {

    public static final MapCodec<BellowsAirParticleData> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(p -> p.pos),
                    Codec.BOOL.fieldOf("beam").forGetter(p -> p.beam),
                    Codec.FLOAT.fieldOf("dx").forGetter(p -> p.dx),
                    Codec.FLOAT.fieldOf("dy").forGetter(p -> p.dy),
                    Codec.FLOAT.fieldOf("dz").forGetter(p -> p.dz),
                    Codec.FLOAT.fieldOf("speed").forGetter(p -> p.speed),
                    Codec.STRING.fieldOf("type").forGetter(p -> p.typeId)
            ).apply(i, BellowsAirParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BellowsAirParticleData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BellowsAirParticleData decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    boolean beam = buf.readBoolean();
                    float dx = buf.readFloat();
                    float dy = buf.readFloat();
                    float dz = buf.readFloat();
                    float speed = buf.readFloat();
                    String typeId = buf.readUtf();
                    return new BellowsAirParticleData(pos, beam, dx, dy, dz, speed, typeId);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, BellowsAirParticleData p) {
                    BlockPos.STREAM_CODEC.encode(buf, p.pos);
                    buf.writeBoolean(p.beam);
                    buf.writeFloat(p.dx);
                    buf.writeFloat(p.dy);
                    buf.writeFloat(p.dz);
                    buf.writeFloat(p.speed);
                    buf.writeUtf(p.typeId);
                }
            };

    public final BlockPos pos;
    public final boolean beam;
    public final float dx, dy, dz;
    public final float speed;
    public final String typeId;

    public BellowsAirParticleData(BlockPos pos, boolean beam,
                                  float dx, float dy, float dz,
                                  float speed, String typeId) {
        this.pos = pos;
        this.beam = beam;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.speed = speed;
        this.typeId = typeId;
    }

    public BellowsAirParticleData() {
        this(BlockPos.ZERO, false, 0, 0, 0, 0, "");
    }

    public static BellowsAirParticleData blast(String typeId) {
        return new BellowsAirParticleData(BlockPos.ZERO, false, 0, 0, 0, 0,
                typeId == null ? "" : typeId);
    }

    public static BellowsAirParticleData beam(float dx, float dy, float dz,
                                              float speed, String typeId) {
        return new BellowsAirParticleData(BlockPos.ZERO, true, dx, dy, dz, speed, typeId);
    }

    @Override
    public @NotNull ParticleType<?> getType() {
        return ModParticleTypes.BELLOWS_AIR;
    }
}
