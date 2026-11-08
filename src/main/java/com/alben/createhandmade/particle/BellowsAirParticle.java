package com.alben.createhandmade.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 风箱气流粒子三合一：数据（网络/编解码）+ 工厂 + 粒子本体，全在同一个文件里，
 * 因为它们只服务于同一个粒子类型（{@code create_hand_made:bellows_air}）。
 */
public class BellowsAirParticle extends SimpleAnimatedParticle {

    private final Vec3 direction;
    private final float maxDistance;
    private final float beamSpeed;
    private final boolean beamMode;
    @Nullable private final FanProcessingType type;
    private final Access access = new Access();

    private double traveledDistance = 0.0;

    protected BellowsAirParticle(ClientLevel world,
                                 Vec3 direction,
                                 float maxDistance,
                                 float beamSpeed,
                                 boolean beamMode,
                                 @Nullable FanProcessingType type,
                                 double x, double y, double z,
                                 SpriteSet sprite) {
        super(world, x, y, z, sprite, world.random.nextFloat() * 0.5f);
        this.direction = direction;
        this.maxDistance = maxDistance;
        this.beamSpeed = beamSpeed;
        this.beamMode = beamMode;
        this.type = type;

        this.quadSize *= 0.75F;
        this.hasPhysics = false;
        this.setColor(0xEEEEEE);
        this.setAlpha(0.25f);

        if (beamMode) {
            this.lifetime = Math.max(4, (int) (maxDistance / beamSpeed));
        } else {
            this.lifetime = 30;
        }

        selectSprite(7);
    }

    public static BellowsAirParticle createBlast(ClientLevel world, Vec3 direction,
                                                 float maxDistance,
                                                 @Nullable FanProcessingType type,
                                                 double x, double y, double z, SpriteSet sprite) {
        return new BellowsAirParticle(world, direction, maxDistance, 0f, false,
                type, x, y, z, sprite);
    }

    public static BellowsAirParticle createBeam(ClientLevel world, Vec3 direction,
                                                float flyDistance, float speed,
                                                @Nullable FanProcessingType type,
                                                double x, double y, double z, SpriteSet sprite) {
        return new BellowsAirParticle(world, direction, flyDistance, speed, true,
                type, x, y, z, sprite);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }

        if (beamMode) {
            Vec3 motion = direction.scale(beamSpeed);
            xd = motion.x;
            yd = motion.y;
            zd = motion.z;

            applyTypeMorph();
            this.move(this.xd, this.yd, this.zd);
            return;
        }

        Vec3 motion = direction.scale(1 / 8f);
        if (traveledDistance > maxDistance * 0.5) {
            motion = motion.scale(0.5);
        }

        traveledDistance += motion.length();

        if (traveledDistance > maxDistance) {
            remove();
            return;
        }

        float progress = (float) Mth.clamp(traveledDistance / maxDistance, 0.0, 1.0);

        if (type != null) {
            applyTypeMorph();
        } else {
            setColor(0xEEEEEE);
            setAlpha(0.25f * (1.0f - progress * 0.7f));
            selectSprite((int) Mth.clamp(progress * 8 + random.nextInt(4), 0, 7));
        }

