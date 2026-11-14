package com.alben.createhandmade.item.contents;

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

    /**
     * 与 {@link InfusionGunContents#fromStackOrEmpty} 对齐的安全读取入口：空碗返回空对象而非 null。
     *
     * <p>对外 API 用这个（{@code CreateHandMadeApi.mortarContents}）。内部的
     * {@link #fromStack} 保留返回 null 的语义——工具类里有大量 {@code != null} 判定依赖它。</p>
     */
    public static MortarContents fromStackOrEmpty(ItemStack holder) {
        MortarContents contents = holder == null || holder.isEmpty() ? null : fromStack(holder);
        return contents != null ? contents : new MortarContents(ItemStack.EMPTY);
    }

    /** 是否为空碗。与 {@link InfusionGunContents#isEmpty()} 同名同义，方便调用方统一写判空。 */
    public boolean isEmpty() {
        return this.stack.isEmpty();
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
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MortarContents other)) return false;
        return ItemStack.isSameItemSameTags(this.stack(), other.stack())
                && this.stack().getCount() == other.stack().getCount();
    }

    @Override
    public int hashCode() {
        ItemStack s = this.stack();
        return java.util.Objects.hash(
                net.minecraft.world.item.Item.getId(s.getItem()),
                s.getCount(),
                s.getTag()
        );
    }
}