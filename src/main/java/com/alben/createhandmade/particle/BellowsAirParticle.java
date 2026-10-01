package com.alben.createhandmade.particle;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

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
}