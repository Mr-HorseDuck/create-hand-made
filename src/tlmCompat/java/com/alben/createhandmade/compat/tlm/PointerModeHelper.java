package com.alben.createhandmade.compat.tlm;

import net.minecraft.world.item.ItemStack;

/**
 * 指杆模式 NBT 读写。
 * ★ 完全属于 tlmCompat，main 不知道这个 NBT 的存在。
 */
public final class PointerModeHelper {

    public static final int MODE_DEFAULT = 0;
    public static final int MODE_MARK = 1;

    private static final String NBT_MODE = "HandMadePointerMode";

    private PointerModeHelper() {}

    public static int getMode(ItemStack stack) {
        if (stack.isEmpty()) return MODE_DEFAULT;
        return stack.getOrCreateTag().getInt(NBT_MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        if (stack.isEmpty()) return;
        stack.getOrCreateTag().putInt(NBT_MODE, mode);
    }

    public static void cycleMode(ItemStack stack) {
        setMode(stack, (getMode(stack) + 1) % 2);
    }
}