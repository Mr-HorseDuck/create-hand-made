package com.alben.createhandmade.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * {@link ItemStack} 的两个小工具：值语义的键、空安全的 NBT 读取。
 *
 * <p><b>为什么需要 {@link Key}：</b>1.20.1 的 {@code ItemStack} <b>没有重写
 * {@code equals}/{@code hashCode}</b>（它只有 {@code matches} / {@code isSameItem} /
 * {@code isSameItemSameTags} 这些静态比较方法），直接把它当 {@code Map} / {@code Set} /
 * record 的键只会按<b>对象身份</b>比较；而 {@code copyWithCount(1)} 每次都走 {@code copy()}
 * 造新实例（只有 isEmpty 才有快路径），于是键永远不相等、缓存与去重会静默失效。</p>
 *
 * <p>键只比「物品 + NBT」：{@link Item} 是单例（身份比较即正确），{@link CompoundTag} 自带
 * 值语义的 {@code equals}/{@code hashCode}。数量不参与比较 —— 键的语义是「同一种物品」，
 * 需要数量的地方由调用方自己归一。</p>
 */
public final class ItemStacks {

    private ItemStacks() {
    }

    /**
     * 以「物品 + NBT」为相等性的键。
     *
     * <p>构造时会把 tag <b>拷一份</b>：键一旦入表就不能再被外部改动，否则哈希会漂移、
     * 表里的条目再也找不到。</p>
     */
    public record Key(Item item, @Nullable CompoundTag tag) {

        public static Key of(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return new Key(stack.getItem(), tag == null ? null : tag.copy());
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return item == key.item && Objects.equals(tag, key.tag);
        }

        @Override
        public int hashCode() {
            return item.hashCode() * 31 + (tag == null ? 0 : tag.hashCode());
        }
    }

    // ==================== 空安全 NBT 读取 ====================
    // ★ 读的时候不要用 getOrCreateTag()：它在 tag 为 null 时会给物品塞一个空
    //   CompoundTag，而空 {} 与 null 在 ItemStack.isSameItemSameTags 下并不相等，
    //   客户端栈会因此和服务端栈"标签不同"（过滤器比对、堆叠合并这类按 NBT 比较的
    //   地方会出现莫名不匹配）。读一律 getTag() + 判空。

    public static int getInt(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(key);
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(key);
    }

    public static String getString(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(key);
    }
}
