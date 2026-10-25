package com.alben.createhandmade.particle;

import com.zurrtum.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 风箱气流粒子。
 *
 * <p>26.1 的粒子渲染管线改为 {@code getGroup()} + {@code SingleQuadParticle.Layer}，
 * 透明度由 {@code Layer.TRANSLUCENT} 决定，因此这里不再重写 {@code getRenderType()}
 * 与光照采样（父类 {@code getLightCoords(float)} 已处理）。</p>
 */
public class BellowsAirParticle extends SimpleAnimatedParticle {

    /** 通用方向：爆发 = 从爆发中心指向外；射线 = 射线方向 */
    private final Vec3 direction;
    /** 爆发模式的最大飞行距离（也是射线的最大飞行距离） */
    private final float maxDistance;
    /** 射线模式每 tick 前进距离 */
    private final float beamSpeed;
    private final boolean beamMode;
    @Nullable
    private final FanProcessingType type;
    private final Access access = new Access();

    /** 爆发模式：已飞行距离（按每 tick 实际位移累加，不依赖绝对坐标空间）。 */
    private double traveledDistance = 0.0;

    protected BellowsAirParticle(ClientLevel world,
                                 Vec3 direction,
                                 float maxDistance,
                                 float beamSpeed,
                                 boolean beamMode,
                                 @Nullable FanProcessingType type,
                                 double x, double y, double z,
                                 SpriteSet sprite) {
        super(world, x, y, z, sprite, world.getRandom().nextFloat() * 0.5f);
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

    /** 爆发模式工厂：direction 即向外扩散方向 */
    public static BellowsAirParticle createBlast(ClientLevel world, Vec3 direction,
                                                 float maxDistance,
                                                 @Nullable FanProcessingType type,
                                                 double x, double y, double z, SpriteSet sprite) {
        return new BellowsAirParticle(world, direction, maxDistance, 0f, false,
                type, x, y, z, sprite);
    }

    /** 射线模式工厂：direction 必须是归一化方向 */
    public static BellowsAirParticle createBeam(ClientLevel world, Vec3 direction,
                                                float flyDistance, float speed,
                                                @Nullable FanProcessingType type,
                                                double x, double y, double z, SpriteSet sprite) {
        return new BellowsAirParticle(world, direction, flyDistance, speed, true,
                type, x, y, z, sprite);
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
            // ===== 射线模式：匀速前进 =====
            Vec3 motion = direction.scale(beamSpeed);
            xd = motion.x;
            yd = motion.y;
            zd = motion.z;

            applyTypeMorph();
            this.move(this.xd, this.yd, this.zd);
            return;
        }

        // ===== 爆发模式：从中心向外扩散（累计位移控制生命周期） =====
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

    /** 统一走 Create 的 morphAirFlow（改颜色 + 冒附加粒子） */
    private void applyTypeMorph() {
        if (type != null) {
            type.morphAirFlow(access, random);
            selectSprite(random.nextInt(3));
        }
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
}
