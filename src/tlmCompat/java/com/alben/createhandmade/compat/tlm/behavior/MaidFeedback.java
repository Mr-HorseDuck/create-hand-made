package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.compat.tlm.MaidConfig;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

/**
 * 女仆加工反馈：音效 + 粒子。
 *
 * <p>全部在服务端触发，{@code level.playSound(null, ...)} 广播给附近玩家，
 * {@code sendParticles} 也是服务端生成。</p>
 *
 * <p>粒子数量按 {@code MaidConfig.maidParticleLevel}（1-5）统一缩放：
 * 基准值对应等级 3，等级 1 约 1/3，等级 5 约 5/3。</p>
 */
public final class MaidFeedback {

    private MaidFeedback() {}

    /** 按配置等级缩放粒子数量，至少 1 个。 */
    private static int scaled(int base) {
        int level = MaidConfig.INSTANCE.maidParticleLevel.get();
        return Math.max(1, base * level / 3);
    }

    // ================= 音效 =================

    /** 冲压：机械压床启动音。 */
    public static void pressSound(Level level, BlockPos pos) {
        AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, pos, 0.8f, 1.0f);
    }

    /** 切削：木头破碎音。 */
    public static void cutSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /** 搅拌：Create 搅拌音。 */
    public static void stirSound(Level level, BlockPos pos) {
        AllSoundEvents.MIXING.playOnServer(level, pos, 1.0f, 2.0f);
    }

    /** 液体注入：灌装音。 */
    public static void fluidFillSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /** 液体抽取：倒空音。 */
    public static void fluidDrainSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /** 风箱：鼓风音。 */
    public static void bellowsSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** 部署：物品拾取音。 */
    public static void deploySound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ================= 粒子 =================

    /** 物品粒子：从方块中心向上散开。{@code baseCount} 是等级 3 时的数量。 */
    public static void itemParticles(Level level, BlockPos pos, ItemStack item, int baseCount) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (item.isEmpty()) return;

        int count = scaled(baseCount);
        ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, item);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.7;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + level.random.nextDouble() * 0.1;
            serverLevel.sendParticles(particle, cx, cy, cz, 1,
                    Math.cos(angle) * speed,
                    0.15 + level.random.nextDouble() * 0.15,
                    Math.sin(angle) * speed,
                    0.0);
        }
    }

    /** 鼓风粒子：从方块中心向四周吹。 */
    public static void windParticles(Level level, BlockPos pos, int baseCount) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        int count = scaled(baseCount);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.7;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double speed = 0.1 + level.random.nextDouble() * 0.15;
            serverLevel.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 1,
                    Math.cos(angle) * speed,
                    0.05 + level.random.nextDouble() * 0.1,
                    Math.sin(angle) * speed,
                    0.0);
        }
    }

    /** 液体粒子：灌装/抽取时溅出的液滴。 */
    public static void fluidParticles(Level level, BlockPos pos, FluidStack fluid, int baseCount) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (fluid.isEmpty()) return;

        int count = scaled(baseCount);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.8;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + level.random.nextDouble() * 0.1;
            serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, cx, cy, cz, 1,
                    Math.cos(angle) * speed,
                    0.1 + level.random.nextDouble() * 0.1,
                    Math.sin(angle) * speed,
                    0.0);
        }
    }

    /** 搅拌粒子：环绕方块中心的螺旋。 */
    public static void stirParticles(Level level, BlockPos pos, int baseCount) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        int count = scaled(baseCount);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = 0.3 + level.random.nextDouble() * 0.2;
            double vx = -Math.sin(angle) * 0.05;
            double vz = Math.cos(angle) * 0.05;
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    cx + Math.cos(angle) * radius,
                    cy + 0.2,
                    cz + Math.sin(angle) * radius,
                    1, vx, 0.02, vz, 0.0);
        }
    }
}