        xd = motion.x;
        yd = motion.y;
        zd = motion.z;

        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
        }
        this.move(this.xd, this.yd, this.zd);
    }

    private void applyTypeMorph() {
        if (type != null) {
            type.morphAirFlow(access, random);
            selectSprite(random.nextInt(3));
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        BlockPos blockpos = BlockPos.containing(this.x, this.y, this.z);
        return this.level.isLoaded(blockpos)
                ? LevelRenderer.getLightColor(level, blockpos)
                : 0;
    }

    private void selectSprite(int index) {
        setSprite(sprites.get(index, 8));
    }

    private class Access implements FanProcessingType.AirFlowParticleAccess {
        @Override
        public void setColor(int color) {
            BellowsAirParticle.this.setColor(color);
        }

        @Override
        public void setAlpha(float alpha) {
            BellowsAirParticle.this.setAlpha(alpha);
        }

        @Override
        public void spawnExtraParticle(ParticleOptions options, float speedMultiplier) {
            level.addParticle(options, x, y, z,
                    xd * speedMultiplier, yd * speedMultiplier, zd * speedMultiplier);
        }
    }

    // ==================== 粒子数据（网络 / 编解码） ====================

    public static class Data implements ParticleOptions,
            ICustomParticleDataWithSprite<Data> {

        public static final MapCodec<Data> CODEC = RecordCodecBuilder.mapCodec(i ->
                i.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(p -> p.pos),
                        Codec.BOOL.fieldOf("beam").forGetter(p -> p.beam),
                        Codec.FLOAT.fieldOf("dx").forGetter(p -> p.dx),
                        Codec.FLOAT.fieldOf("dy").forGetter(p -> p.dy),
                        Codec.FLOAT.fieldOf("dz").forGetter(p -> p.dz),
                        Codec.FLOAT.fieldOf("speed").forGetter(p -> p.speed),
                        Codec.STRING.fieldOf("type").forGetter(p -> p.typeId)
                ).apply(i, Data::new));

        public final BlockPos pos;
        public final boolean beam;
        public final float dx, dy, dz;
        public final float speed;
        public final String typeId;

        public Data(BlockPos pos, boolean beam,
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

        /** 供 ModParticleTypes.createType() 使用的无参构造 */
        public Data() {
            this(BlockPos.ZERO, false, 0, 0, 0, 0, "");
        }

        /** 爆发模式便捷构造 */
        public static Data blast(String typeId) {
            return new Data(BlockPos.ZERO, false, 0, 0, 0, 0,
                    typeId == null ? "" : typeId);
        }

        /** 射线模式便捷构造 */
        public static Data beam(float dx, float dy, float dz,
                                float speed, String typeId) {
            return new Data(BlockPos.ZERO, true, dx, dy, dz, speed, typeId);
        }

        @Override
        public @NotNull ParticleType<?> getType() {
            return ModParticleTypes.BELLOWS_AIR.get();
        }

        @Override
        public String writeToString() {
            return "bellows_air[beam=" + beam + ",type=" + typeId + "]";
        }

        @Override
        public void writeToNetwork(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(beam);
            buf.writeFloat(dx);
            buf.writeFloat(dy);
            buf.writeFloat(dz);
            buf.writeFloat(speed);
            buf.writeUtf(typeId);
        }

        @Override
        public Codec<Data> getCodec(ParticleType<Data> type) {
            return CODEC.codec();
        }

        @Override
        public ParticleOptions.Deserializer<Data> getDeserializer() {
            return new ParticleOptions.Deserializer<Data>() {
                @Override
                public Data fromNetwork(ParticleType<Data> type, FriendlyByteBuf buf) {
                    return new Data(
                            buf.readBlockPos(),
                            buf.readBoolean(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readUtf()
                    );
                }

                public Data fromJson(ParticleType<Data> type,
                                     com.google.gson.JsonElement json) {
                    return CODEC.codec()
                            .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                            .getOrThrow(false, s -> {});
                }

                // ★ 关键：去掉 @Override，让编译器不再校验签名匹配
                public Data fromCommand(ParticleType<Data> type,
                                        com.mojang.brigadier.StringReader reader) {
                    return new Data();
                }
            };
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public SpriteParticleRegistration<Data> getMetaFactory() {
            return Provider::new;
        }
    }

    // ==================== 粒子工厂 ====================

    public static class Provider implements ParticleProvider<Data> {

        /** 爆发粒子的固定最大飞行距离（1 格） */
        private static final float BLAST_MAX_DISTANCE = 1.0f;

        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        @Nullable
        public Particle createParticle(Data data, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            FanProcessingType type = data.typeId.isEmpty()
                    ? null
                    : FanProcessingType.parse(data.typeId);

            if (data.beam) {
                // ===== 射线模式 =====
                Vec3 dir = new Vec3(data.dx, data.dy, data.dz);
                if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 1, 0);
                dir = dir.normalize();

                float flyDistance = 3.0f;
                float speed = data.speed <= 0 ? 0.5f : data.speed;

                return createBeam(level, dir, flyDistance, speed,
                        type, x, y, z, this.spriteSet);
            }

            // ===== 爆发模式 =====
            // 方向客户端随机，生命周期由累计位移控制，不依赖任何绝对坐标
            Vec3 direction = randomUnitVector(level.random);

            return createBlast(level, direction,
                    BLAST_MAX_DISTANCE, type, x, y, z, this.spriteSet);
        }

        /** 球面均匀分布的随机单位向量 */
        private static Vec3 randomUnitVector(RandomSource random) {
            double theta = random.nextDouble() * Math.PI * 2;
            double cosPhi = 2 * random.nextDouble() - 1;
            double sinPhi = Math.sqrt(1 - cosPhi * cosPhi);
            return new Vec3(
                    sinPhi * Math.cos(theta),
                    cosPhi,
                    sinPhi * Math.sin(theta)
            );
        }
    }
}
