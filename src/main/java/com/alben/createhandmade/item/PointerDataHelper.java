package com.alben.createhandmade.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class PointerDataHelper {

    public static final String NBT_ROOT = "HandMadePointerData";
    public static final String KEY_WORK = "WorkBlock";
    public static final String KEY_INPUT = "InputBlock";
    public static final String KEY_OUTPUT = "OutputBlock";

    private PointerDataHelper() {}

    // ================= 写入 =================

    public static void setWork(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_WORK, level, pos);
    }

    public static void setInput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_INPUT, level, pos);
    }

    public static void setOutput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_OUTPUT, level, pos);
    }

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

    // ================= 读取 =================

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

    @Nullable
    private static BlockPos readBlock(Entity entity, String key, Level level) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        if (!root.contains(key)) return null;

        CompoundTag tag = root.getCompound(key);
        String dim = tag.getString("Dim");
        if (!dim.equals(level.dimension().location().toString())) return null;

        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    // ================= 清空 =================

    public static void clear(Entity entity) {
        entity.getPersistentData().remove(NBT_ROOT);
    }

    // ================= 女仆复制 =================

    public static void copyFrom(Entity source, Entity target) {
        CompoundTag root = source.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) return;
        target.getPersistentData().put(NBT_ROOT, root.copy());
    }
}