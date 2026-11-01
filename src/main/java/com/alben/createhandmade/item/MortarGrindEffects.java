package com.alben.createhandmade.item;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 研钵 / 碾钵共用的研磨音效与粒子逻辑。
 * 两份 onUseTick 曾各自复制一份，碾钵漏改配置读取导致起手节奏错位；
 * 现在公共逻辑只在这里维护，两个物品只提供各自的 windupTicks。
 */
public final class MortarGrindEffects {

    /** 保底音标记：本次加工是否已经播过常规研磨音。唯一 key，两侧共用。 */
    public static final String NBT_SOUND_PLAYED = "HandMadeGrindSoundPlayed";

    private MortarGrindEffects() {
    }

    /**
     * 按已使用 tick 播放研磨音与粒子。
     *
     * @param windupTicks 起手时长，来自当前工具自己的配置（研钵 / 碾钵不同）
     */
    public static void tick(Level level, LivingEntity entity, ItemStack stack, ItemStack input,
                            int usedTicks, int windupTicks) {
        if (usedTicks < windupTicks || usedTicks % 4 != 0) return;
        if (level.isClientSide) return;

        level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);

        stack.getOrCreateTag().putBoolean(NBT_SOUND_PLAYED, true);

        if (level instanceof ServerLevel serverLevel) {
            Vec3 eye = entity.getEyePosition(1f);
            Vec3 look = entity.getLookAngle();
            Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

            for (int i = 0; i < 2; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double horizSpeed = 0.05 + level.random.nextDouble() * 0.1;
                double vx = Math.cos(angle) * horizSpeed;
                double vz = Math.sin(angle) * horizSpeed;
                double vy = 0.15 + level.random.nextDouble() * 0.15;

                serverLevel.sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, input),
                        spawnPos.x, spawnPos.y, spawnPos.z,
                        1, vx, vy, vz, 0.0
                );
            }
        }
    }
}