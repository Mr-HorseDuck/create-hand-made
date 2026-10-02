package com.alben.createhandmade.particle;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BellowsAirParticleProvider implements ParticleProvider<BellowsAirParticleData> {

    private final SpriteSet spriteSet;

    /** 爆发粒子的固定最大飞行距离（1 格） */
    private static final float BLAST_MAX_DISTANCE = 1.0f;

    public BellowsAirParticleProvider(SpriteSet spriteSet) {
        this.spriteSet = spriteSet;
    }

    @Override
    @Nullable
    public Particle createParticle(BellowsAirParticleData data, ClientLevel level,
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

            return BellowsAirParticle.createBeam(level, dir, flyDistance, speed,
                    type, x, y, z, this.spriteSet);
        }

        // ===== 爆发模式 =====
        // 方向客户端随机，生命周期由累计位移控制，不依赖任何绝对坐标
        Vec3 direction = randomUnitVector(level.random);

        return BellowsAirParticle.createBlast(level, direction,
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