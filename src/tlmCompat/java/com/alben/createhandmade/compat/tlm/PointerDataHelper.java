package com.alben.createhandmade.compat.tlm;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class PointerDataHelper {

    public static final String NBT_ROOT = "HandMadePointerData";
    public static final String KEY_WORK = "WorkBlock";
    public static final String KEY_INPUT = "InputBlock";
    public static final String KEY_OUTPUT = "OutputBlock";

    // ★ 液体模式（MODE_LIQUID）
    public static final String KEY_LIQUID_INPUT = "LiquidInputBlock";
    public static final String KEY_LIQUID_OUTPUT = "LiquidOutputBlock";
    public static final String KEY_LIQUID_OVERFLOW = "LiquidOverflowBlock";

    private PointerDataHelper() {}

    // ================= MODE_MARK =================

    public static void setWork(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_WORK, level, pos);
    }

    public static void setInput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_INPUT, level, pos);
    }

    public static void setOutput(Entity entity, Level level, @Nullable BlockPos pos) {
        if (pos == null) {
            removeBlock(entity, KEY_OUTPUT);
            return;
        }
        writeBlock(entity, KEY_OUTPUT, level, pos);
    }

    @Nullable
    public static BlockPos getWork(Entity entity, Level level) {
        return readBlock(entity, KEY_WORK, level);
    }

    @Nullable
    public static BlockPos getInput(Entity entity, Level level) {
        return readBlock(entity, KEY_INPUT, level);
    }

    @Nullable
    public static BlockPos getOutput(Entity entity, Level level) {
        return readBlock(entity, KEY_OUTPUT, level);
    }

    // ================= MODE_LIQUID（新增） =================

    public static void setLiquidInput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_INPUT, level, pos);
    }

    public static void setLiquidOutput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_OUTPUT, level, pos);
    }

    public static void setLiquidOverflow(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_OVERFLOW, level, pos);
    }

    @Nullable
    public static BlockPos getLiquidInput(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_INPUT, level);
    }

    @Nullable
    public static BlockPos getLiquidOutput(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_OUTPUT, level);
    }

    @Nullable
    public static BlockPos getLiquidOverflow(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_OVERFLOW, level);
    }

    /** 清除液体模式的三个坐标 */
    public static void clearLiquid(Entity entity) {
        removeBlock(entity, KEY_LIQUID_INPUT);
        removeBlock(entity, KEY_LIQUID_OUTPUT);
        removeBlock(entity, KEY_LIQUID_OVERFLOW);
    }

    /**
     * ★ 只清输出和过剩，保留输入。
     * 用于三标满后再次标记、覆盖输入的场景。
     */
    public static void clearLiquidPartial(Entity entity) {
        removeBlock(entity, KEY_LIQUID_OUTPUT);
        removeBlock(entity, KEY_LIQUID_OVERFLOW);
    }

    // ================= 通用 =================

    private static void writeBlock(Entity entity, String key, Level level, BlockPos pos) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putString("Dim", level.dimension().location().toString());
        root.put(key, tag);
        entity.getPersistentData().put(NBT_ROOT, root);
    }

    private static void removeBlock(Entity entity, String key) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        root.remove(key);
        entity.getPersistentData().put(NBT_ROOT, root);
    }

    @Nullable
    private static BlockPos readBlock(Entity entity, String key, Level level) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        if (!root.contains(key)) return null;

        CompoundTag tag = root.getCompound(key);
        String dim = tag.getString("Dim");
        if (!dim.equals(level.dimension().location().toString())) return null;

        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    public static void clear(Entity entity) {
        entity.getPersistentData().remove(NBT_ROOT);
    }

    public static void copyFrom(Entity source, Entity target) {
        CompoundTag root = source.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) return;
        target.getPersistentData().put(NBT_ROOT, root.copy());
    }
}