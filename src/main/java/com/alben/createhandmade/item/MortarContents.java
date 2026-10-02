package com.alben.createhandmade.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record MortarContents(ItemStack stack) {

    /** ★ 1.20.1：NBT 存储 key（替代 DataComponentType） */
    private static final String NBT_KEY = "MortarContents";
    private static final String STACK_KEY = "Stack";

    // ========================================================
    // ★ 网络编解码：FriendlyByteBuf（Forge 1.20.1 的 ItemStack 用 writeItem/readItem）
    // ========================================================

    /** 网络编码 */
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(this.stack);
    }

    /** 网络解码 */
    public static MortarContents decodePacket(FriendlyByteBuf buf) {
        return new MortarContents(buf.readItem());
    }

    // ========================================================
    // ★ NBT 存储：替代原本的 stack.get(ModDataComponents.MORTAR_CONTENTS.get())
    // ========================================================

    /** 从 ItemStack 的 NBT 中读取 MortarContents，不存在时返回 null */
    public static MortarContents fromStack(ItemStack holder) {
        CompoundTag tag = holder.getTagElement(NBT_KEY);
        if (tag == null || !tag.contains(STACK_KEY)) return null;

        ItemStack stored = ItemStack.of(tag.getCompound(STACK_KEY));
        if (stored.isEmpty()) return null;

        return new MortarContents(stored);
    }

    /** 将 MortarContents 写入 ItemStack 的 NBT */
    public void writeToStack(ItemStack holder) {
        if (this.stack.isEmpty()) {
            clearFromStack(holder);
            return;
        }

        CompoundTag inner = new CompoundTag();
        this.stack.save(inner);

        CompoundTag tag = new CompoundTag();
        tag.put(STACK_KEY, inner);

        holder.getOrCreateTag().put(NBT_KEY, tag);
    }

    /** 从 ItemStack 的 NBT 中清除 MortarContents */
    public static void clearFromStack(ItemStack holder) {
        holder.removeTagKey(NBT_KEY);
    }
}